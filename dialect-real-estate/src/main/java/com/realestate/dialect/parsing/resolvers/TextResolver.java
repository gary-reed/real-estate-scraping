package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.scraper.flavor.cms.model.ScrapingDetails;
import com.realestate.dialect.model.ScrapingTaggingEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.scraper.flavor.cms.parsing.text.ClosestToWordMatchObject;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import com.scraper.flavor.cms.parsing.text.TextSearchUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for resolving missing listing details from normalized text.
 *
 * <p>Each subclass focuses on a specific section of the listing data model
 * (transaction, property, building, furnish, interior) and knows which
 * keywords to look for in each supported language.</p>
 *
 * <p>The resolution pattern is:</p>
 * <ol>
 *   <li>Check if a field is missing (null or empty)</li>
 *   <li>If missing, search the text for language-specific keywords</li>
 *   <li>Extract the value from the context around the keyword</li>
 *   <li>Set the field on the listing</li>
 * </ol>
 *
 * <p>Subclasses must implement:</p>
 * <ul>
 *   <li>{@link #resolve(RealEstateListing, NormalizedText)} — the actual resolution logic</li>
 *   <li>{@link #getSectionName()} — for logging purposes</li>
 * </ul>
 *
 * <p>The {@code whatText} field identifies the source text being resolved
 * (e.g. "description", "features") and is included in log messages for
 * easier debugging.</p>
 */
public abstract class TextResolver {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected static final String TAG_TEXT_RESOLVE = "[TEXT_RESOLVE]";

    /** Languages to attempt resolution for, in priority order */
    private final List<ParserLanguage> languages;

    /** Language-specific number-as-text classes for parsing words like "three", "cinco", etc. */
    private final NumbersAsText[] numberClasses;

    /**
     * Cached normalized text, set via {@link #prepare(NormalizedText, String)}.
     * Used by {@link #findClosestNumber} to avoid re-normalizing on every call.
     */
    private NormalizedText normalizedText;

    /**
     * Identifies which text is being resolved (e.g. "description", "features").
     * Included in log messages for easier debugging.
     */
    private String whatText;

    /** Listing used only to append the URL on WARN resolver logs. */
    private RealEstateListing listingForLog;

    /**
     * When true for this prepare/resolve pass, non-description hits are logged at WARN.
     * Description is always WARN. Set via {@link #prepare(NormalizedText, String, RealEstateListing, boolean)}.
     */
    private boolean warnOnResolve;

    protected TextResolver(List<ParserLanguage> languages, NumbersAsText[] numberClasses) {
        this.languages = languages;
        this.numberClasses = numberClasses != null ? numberClasses : new NumbersAsText[0];
    }

    /**
     * Prepares this resolver for a resolution pass on the given text.
     * Must be called ONCE before {@link #resolve} so that all
     * {@link #findClosestNumber} calls reuse the same normalized text.
     *
     * @param normalizedText text already fully processed by
     *        {@link com.scraper.flavor.cms.parsing.text.NormalizedText#normalizeText}
     * @param whatText label identifying the text source (e.g. "description", "features")
     */
    public void prepare(NormalizedText normalizedText, String whatText) {
        prepare(normalizedText, whatText, null, false);
    }

    public void prepare(NormalizedText normalizedText, String whatText, RealEstateListing listing) {
        prepare(normalizedText, whatText, listing, false);
    }

    /**
     * @param warn when true, {@link #logResolved} uses WARN even if the source is not description
     */
    public void prepare(NormalizedText normalizedText, String whatText, RealEstateListing listing, boolean alwaysWarn) {
        this.normalizedText = normalizedText;
        this.whatText = whatText;
        this.listingForLog = listing;
        this.warnOnResolve = alwaysWarn;
    }

    /**
     * Attempt to resolve missing fields from normalized text.
     *
     * @param listing The listing to enrich
     * @param text    Normalized text (created via {@link NormalizedText#normalize})
     * @return The same listing, potentially enriched
     */
    public abstract RealEstateListing resolve(RealEstateListing listing, NormalizedText text);

    /**
     * Returns the section name for logging (e.g. "Transaction", "Property").
     */
    public abstract String getSectionName();

    /**
     * Returns the configured languages.
     */
    public List<ParserLanguage> getLanguages() {
        return languages;
    }

    /**
     * Returns the label for the text being resolved (e.g. "description", "features").
     */
    protected String getWhatText() {
        return whatText != null ? whatText : "text";
    }

    /**
     * Returns true if currently resolving from description text.
     */
    protected boolean isDescription() {
        return "description".equals(whatText);
    }

    /**
     * Tags a field filled from description free text (not structured site fields).
     * No-op when resolving from features or any other non-description source.
     * Creates {@link ScrapingDetails} if missing.
     */
    protected void tagResolvedFromDescription(RealEstateListing listing, ScrapingTaggingEnum tag) {
        if (listing == null || tag == null || !isDescription()) return;
        if (listing.getScrapingDetails() == null) {
            listing.setScrapingDetails(new ScrapingDetails());
        }
        listing.getScrapingDetails().addTag(tag);
    }

    /**
     * Returns the number classes array for subclass use (e.g. heavy resolution).
     */
    protected NumbersAsText[] getNumberClasses() {
        return numberClasses;
    }

    /** Returns the cardinal numbers-as-text class, or null if not available. */
    protected NumbersAsText getCardinals() {
        return numberClasses.length > 0 ? numberClasses[0] : null;
    }

    /** Returns the ordinal numbers-as-text class, or null if not available. */
    protected NumbersAsText getOrdinals() {
        return numberClasses.length > 1 ? numberClasses[1] : null;
    }

    /** Returns the ordinals-with-numbers class, or null if not available. */
    protected NumbersAsText getOrdinalsWithNrs() {
        return numberClasses.length > 2 ? numberClasses[2] : null;
    }

    // ==================== Helper Methods for subclasses ====================

    /**
     * Checks if a string value is missing (null or blank).
     */
    protected boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Checks if an integer field has not been set yet.
     * {@code 0} and {@code -1} are real values (studio, no bathrooms, ground floor, basement)
     * and must not be overwritten.
     */
    protected boolean isMissing(Integer value) {
        return value == null;
    }

    /**
     * Checks if a double field has not been set yet. {@code 0} is treated as already set.
     */
    protected boolean isMissing(Double value) {
        return value == null;
    }

    /**
     * Checks if an enum value is missing (null).
     */
    protected boolean isMissing(Enum<?> value) {
        return value == null;
    }

    /**
     * Searches for the closest number to any of the given keywords in text.
     * Uses language-specific NumbersAsText classes to also detect number words
     * (e.g. "three bedrooms", "cinco habitaciones").
     *
     * <p>Performs a fast {@link #containsAny} check first — if none of the keywords
     * exist in the text, returns null immediately without invoking the expensive
     * number-search logic.</p>
     *
     * @param text The text to search in
     * @param keywords Keywords to search near
     * @return The closest number found, or null if none
     */
    protected Double findClosestNumber(String text, List<String> keywords) {
        // Fast path: if none of the keywords appear in the text, skip entirely
        if (!containsAny(text, keywords)) return null;

        try {
            // Use cached normalized text if available
            String effectiveText = (normalizedText != null) ? normalizedText.value() : text;

            return TextSearchUtils.getClosestNumberToWordsOnText(
                    effectiveText, keywords, null, 3, null, null, numberClasses);
        } catch (Exception e) {
            List<String> foundKeywords = filterFoundKeywords(text, keywords);
            log.debug("{} {} Error finding closest number for keywords {} in [{}]: {}",
                TAG_TEXT_RESOLVE, getSectionName(), foundKeywords, getWhatText(), e.getMessage());
            return null;
        }
    }

    /**
     * Searches for the closest number with extended parameters (vicinity, inclusions, exclusions).
     * Used by heavy resolution methods that need more control over the search.
     */
    protected Double findClosestNumberExtended(String text, List<String> keywords,
            Boolean preferBefore, int vicinitySize, List<String> mustInclude,
            List<String> exclusions, NumbersAsText... nrClasses) {
        if (!containsAny(text, keywords)) return null;

        try {
            String effectiveText = (normalizedText != null) ? normalizedText.value() : text;

            return TextSearchUtils.getClosestNumberToWordsOnText(
                    effectiveText, keywords, preferBefore, vicinitySize,
                    mustInclude, exclusions, nrClasses);
        } catch (Exception e) {
            List<String> foundKeywords = filterFoundKeywords(text, keywords);
            log.debug("{} {} Error in extended number search for keywords {} in [{}]: {}",
                TAG_TEXT_RESOLVE, getSectionName(), foundKeywords, getWhatText(), e.getMessage());
            return null;
        }
    }

    protected Double findClosestNumberIgnoringAreaUnits(String text, List<String> keywords,
            ParserLanguage lang, int vicinitySize, NumbersAsText... nrClasses) {
        return findClosestNumberIgnoringAreaUnits(text, keywords, lang, vicinitySize, null, nrClasses);
    }

    /**
     * Like {@link #findClosestNumberExtended} but skips numbers sitting next to an area unit
     * ({@code 55 m²}, {@code 800 sqft}) so surface figures are not taken as floor counts.
     */
    protected Double findClosestNumberIgnoringAreaUnits(String text, List<String> keywords,
            ParserLanguage lang, int vicinitySize, List<String> exclusions, NumbersAsText... nrClasses) {
        if (!containsAny(text, keywords) || lang == null) return null;

        try {
            String effectiveText = (normalizedText != null) ? normalizedText.value() : text;
            List<ClosestToWordMatchObject> matches = TextSearchUtils.getClosestNumberToWordsOnTextAdv(
                    effectiveText, keywords, null, vicinitySize, null, exclusions, nrClasses);
            if (matches == null || matches.isEmpty()) return null;

            List<String> units = lang.areaUnitPatterns();
            for (ClosestToWordMatchObject match : matches) {
                if (match.getNumberFound() == null) continue;
                if (TextSearchUtils.isMatchedNumberBesideAny(match, units)) {
                    continue;
                }
                return match.getNumberFound();
            }
            return null;
        } catch (Exception e) {
            log.debug("{} {} Error in area-unit-aware number search for keywords {} in [{}]: {}",
                TAG_TEXT_RESOLVE, getSectionName(), keywords, getWhatText(), e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether the text contains any of the given keywords.
     * <p>For multi-word keywords (containing spaces), uses {@code wordsTogetherInText}
     * which handles stem/partial matching, proximity, and respects context boundaries.
     * For single-word keywords, uses {@code matchesKeyword} which handles
     * {@code |} word-boundary markers.</p>
     */
    protected boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        for (String keyword : keywords) {
            if (keyword.contains(" ")) {
                String[] parts = keyword.split("\\s+");
                if (parts.length >= 2
                        && TextSearchUtils.wordsTogetherInText(text, parts[0],
                            java.util.Arrays.copyOfRange(parts, 1, parts.length))) {
                    return true;
                }
            } else if (TextSearchUtils.matchesKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns only the keywords from the list that actually appear in the text.
     * Used for precise error logging.
     */
    private List<String> filterFoundKeywords(String text, List<String> keywords) {
        List<String> found = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword.contains(" ")) {
                String[] parts = keyword.split("\\s+");
                if (parts.length >= 2
                        && TextSearchUtils.wordsTogetherInText(text, parts[0],
                            java.util.Arrays.copyOfRange(parts, 1, parts.length))) {
                    found.add(keyword);
                }
            } else if (TextSearchUtils.matchesKeyword(text, keyword)) {
                found.add(keyword);
            }
        }
        return found;
    }

    /**
     * Logs a successful resolution. Description is always WARN. Other sources are DEBUG
     * unless this pass was prepared with {@code warn=true}.
     */
    protected void logResolved(String field, Object value, String source) {
        boolean warn = isDescription() || warnOnResolve;
        String url = listingForLog != null ? getListingUrl(listingForLog) : null;
        if (warn && url != null && !"unknown".equals(url)) {
            log.warn("{} {} resolved '{}' = {} from [{}] ({}) url={}",
                    TAG_TEXT_RESOLVE, getSectionName(), field, value, getWhatText(), source, url);
        } else if (warn) {
            log.warn("{} {} resolved '{}' = {} from [{}] ({})",
                    TAG_TEXT_RESOLVE, getSectionName(), field, value, getWhatText(), source);
        } else {
            log.debug("{} {} resolved '{}' = {} from [{}] ({})",
                    TAG_TEXT_RESOLVE, getSectionName(), field, value, getWhatText(), source);
        }
    }

    /**
     * Gets the listing URL from the listing for logging purposes.
     */
    protected String getListingUrl(RealEstateListing listing) {
        return listing.getScrapingDetails() != null
                ? listing.getScrapingDetails().getExtListingUrl() : "unknown";
    }
}
