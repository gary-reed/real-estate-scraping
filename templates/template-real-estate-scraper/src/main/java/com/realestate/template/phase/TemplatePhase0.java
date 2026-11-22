package com.realestate.template.phase;

import com.scraper.flavor.cms.domain.ListingIdUrl;
import com.scraper.commons.core.domain.Work;
import com.scraper.commons.core.domain.WorkResult;
import com.scraper.flavor.cms.model._ListingDetails;
import com.realestate.dialect.model.RealEstateListing;
import com.scraper.flavor.cms.phase.Phase0Validation;
import com.scraper.commons.core.phase.ScraperBasicDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * Phase 0 - VALIDATION ("does this scraper still work against the live site?").
 *
 * <h2>What the engine does (generic, do not reimplement)</h2>
 * <ol>
 *   <li>Fetches {@link #sampleSearch()} (one very specific search).</li>
 *   <li>Runs Phase 2 {@code resolveListingIDs} on it and checks every id with {@link #validateListingId}.</li>
 *   <li>Picks one listing ({@link #getRandomListingToParseFromList}) and fetches its detail page with
 *       the Phase 3 mechanics (scroll targets + intercept patterns), then runs the FULL Phase 3 parse
 *       pipeline ({@code executeParseLogic}) on it.</li>
 *   <li>Calls {@link #validateListingDetailsParsing} with the parsed listing.</li>
 *   <li>Calls {@link #customValidation} (optional extra steps).</li>
 * </ol>
 *
 * <p>The idea: the validation search is so specific (city, operation, narrow price band, fixed
 * bedrooms/bathrooms/type) that the site-independent facts about EVERY listing it returns are KNOWN
 * in advance. Therefore the parsed listing can be asserted against those facts, which catches broken
 * selectors / changed site layouts BEFORE a 10-hour crawl starts.
 *
 * <p>All of this is answerable from the recon blueprint section "Phase 0 fixture". The assertions
 * only make sense if the search is NARROW and STABLE. The engine also requires EXACTLY
 * {@code expectedListingsPerPage} ids from {@code resolveListingIDs} on its page 1, so the search must
 * return MORE THAN ONE FULL PAGE (and featured/promoted extras must be excluded by Phase 2).
 */
@Component
public class TemplatePhase0 implements Phase0Validation<RealEstateListing> {

    private static final Logger log = LoggerFactory.getLogger(TemplatePhase0.class);

    @SuppressWarnings("unused") // used once the TODOs below are implemented (site alias prefix, base domain)
    private final ScraperBasicDetails scraperDetails;

    public TemplatePhase0(ScraperBasicDetails scraperDetails) {
        this.scraperDetails = scraperDetails;
    }

    // ------------------------------------------------------------------------------------------
    // FIXTURE: the validation search + the facts it guarantees. Keep them as constants next to each
    // other so that a change of the URL forces a change of the assertions (typically:
    // SPECIFIC_SPLIT_URL, EXPECTED_PRICE_MIN/MAX, EXPECTED_BEDROOMS/BATHROOMS and the city
    // bounding box CITY_LAT/LON_MIN/MAX).
    // ------------------------------------------------------------------------------------------
    //
    // private static final String SPECIFIC_SPLIT_URL = "https://www.<site>/<rent-token>/<big-city>/<filters...>/";
    // private static final double EXPECTED_PRICE_MIN = ...;   // lower bound of the price band in the URL
    // private static final double EXPECTED_PRICE_MAX = ...;   // upper bound of the price band in the URL
    // private static final int    EXPECTED_BEDROOMS  = ...;   // value fixed in the URL
    // private static final int    EXPECTED_BATHROOMS = ...;   // value fixed in the URL
    // private static final double CITY_LAT_MIN = ..., CITY_LAT_MAX = ..., CITY_LON_MIN = ..., CITY_LON_MAX = ...;

    /**
     * Returns the ONE fixed search URL used for validation, as a {@link Work}.
     *
     * <p>HOW TO CHOOSE (blueprint "Phase 0 fixture"):
     * <ul>
     *   <li>A BIG city (so it never runs dry) + one operation (RENT is the best: lots of listings,
     *       price band is meaningful) + a NARROW price band + fixed bedrooms + fixed bathrooms + fixed
     *       residential type, all set through the URL, so that the result set is homogeneous but still
     *       LARGER THAN ONE FULL PAGE: the engine fails Phase 0 unless page 1 yields exactly
     *       {@code expectedListingsPerPage} ids. Open it in a browser and confirm it by eye.</li>
     *   <li>It must return results on every day of the week, also after a market lull. Prefer a
     *       band in the densest part of the market (e.g. around the median rent of that city).</li>
     *   <li>It must NOT depend on the session (no cookies, no login, no sort stored in a cookie).</li>
     *   <li>Build the URL by hand from the observed URL grammar, or (better) from the Phase 1
     *       {@code buildUrlFromFilters} so the fixture also exercises the URL builder - but then keep
     *       an independent literal in a test to catch regressions in the builder.</li>
     * </ul>
     */
    @Override
    public Work sampleSearch() {
        // TODO(site): return new Work(SPECIFIC_SPLIT_URL);
        throw new UnsupportedOperationException(
                "TemplatePhase0.sampleSearch() is not implemented - see the method Javadoc");
    }

    /**
     * Returns {@code true} when {@code listingId} (the PREFIXED id, i.e. {@code <siteAlias><rawId>})
     * has the format of a real listing id of this site.
     *
     * <p>HOW TO RESOLVE: look at ~20 real detail URLs / ids of the site and write the strictest
     * regex that all of them match and that a non-listing (an ad, a project, an agency page, a
     * search page) does not. Check:
     * <ul>
     *   <li>Numeric only ({@code \d{7,}}), alphanumeric slug, UUID, or a mix? Fixed or variable length?
     *       (Example: digits, 7+.) Sites with slug-ids in the path (e.g. {@code /for-sale/house-123456/})
     *       may need the raw id to be the numeric tail only.</li>
     *   <li>The raw id must be STABLE: the same listing must give the same id on every visit, and
     *       different listings never the same id. Tracking parameters, the title slug or the search
     *       position must NOT be part of the id (title slugs change when the title is edited, which
     *       would turn an edit into "delete + new listing").</li>
     *   <li>Whether the id is unique across operations (a listing that moves from rent to sale keeps
     *       its id?), or per operation.</li>
     * </ul>
     *
     * <p>The check always has two parts: the prefix equals {@code scraperDetails.getSiteAlias()} and
     * the remainder matches the raw-id regex of {@code TemplatePhase2#resolveIdFromListingUrl}. Keep the
     * two regexes consistent.
     */
    @Override
    public boolean validateListingId(String listingId) {
        // TODO(site):
        //   if (listingId == null) return false;
        //   if (!listingId.startsWith(scraperDetails.getSiteAlias())) return false;
        //   String raw = listingId.substring(scraperDetails.getSiteAlias().length());
        //   return raw.matches("<raw-id-regex>");
        throw new UnsupportedOperationException(
                "TemplatePhase0.validateListingId() is not implemented - see the method Javadoc");
    }

    /**
     * Picks which listing of the validation search is fetched and parsed in full.
     *
     * <p>The interface default picks a RANDOM one (good: exercises different advertisers over time).
     * Override ONLY to make the choice deterministic (the reference scraper takes the first listing:
     * the first card is the one the asserts were most tested on). Beware: if the first card can be a
     * promoted/sponsored listing from OUTSIDE the search filters (a paid "highlighted" listing),
     * its parsed values will violate the assertions below: either filter those out in Phase 2, or
     * pick a non-promoted index here. Return {@code null} for an empty list.
     */
    @Override
    public Work getRandomListingToParseFromList(List<ListingIdUrl> listings) {
        // Default behavior kept on purpose (random pick). Override to make deterministic:
        // if (listings == null || listings.isEmpty()) return null;
        // return listings.get(0).toWork();
        return Phase0Validation.super.getRandomListingToParseFromList(listings);
    }

    /**
     * Asserts that the fully parsed listing matches the facts guaranteed by the validation search.
     * Return {@code false} (and log WHY at WARN, as the reference does) on the first mismatch.
     * The engine has already run the whole Phase 3 pipeline (including intercept/scroll fetch and
     * anomaly checks), so {@code parsed} is exactly what would be stored in production.
     *
     * <p>CHECK ONLY WHAT THE SEARCH FILTERS GUARANTEE, and what the parser must never get wrong.
     * Typical assertion list (adapt to the fixture):
     * <ol>
     *   <li>{@code parsed}, {@code parsed.getBasic()} not null.</li>
     *   <li>{@code basic.getPropertyType()} equals {@code TypeEnum.RESIDENTIAL}.</li>
     *   <li>{@code basic.getOperation()} equals the fixture operation (e.g. {@code OperationEnum.RENT}).</li>
     *   <li>{@code basic.getPriceInSiteCurrency()} inside the URL's price band (inclusive of both ends unless the
     *       site is exclusive - you measured it in the recon). That helper prefers {@code originalPrice}
     *       when set (site amount kept after non-USD → USD conversion in {@code postProcess}); otherwise
     *       {@code price}. Do not compare a USD {@code price} to a site-currency filter band.</li>
     *   <li>{@code location.getCity()} (case/accent-insensitive {@code contains}) is the fixture city.</li>
     *   <li>{@code property.getBedrooms()} and {@code property.getBathrooms()} equal the fixed values.</li>
     *   <li>Coordinates present and inside the city bounding box (a generous lat/lon rectangle that
     *       contains the municipality): this catches the very common failure where the map capture
     *       (intercept/scroll) silently stops working, and wrong lat/lon swaps.</li>
     *   <li>A phone or an email was obtained (this also catches a contact capture that silently stopped
     *       working).</li>
     *   <li>Optionally: media count &gt; 0, a non-blank description, listing id format valid.</li>
     * </ol>
     *
     * <p>Do NOT assert fields the fixture does not constrain (surface, floor, amenities...) and do not
     * assert anything that legitimately varies, or the validation becomes flaky and blocks real runs.
     */
    @Override
    public boolean validateListingDetailsParsing(RealEstateListing parsed) {
        // TODO(site): implement the assertions listed in the Javadoc, logging WARN on each failure.
        throw new UnsupportedOperationException(
                "TemplatePhase0.validateListingDetailsParsing() is not implemented - see the method Javadoc");
    }

    /**
     * OPTIONAL extra validation after the standard steps. Default (kept): always passes.
     *
     * <p>Implement when the site needs an additional sanity check that does not fit the standard
     * flow, for instance:
     * <ul>
     *   <li>Verify that {@code TemplatePhase1#parseSplitCount} reads a plausible number on a known
     *       big search (count &gt; 0 and not -1) - this validates the count parser, which the standard
     *       Phase 0 does not exercise;</li>
     *   <li>Verify pagination: fetch page 2 through {@code TemplatePhase2#resolvePagination} and check
     *       that it contains different ids than page 1;</li>
     *   <li>Verify {@code wasDelisted} on a known-fake listing id URL (must return true).</li>
     * </ul>
     * Use the provided {@code workExecutor} to fetch any URL through the normal worker pool (proxies,
     * browser, anti-bot handling).
     */
    @Override
    public boolean customValidation(Function<Work, WorkResult> workExecutor) {
        log.debug("TemplatePhase0.customValidation(): no extra validation implemented");
        return true;
    }
}
