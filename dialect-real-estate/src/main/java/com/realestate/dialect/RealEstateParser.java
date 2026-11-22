package com.realestate.dialect;

import com.realestate.dialect.model.Basic;
import com.scraper.flavor.cms.config.ScraperConfig;
import com.scraper.flavor.cms.parsing.ScraperParser;
import org.springframework.beans.factory.annotation.Autowired;
import com.scraper.flavor.cms.parsing.language.dictionary.de.GermanCardinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.de.GermanOrdinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.de.GermanOrdinalsWithNumbers;
import com.scraper.flavor.cms.parsing.language.dictionary.en.EnglishCardinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.en.EnglishOrdinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.en.EnglishOrdinalsWithNumbers;
import com.scraper.flavor.cms.parsing.language.dictionary.es.SpanishCardinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.es.SpanishOrdinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.es.SpanishOrdinalsWithNumbers;
import com.scraper.flavor.cms.parsing.language.dictionary.fr.FrenchCardinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.fr.FrenchOrdinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.fr.FrenchOrdinalsWithNumbers;
import com.scraper.flavor.cms.parsing.language.dictionary.it.ItalianCardinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.it.ItalianOrdinalNumbersAsText;
import com.scraper.flavor.cms.parsing.language.dictionary.it.ItalianOrdinalsWithNumbers;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import com.realestate.dialect.parsing.validation.AnomalyBounds;
import com.realestate.dialect.currency.UsdRateClient;
import com.realestate.dialect.model.RealEstateListing;
import com.realestate.dialect.parsing.ParsedListingCleaner;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.realestate.dialect.parsing.resolvers.BuildingResolver;
import com.realestate.dialect.parsing.resolvers.FurnishResolver;
import com.realestate.dialect.parsing.resolvers.InteriorResolver;
import com.realestate.dialect.parsing.resolvers.PropertyResolver;
import com.realestate.dialect.parsing.resolvers.TextResolver;
import com.realestate.dialect.parsing.resolvers.TransactionResolver;
import com.realestate.dialect.parsing.validation.AnomalyDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Real-estate detail parser. A site scraper extends this and implements {@link #parseListingDetails}
 * and {@link #getLanguage}.
 *
 * <p>{@link #finish} runs the property pipeline: clean, anomaly check, description resolvers,
 * anomaly check again, convert the price to USD, then price ratio and term price.</p>
 */
public abstract class RealEstateParser extends ScraperParser<RealEstateListing> {

    /** Absolute USD tolerance when verifying an already-converted price against {@code originalPrice * rate}. */
    private static final double USD_CONVERT_ABS_TOLERANCE = 1.0;
    /** Relative tolerance (1%) for the same check. */
    private static final double USD_CONVERT_REL_TOLERANCE = 0.01;

    private AnomalyDetector anomalyDetector;
    private List<TextResolver> resolvers;
    private NumbersAsText[] numberClasses;
    private final ParsedListingCleaner listingCleaner = new ParsedListingCleaner();

    /** Filled by Spring on a scraper. Tests that construct a parser directly leave this null. */
    @Autowired(required = false)
    private ScraperConfig scraperConfig;

    /**
     * USD per 1 unit of a currency, from Frankfurter. The framework's {@code CurrencyRateService} only
     * fetches a rate when the site currency is not EUR, and it stores that number as an EUR rate.
     * This dialect converts to USD itself, so an EUR site is still converted.
     */
    @Autowired(required = false)
    private UsdRateClient usdRateClient;

    /** Language of the site. Selects the real-estate vocabulary and the number-word classes. */
    public abstract ParserLanguage getLanguage();

    /**
     * Anomaly thresholds. Override with {@code realEstateSettings.getAnomalyBounds()} (a
     * {@link RealEstateSettings} bean) so {@code scraper.anomaly-bounds.*} in application.yaml applies.
     */
    public AnomalyBounds getAnomalyBounds() {
        return new AnomalyBounds();
    }

    /**
     * Fallback rate when {@link UsdRateClient} is not wired (unit tests). A running scraper uses the
     * client inside {@link #applyUsdPriceConversion} and does not read {@link ScraperConfig}'s rate:
     * that value is an EUR quote, and it stays {@code 1.0} for an EUR site.
     */
    protected double getExchangeRate() {
        return scraperConfig != null ? scraperConfig.getExchangeRate() : 1.0;
    }

    /** Tests bind a client directly. Spring injects the same field on a scraper. */
    protected void useUsdRateClient(UsdRateClient client) {
        this.usdRateClient = client;
    }

    /**
     * USD per 1 unit of {@code currency}. The wired client asks Frankfurter. Without one, tests use
     * {@link #getExchangeRate()}. A failed lookup returns {@code -1} so the listing is not stored.
     */
    private double usdPerUnit(String currency) {
        if (usdRateClient == null) {
            return getExchangeRate();
        }
        try {
            return usdRateClient.usdPerUnit(currency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[PARSING_PENDING] USD rate for {} was interrupted — listing not stored", currency);
            return -1;
        } catch (Exception e) {
            log.error("[PARSING_PENDING] USD rate for {} failed: {} — listing not stored", currency, e.toString());
            return -1;
        }
    }

    @Override
    protected RealEstateListing finish(RealEstateListing listing) {
        return postProcess(listing);
    }

    /**
     * Clean, resolve from the description, convert currency, recalculate.
     * Public so tests and the resolve-text endpoint can run the pipeline on a listing they built.
     *
     * @return the listing, or {@code null} when the currency conversion is inconsistent
     */
    public RealEstateListing postProcess(RealEstateListing listing) {
        initPipelineIfNeeded();

        listingCleaner.clean(listing);
        anomalyDetector.detect(listing);

        NormalizedText descriptionText = listing.getDescription() != null
                ? NormalizedText.normalize(listing.getDescription()) : null;
        if (descriptionText != null && !descriptionText.isBlank()) {
            for (TextResolver resolver : resolvers) {
                resolver.prepare(descriptionText, "description", listing);
                resolver.resolve(listing, descriptionText);
            }
        }

        String descriptionStr = descriptionText != null ? descriptionText.value() : null;
        anomalyDetector.detectWithDescription(listing, descriptionStr);

        if (!applyUsdPriceConversion(listing)) {
            return null;
        }

        listing.calculateAndSetPriceRatio();
        listing.calculateAndSetFloorNrAmenity();
        listing.resolveAndSetTopTermPrice();
        return listing;
    }

    /**
     * Run the resolvers on a text the site parser already extracted (features, title).
     * Description resolution happens on its own inside {@link #postProcess}.
     */
    public void resolveFromText(RealEstateListing listing, String text, String whatText) {
        resolveFromText(listing, text, whatText, false);
    }

    public void resolveFromText(RealEstateListing listing, String text, String whatText, boolean alwaysWarn) {
        if (text == null || text.isBlank()) return;
        initPipelineIfNeeded();
        NormalizedText normalized = NormalizedText.normalize(text);
        for (TextResolver resolver : resolvers) {
            resolver.prepare(normalized, whatText, listing, alwaysWarn);
            resolver.resolve(listing, normalized);
        }
    }

    private boolean applyUsdPriceConversion(RealEstateListing listing) {
        Basic basic = listing.getBasic();
        if (basic == null) return true;

        String currency = basic.getCurrency();
        if (currency == null || currency.isBlank() || "USD".equalsIgnoreCase(currency)) {
            return true;
        }

        Double price = basic.getPrice();
        Double original = basic.getOriginalPrice();
        if (price == null && original == null) {
            return true;
        }

        double rate = usdPerUnit(currency);
        if (rate <= 0) {
            log.error("[PARSING_PENDING] Invalid exchange rate {} for currency {} — listing not stored",
                    rate, currency);
            return false;
        }

        if (price == null) {
            basic.setPrice(original * rate);
            log.debug("USD convert: price was null — set from originalPrice {} {} × {} → {}",
                    original, currency, rate, basic.getPrice());
            return true;
        }

        if (original == null) {
            basic.setOriginalPrice(price);
            basic.setPrice(price * rate);
            log.debug("USD convert: {} {} → originalPrice={}, price(USD)={}",
                    price, currency, basic.getOriginalPrice(), basic.getPrice());
            return true;
        }

        if (Objects.equals(price, original) || almostEqual(price, original, 0.01, 0.0)) {
            basic.setPrice(original * rate);
            log.debug("USD convert: price==originalPrice ({} {}) → price(USD)={}",
                    original, currency, basic.getPrice());
            return true;
        }

        if (matchesSiteToUsd(original, price, rate)) {
            basic.setOriginalPrice(original);
            basic.setPrice(original * rate);
            log.debug("USD convert: originalPrice={} {} → price(USD)={} (rate={})",
                    original, currency, basic.getPrice(), rate);
            return true;
        }
        if (matchesSiteToUsd(price, original, rate)) {
            basic.setOriginalPrice(price);
            basic.setPrice(price * rate);
            log.debug("USD convert: swapped fields — price was site {} {} → originalPrice={}, price(USD)={}",
                    price, currency, basic.getOriginalPrice(), basic.getPrice());
            return true;
        }

        log.error("[PARSING_PENDING] Inconsistent prices for currency {}: price={}, originalPrice={}, "
                        + "rate={} (neither is site→USD of the other) — listing not stored",
                currency, price, original, rate);
        return false;
    }

    private static boolean matchesSiteToUsd(double siteAmount, double usdCandidate, double rate) {
        return almostEqual(usdCandidate, siteAmount * rate,
                USD_CONVERT_ABS_TOLERANCE, USD_CONVERT_REL_TOLERANCE);
    }

    private static boolean almostEqual(double a, double b, double absTol, double relTol) {
        double diff = Math.abs(a - b);
        if (diff <= absTol) return true;
        double scale = Math.max(Math.abs(a), Math.abs(b));
        return scale > 0 && diff / scale <= relTol;
    }

    private void initPipelineIfNeeded() {
        if (anomalyDetector != null) return;

        ParserLanguage language = getLanguage();
        List<ParserLanguage> langs = List.of(language);
        this.numberClasses = getNumberClasses(language);

        anomalyDetector = new AnomalyDetector(getAnomalyBounds());
        resolvers = List.of(
                new PropertyResolver(langs, numberClasses),
                new TransactionResolver(langs, numberClasses),
                new BuildingResolver(langs, numberClasses),
                new FurnishResolver(langs, numberClasses),
                new InteriorResolver(langs, numberClasses)
        );
    }

    /** Number words for one language ("three", "tercer"), used by the resolvers. */
    public static NumbersAsText[] getNumberClasses(ParserLanguage language) {
        List<NumbersAsText> classes = new ArrayList<>();
        switch (language) {
            case ENGLISH -> {
                classes.add(new EnglishCardinalNumbersAsText());
                classes.add(new EnglishOrdinalNumbersAsText());
                classes.add(new EnglishOrdinalsWithNumbers());
            }
            case SPANISH -> {
                classes.add(new SpanishCardinalNumbersAsText());
                classes.add(new SpanishOrdinalNumbersAsText());
                classes.add(new SpanishOrdinalsWithNumbers());
            }
            case GERMAN -> {
                classes.add(new GermanCardinalNumbersAsText());
                classes.add(new GermanOrdinalNumbersAsText());
                classes.add(new GermanOrdinalsWithNumbers());
            }
            case FRENCH -> {
                classes.add(new FrenchCardinalNumbersAsText());
                classes.add(new FrenchOrdinalNumbersAsText());
                classes.add(new FrenchOrdinalsWithNumbers());
            }
            case ITALIAN -> {
                classes.add(new ItalianCardinalNumbersAsText());
                classes.add(new ItalianOrdinalNumbersAsText());
                classes.add(new ItalianOrdinalsWithNumbers());
            }
        }
        return classes.toArray(new NumbersAsText[0]);
    }
}
