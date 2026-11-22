package com.realestate.template;

import com.scraper.commons.core.filter.EnumFilter;
import com.scraper.commons.core.filter.RangeFilter;

import static com.scraper.commons.core.filter.EnumFilter.entry;

/**
 * Site constants and SPLIT FILTER DEFINITIONS of the target site.
 *
 * <p>This is the "data" half of Phase 1. The Phase 1 class only wires these constants together
 * ({@code TemplatePhase1}); almost all the site knowledge that does not need parsing lives here as
 * plain values copied from the live site. A recon agent should be able to fill this whole file from
 * the site's search UI + URLs alone (the "Draft constants" section of the Scraper Blueprint).
 *
 * <h2>How the engine uses these filters (read before filling anything)</h2>
 * <ol>
 *   <li><b>Basic splits</b> = cartesian product of the BASIC enum filters (normally
 *       {@code LOCATION x OPERATION}). Built in {@code TemplatePhase1#getBasicSplits()}.</li>
 *   <li>The engine fetches each split and reads its total result count
 *       ({@code TemplatePhase1#parseSplitCount}).</li>
 *   <li>If {@code count > ScraperBasicDetails.getMaxCountPerSearch()}, the engine subdivides with
 *       the filters returned by {@code TemplatePhase1#getFilterPriority(split)}, IN ORDER, using the
 *       first filter that can still split the current split:
 *       <ul>
 *         <li>{@link EnumFilter} not yet applied: one child per value.</li>
 *         <li>{@link RangeFilter}: binary split over the FIXED ORDERED list of boundary values.
 *             First cut is open-ended ({@code [null..mid]} and {@code [mid..null]}); later cuts are
 *             {@code [from..mid]} and {@code [mid..to]} until the two bounds are adjacent values in
 *             the list (filter exhausted, next filter is used). NOTE the middle value is shared by
 *             both children: it is the upper bound of the lower child and the lower bound of the
 *             upper child. Whether a listing priced exactly on the boundary shows in 0, 1 or 2
 *             children depends on whether the site treats min/max as inclusive or exclusive. Test it
 *             with real listings priced exactly on a boundary and record the answer (see the notes
 *             on each RangeFilter below).</li>
 *       </ul>
 *   <li>When every filter is exhausted the split is accepted anyway and whatever lies beyond the
 *       site's page cap is lost (log it, then add another granular filter to the priority list).</li>
 * </ol>
 *
 * <h2>Rules that are easy to get wrong</h2>
 * <ul>
 *   <li>{@code shortName} ("Op", "Loc", "Price", ...) is part of the split identity:
 *       {@code SplitCount.computeAlias} sorts and keys by it, and lookups like
 *       {@code getRangeFilter(filters, "Price")} match by it. Use the SAME shortName for the rent
 *       and the buy variant of the same concept (e.g. both price filters are called "Price") and
 *       never rename it after the first real run.</li>
 *   <li>The raw enum VALUE (the first argument of {@code entry(value, alias)}) must be exactly the
 *       URL token (or id) used to build the URL. It is also stored: {@code split_group} is the raw
 *       values of the basic filters joined with {@code _} (e.g. {@code region-slug_for-sale}).
 *       Changing a token later orphans the stored split groups.</li>
 *   <li>The alias (second argument) is the human display name used in logs and in the Manager UI.
 *       Convention: operations use the model names {@code "RENT"}, {@code "BUY"}, {@code "SHARE"};
 *       locations use the site's display name.</li>
 *   <li>Range boundary lists: ascending, strictly increasing, NO {@code "0"} (a {@code 0} bound is
 *       skipped by {@code buildUrlFromFilters}, producing a URL without that filter that returns the
 *       FULL unfiltered set and breaks the count logic), and each value must be one the site really
 *       accepts. Prefer the values of the site's own dropdowns: they are guaranteed to be valid and
 *       often snapped to when the site receives something else. Verify what happens for an
 *       off-list value: rejected, snapped to the nearest value, or SILENTLY IGNORED (the worst case:
 *       the unfiltered set is returned and splitting never converges).</li>
 *   <li>The values of each filter list must COVER 100% of the site's listings once all of them are
 *       combined (for enums: every listing has exactly one value; if some listings have none, an
 *       extra catch-all filter or a non-split fallback is needed, otherwise they vanish once the
 *       filter is applied). Measure the leakage: {@code count(parent) - sum(count(children))}.</li>
 * </ul>
 */
public final class TemplateConstants {

    private TemplateConstants() {}

    // ==================== Site Identifiers ====================
    // The site alias / base domain / currency / country live in TemplateScraperDetails
    // (ScraperBasicDetails). Do not duplicate them here unless several classes need the same literal.

    // ==================== Site Constraints ====================

    /**
     * Max results per split before subdividing (see {@code ScraperBasicDetails#getMaxCountPerSearch}
     * for the full reasoning: min(hard cap, capped-count limit, ~8 pages * LISTINGS_PER_PAGE)).
     * Small on purpose: the SAME worker paginates a whole split, so we want few pages per split.
     *
     * <p>TODO(site): REPLACE. 240 is a placeholder (about 8 pages of 30), not a value to keep.
     */
    public static final int MAX_RESULTS_PER_SEARCH = 240;

    /**
     * Listing cards per full search page (see {@code ScraperBasicDetails#expectedListingsPerPage}).
     *
     * <p>TODO(site): REPLACE. 30 is a placeholder, not a value to keep.
     */
    public static final int LISTINGS_PER_PAGE = 30;

    // ==================== Operations (BASIC enum filter) ====================

    /**
     * The OPERATION dimension: buy / rent / (share) - every listing "category" the scraper must
     * cover that maps to our model {@code Basic.OperationEnum} (BUY, RENT, SHARE).
     *
     * <p>HOW TO RESOLVE:
     * <ul>
     *   <li>List every transaction type the site offers: sale, rent long-term, rent short/holiday,
     *       rooms/flat-share, new-build developments, garages, land, commercial, auctions...
     *       Decide per type whether it is IN SCOPE (usually: residential BUY, RENT, SHARE) and map
     *       it to our model (operation BUY/RENT/SHARE; term LONG/MID/SHORT; type
     *       RESIDENTIAL/COMMERCIAL). Anything out of scope must be listed in the README/blueprint
     *       as "excluded on purpose", so a future dev does not think it was forgotten.</li>
     *   <li>The VALUE is whatever part of the URL selects it: a path segment
     *       ({@code /for-rent/}), a query parameter value ({@code ?type=rent}) or an id.
     *       Copy it from a real search URL, do not infer it.</li>
     *   <li>If the operation is NOT part of the path but a query parameter, also decide where it
     *       goes in {@code buildUrlFromFilters} (parameter order sometimes matters).</li>
     *   <li>Some sites put the operation AND the property category in one token ("for-sale"
     *       = sale + homes); then the token covers both and is a single enum value.</li>
     *   <li>If a type needs different downstream logic (e.g. rent prices are monthly, buy prices are
     *       totals, so they need different price boundary lists), note it: {@code getFilterPriority}
     *       selects the price list per operation.</li>
     * </ul>
     *
     * <p>Example (reference scraper):
     * <pre>
     * EnumFilter.withAliases("Op", "operation",
     *     entry("for-rent", "RENT"),
     *     entry("for-sale", "BUY"));
     * </pre>
     *
     * <p>TODO(site): add the real entries. Empty on purpose in the template.
     */
    public static final EnumFilter OPERATIONS = EnumFilter.withAliases("Op", "operation"
            // , entry("<url-token-for-rent>", "RENT")
            // , entry("<url-token-for-buy>",  "BUY")
            // , entry("<url-token-for-share>", "SHARE")   // only if the site has rooms/flat-share
    );

    // ==================== Locations (BASIC enum filter) ====================

    /**
     * The LOCATION dimension: the level of the site's location hierarchy that forms the BASIC
     * splits. It is what makes the crawl parallel and resumable; every other filter only exists to
     * break up locations that are still too big.
     *
     * <p>HOW TO RESOLVE:
     * <ol>
     *   <li>Map the hierarchy the site uses (country > region > province > city > district >
     *       neighbourhood) and how each level appears in the URL (path segment, slug, numeric id,
     *       query parameter). Sitemaps ({@code /sitemap.xml}, {@code robots.txt} references) and
     *       the location dropdown/autocomplete JSON often list ALL locations in one request.</li>
     *   <li>Choose ONE level for the basic splits: the level where a search returns "a few
     *       thousands" of results at most for the average entry and where the full list is finite
     *       and obtainable (provinces / counties / states are typical; cities are too many and make
     *       Phase 1 slow; a single country-wide split is too big and gets split only by price,
     *       which is inefficient and ordering-fragile).</li>
     *   <li>The list MUST be COMPLETE and NON-OVERLAPPING for the chosen level: children must add
     *       up to the whole country. Check that: {@code sum(count(location_i)) ~= count(whole site)}
     *       per operation. Overlap (a city that is listed under two provinces, a "region" token
     *       that includes its provinces) duplicates listings; gaps lose them. Include special
     *       territories/islands/autonomous cities that sit outside the normal hierarchy.</li>
     *   <li>Store entries as {@code entry("<slug-or-id>", "<Display name>")}. Keep the site's own
     *       slug exactly, including accents-stripped forms, a region suffix the site adds, and ids.</li>
     *   <li>If a big city needs its own basic split because the province split is dominated by it,
     *       add the city as an ADDITIONAL basic entry ONLY IF the site excludes it from the
     *       province split (check the counts). Otherwise rely on sub-splitting by price.</li>
     *   <li>Active vs inactive: during development keep only ONE small location active (as the
     *       reference scraper does with a small province) so E2E test runs are short, and keep the
     *       rest commented out right below it, ready to switch on.</li>
     * </ol>
     *
     * <p>TODO(site): add the real entries. Empty on purpose in the template.
     */
    public static final EnumFilter REGIONS = EnumFilter.withAliases("Loc", "city"
            // , entry("<location-slug-or-id>", "<Display name>")
    );

    // ==================== Price ranges ====================
    //
    // PRICE is almost always the first subdivision filter: every listing has one, it is granular,
    // and sites support open-ended min/max in the URL. Use a SEPARATE list per operation, because
    // rent (monthly, hundreds) and buy (totals, hundreds of thousands) have different scales.
    //
    // HOW TO RESOLVE (for each operation):
    //  1. Copy the exact values of the site's min/max price dropdowns (or the values its URL
    //     accepts). Remove "0"/"No min"/"No max" entries (see class Javadoc).
    //  2. Verify OPEN-ENDED support: does "only min" work? does "only max" work? (the first
    //     binary cut is [null..mid] and [mid..null]).
    //  3. Verify BOUNDARY semantics with real listings priced exactly at a boundary value:
    //     min inclusive? max inclusive? If BOTH are exclusive, listings priced exactly at a boundary
    //     fall out of both children = permanent loss. If BOTH are inclusive they appear in both
    //     children = duplicates (harmless: Phase 2 dedups by id, but wasteful). Record the result
    //     here as a comment.
    //  4. Verify INVALID VALUE behaviour (off-list value, 0, non-numeric): rejected / snapped /
    //     silently ignored. Silently ignored = broken splitting; then only list values the site
    //     accepts.
    //  5. Choose density: finer steps where listings concentrate (most rentals are cheap, most
    //     sales mid-range) and coarser steps in the tails. The finest step is the last resort for
    //     a dense location: if two adjacent values still hold more than getMaxCountPerSearch()
    //     results, the next filter in the priority list takes over.
    //  6. Measure LEAKAGE: listings without a price ("price on request") vanish as soon as a price
    //     bound is applied. Report the % in the README; if significant, add a non-price fallback
    //     (e.g. a separate "no price" split if the site supports it).

    /**
     * Price boundaries for RENT. TODO(site): fill. Example (a European site, EUR/month):
     * {@code "100","150",...,"1000","1100",...,"2000","2100","2400","2700","3000"}.
     * shortName MUST be "Price" (same as {@link #PRICES_BUY}).
     */
    public static final RangeFilter PRICES_RENT = RangeFilter.of("Price"
            // , "<boundary-1>", "<boundary-2>", ...   (ascending, no "0")
    );

    /**
     * Price boundaries for BUY. TODO(site): fill. Example (a European site, EUR):
     * {@code "60000","80000",...,"1000000","1100000",...,"3000000"}.
     * shortName MUST be "Price" (same as {@link #PRICES_RENT}).
     */
    public static final RangeFilter PRICES_BUY = RangeFilter.of("Price"
            // , "<boundary-1>", "<boundary-2>", ...   (ascending, no "0")
    );

    // ==================== Surface ranges (m2 / sq ft) ====================

    /**
     * Surface (built or usable area) boundaries - usually the SECOND subdivision filter after price.
     * Same verification steps as the price lists (open-ended, inclusive/exclusive, invalid values,
     * leakage - listings with no surface vanish when it is applied).
     *
     * <p>Only include this filter if (a) it works through the URL alone, (b) it works for every
     * operation, and (c) almost every listing has a surface. Otherwise delete it from the priority
     * list. Note the unit: m2 vs sq ft (UK sites use sq ft: either convert the model value in
     * Phase 3 or keep the site's boundaries as they are - they are only used to split).
     *
     * <p>TODO(site): fill or remove. Example (m2): {@code "40","60","80",...,"500","600",...,"1200"}.
     */
    public static final RangeFilter SURFACES = RangeFilter.of("Surface"
            // , "<boundary-1>", "<boundary-2>", ...
    );

    // ==================== Property types (enum filter) ====================

    /**
     * Property type / sub-category - usually the LAST resort filter, after price and surface: it
     * splits an otherwise un-splittable dense price/surface band into flats / houses / etc.
     *
     * <p>The values MUST cover 100% of the site's residential listings when taken together (every
     * listing falls in exactly one value, or at least in one). Check against the unfiltered count.
     * Map each one to our {@code Basic.SubTypeEnum} for documentation purposes (the actual subtype
     * of each listing is parsed from the detail page in Phase 3, this list only drives splitting).
     *
     * <p>Beware of overlapping categories (a "penthouse" that is also under "flat"): overlap yields
     * duplicates (harmless but wasteful); gaps yield losses (harmful).
     *
     * <p>TODO(site): fill or remove. Example: {@code entry("flat","Flat"), entry("house","House")}.
     * The value is the site's own URL token, copied from a real search URL.
     */
    public static final EnumFilter PROPERTY_TYPES = EnumFilter.withAliases("Type", "propertyType"
            // , entry("<url-token>", "<Display name>")
    );

    // ==================== Other candidate filters (not enabled) ====================
    //
    // Bedrooms, bathrooms, sub-locations (districts/neighbourhoods), agency/advertiser type,
    // publication date... can be added as further RangeFilter / EnumFilter constants and appended to
    // the priority list in TemplatePhase1.getFilterPriority(). Prefer filters that:
    //   - are settable purely through the URL (no click/Action needed),
    //   - exist for EVERY operation and location,
    //   - are present on almost every listing (otherwise leakage),
    //   - are granular (many roughly equal buckets), and
    //   - have reliable inclusive/exclusive semantics.
    // Sub-LOCATIONS (districts) are often better than "bedrooms" for large cities because they
    // split by geography, which is stable and covers 100% of listings. Document the decision.
    //
    // public static final RangeFilter BEDROOMS = RangeFilter.of("Beds", "1", "2", "3", "4", "5");
}
