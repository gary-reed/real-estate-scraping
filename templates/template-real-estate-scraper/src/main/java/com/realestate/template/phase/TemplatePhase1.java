package com.realestate.template.phase;

import com.scraper.flavor.cms.domain.SplitCount;
import com.scraper.commons.core.domain.Work;
import com.scraper.commons.core.domain.WorkResult;
import com.scraper.commons.core.filter.FilterValue;
import com.scraper.commons.core.filter.SplitFilter;
import com.scraper.flavor.cms.phase.Phase1Splitting;
import com.scraper.commons.core.phase.ScraperBasicDetails;
import com.realestate.template.TemplateConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Phase 1 - SPLITTING: turn the WHOLE site into a set of search URLs ("splits") that each hold at
 * most {@link ScraperBasicDetails#getMaxCountPerSearch()} results, so Phase 2 can paginate each one
 * completely (a site never lets you page through "everything").
 *
 * <h2>The engine's algorithm (generic, in flavor-cms {@code Phase1Executor})</h2>
 * <ol>
 *   <li>{@link #getBasicSplits()} -> every basic split (normally LOCATION x OPERATION).</li>
 *   <li>Each split is fetched; {@link #parseSplitCount} reads the total result count from the page.</li>
 *   <li>{@code count > maxCountPerSearch}: the engine subdivides using {@link #getFilterPriority}
 *       (first filter that can still split, in order; enum = one child per value, range = binary
 *       split over the ordered boundary list), building each child's URL with
 *       {@link #buildUrlFromFilters}. Repeat on every child until small enough or filters exhausted.</li>
 *   <li>Blocked / failed / unresolved (-1) counts are retried by the engine (max 3, then the run
 *       stops with a fatal error), never treated as "0 results".</li>
 * </ol>
 *
 * <p>So the site-specific work of this phase is exactly: (a) the constants in
 * {@link TemplateConstants}, (b) how to READ a count from a result page, (c) how to BUILD a URL for
 * ANY combination of filter values, (d) the ORDER of the subdivision filters. Everything else is
 * generic. See the blueprint sections "URL grammar", "Locations", "Result count", "Filter inventory"
 * and "Filter priority (the key decision)".
 */
@Component
public class TemplatePhase1 implements Phase1Splitting {

    private static final Logger log = LoggerFactory.getLogger(TemplatePhase1.class);

    @SuppressWarnings("unused") // used by buildUrlFromFilters once implemented (base domain)
    private final ScraperBasicDetails scraperDetails;

    public TemplatePhase1(ScraperBasicDetails scraperDetails) {
        this.scraperDetails = scraperDetails;
    }

    // ==========================================================================================
    // getBasicSplits / getBasicFilters
    // ==========================================================================================

    /**
     * The initial splits: one {@link Work} per combination of the BASIC enum filters (normally
     * every location x every operation), each carrying its filter values so the engine can later
     * subdivide it and compute the split group. This is the standard generic implementation (it
     * only depends on the constants and on {@link #buildUrlFromFilters}); change it ONLY if the
     * site's structure is not a plain cartesian product, for example:
     * <ul>
     *   <li>Some operations only exist for some locations (skip the missing pairs);</li>
     *   <li>A third basic dimension (e.g. property category: homes / commercial / land, each with
     *       its own URL tree) - add it as another loop and to {@link #getBasicFilters()};</li>
     *   <li>The basic URL is NOT derivable by {@code buildUrlFromFilters} (rare). Then build it by
     *       hand here, but the children created by the engine still go through the builder.</li>
     * </ul>
     *
     * <p>Properties set on each Work: {@code splitBaseUrl} = the URL of the basic split itself;
     * used as navigation affinity / referrer hint for the whole subtree of that split.
     */
    @Override
    public List<Work> getBasicSplits() {
        List<Work> splits = new ArrayList<>();

        for (String region : TemplateConstants.REGIONS.getValues()) {
            for (String operation : TemplateConstants.OPERATIONS.getValues()) {
                List<FilterValue> filters = List.of(
                        FilterValue.of(TemplateConstants.REGIONS, region),
                        FilterValue.of(TemplateConstants.OPERATIONS, operation)
                );
                String url = buildUrlFromFilters(filters);
                splits.add(Work.builder(url)
                        .filters(filters)
                        .property("splitBaseUrl", url)
                        .build());
            }
        }

        log.info("Built {} initial search splits ({} regions x {} operations)",
                splits.size(),
                TemplateConstants.REGIONS.size(),
                TemplateConstants.OPERATIONS.size());

        return splits;
    }

    /**
     * Which filters compose a BASIC split (the "split group"). Used as the machine key
     * ({@code split_group}: the raw values joined with {@code _}, e.g. {@code <location>_<operation>})
     * and for the human label in logs / the Manager UI. Must list the SAME filters, in the SAME order,
     * as the loops of {@link #getBasicSplits()}. Range filters never belong here.
     */
    @Override
    public List<SplitFilter> getBasicFilters() {
        return List.of(TemplateConstants.REGIONS, TemplateConstants.OPERATIONS);
    }

    // ==========================================================================================
    // parseSplitCount
    // ==========================================================================================

    /**
     * Reads the TOTAL number of results of a search from the fetched result page and returns it in a
     * {@link SplitCount} (url + a name + the count + the filters of the work).
     *
     * <p><b>Contract:</b> return {@code resultCount = -1} whenever the count cannot be read (null
     * content, block / CAPTCHA / challenge page, layout changed, parse error). The engine retries
     * -1 and eventually aborts the run. NEVER return 0 for "could not read": a wrongly-read 0 makes
     * a huge split look empty and silently drops all its listings. Return 0 ONLY when the page
     * positively says there are no results.
     *
     * <p>Skeleton (the reference does exactly this, keep it):
     * <pre>
     * Work work = result.getWork();
     * List&lt;FilterValue&gt; filters = work.getFilters();
     * if (result.getContent() == null) return new SplitCount(work.getUrl(), "split", -1, filters);
     * try {
     *     int count = parseCount(Jsoup.parse(result.getContent()));
     *     return new SplitCount(work.getUrl(), "split", count, filters);
     * } catch (Exception e) {
     *     log.warn(...); return new SplitCount(work.getUrl(), "split", -1, filters);
     * }
     * </pre>
     *
     * <p><b>HOW TO FIND the count (try in this order, prefer the sturdiest):</b>
     * <ol>
     *   <li><b>Embedded JSON</b>: {@code application/ld+json} ({@code numberOfItems}),
     *       {@code __NEXT_DATA__} / {@code window.__INITIAL_STATE__} / {@code dataLayer}
     *       (look for {@code totalResults}, {@code totalCount}, {@code resultsCount}, {@code total}).
     *       Immune to wording changes and locale number formats. Use this when present.</li>
     *   <li><b>A dedicated element</b> (stable {@code id} / {@code data-*} / semantic tag):
     *       e.g. {@code [data-testid="results-count"]}, {@code #resultCount}, the page's
     *       {@code <h1>} ("1,234 homes in ..."), or {@code <title>} / {@code og:title} /
     *       {@code meta[name=description]}. Give the primary selector plus one or two fallbacks.</li>
     *   <li><b>Regex over visible text</b>: number + a localized noun
     *       ({@code "([\\d.,\\s]+)\\s+(properties|results|homes|listings)"}).
     *       Weakest: wording changes silently break it, and unrelated numbers match.</li>
     *   <li><b>NEVER</b> fall back to "number of cards on this page" as the total. It is wrong for any
     *       result set larger than one page (the reference has such a last resort; do NOT copy it for
     *       a new site, return -1 instead - it hides outages as small counts).</li>
     * </ol>
     *
     * <p><b>NUMBER FORMATS</b> to handle: thousands separators ({@code 1.234}, {@code 1,234},
     * {@code 1 234}, non-breaking space U+00A0, thin space U+202F), decimal-less integers only,
     * and CAPPED counts such as {@code "1000+"} / {@code "More than 10,000"} / {@code "10.000+"}.
     * A capped count is a LOWER bound: if the site shows one, return the cap value (it will exceed
     * {@code maxCountPerSearch}, so the engine keeps subdividing, which is what we want) and note the
     * cap in the README; the split limit in {@code getMaxCountPerSearch()} must stay BELOW the cap.
     * Use {@code NumberParsingUtils.strNumberToInt} for tolerant integer parsing.
     *
     * <p><b>ZERO RESULTS vs BLOCK PAGE</b> (the single most important distinction here):
     * <ul>
     *   <li>A genuine "no results" page has its own marker ("0 properties", "no results found",
     *       "no matches", an empty-state element). Return 0 only when that marker is present.</li>
     *   <li>A block / challenge / CAPTCHA / consent-wall / error page has NO count and NO empty-state
     *       marker ("Access denied", "Just a moment", a captcha shell). Return -1.</li>
     *   <li>Also return -1 for a page that is a different page than requested (redirect to the
     *       homepage / to the unfiltered search because a filter value was invalid). The
     *       {@code result.getFinalUrl()} vs the requested URL comparison is a cheap guard here.</li>
     * </ul>
     *
     * <p><b>WATCH OUT FOR</b>: counts that include "nearby / similar / sponsored" results (then the
     * count is inflated and the cards leak in from outside the split - see Phase 2 notes); counts
     * shown only after JS runs (then browser rendering is required and WorkResult content is the
     * rendered DOM - normally already the case); and counts that differ per locale/currency.
     *
     * <p><b>VERIFY</b> (blueprint "Result count"): crawl every page of two SMALL searches and check
     * that the number of unique ids equals the parsed count.
     */
    @Override
    public SplitCount parseSplitCount(WorkResult result) {
        // TODO(site): implement as described above.
        throw new UnsupportedOperationException(
                "TemplatePhase1.parseSplitCount() is not implemented - see the method Javadoc");
    }

    // ==========================================================================================
    // getFilterPriority
    // ==========================================================================================

    /**
     * The ORDERED list of filters the engine may use to subdivide a split that is too big. The
     * engine walks the list and uses the FIRST filter that can still split the current split:
     * enums that are not yet applied; ranges that still have room between their two bounds.
     * The list should also contain the CONTEXT filters (location, operation: already applied on
     * every split, so they never subdivide) because they supply aliases for logging.
     *
     * <p><b>THIS IS THE KEY DECISION OF THE WHOLE SCRAPER.</b> It decides how many requests Phase 1
     * needs and whether every final split fits in the page cap. Decide it with DATA, not intuition:
     * <ol>
     *   <li>Take the 2-3 LARGEST location x operation searches and one small one.</li>
     *   <li>Drill down by hand the way the engine would and record the count tree.</li>
     *   <li>Follow the densest branch to the narrowest adjacent price band: is it still above
     *       {@code maxCountPerSearch}? Then find which 2nd and 3rd filters bring it down, and whether
     *       anything stays above the target even when all filters are used.</li>
     *   <li>Compare at least two orderings (price -> surface -> type vs sub-location -> price -> type)
     *       by number of requests and number of final splits. Fewer requests and more balanced
     *       splits win. Pick the filters that are granular, present on (almost) every listing,
     *       available for every operation, and settable via the URL alone.</li>
     * </ol>
     *
     * <p>Typical order: PRICE first (always present, very granular), then SURFACE, then PROPERTY TYPE
     * (last resort). Sub-locations (districts) are often a better 1st or 2nd step for the big cities.
     *
     * <p>PER-OPERATION lists: select the filter instance from {@code splitCount}, e.g. buy prices vs
     * rent prices (see below). The same {@code shortName} ("Price") for both keeps the URL builder
     * simple.
     *
     * <p>Filters whose values are empty never subdivide (the engine skips them), which is why the
     * template compiles and runs with empty constants.
     */
    @Override
    public List<SplitFilter> getFilterPriority(SplitCount splitCount) {
        SplitFilter priceFilter = isBuyOperation(splitCount)
                ? TemplateConstants.PRICES_BUY
                : TemplateConstants.PRICES_RENT;
        return List.of(
                // Context filters (already applied on every basic split; never subdivide)
                TemplateConstants.REGIONS,
                TemplateConstants.OPERATIONS,
                // Subdivision filters in priority order.
                // TODO(site): re-order / add / remove according to the drill-down experiments.
                priceFilter,
                TemplateConstants.SURFACES,
                TemplateConstants.PROPERTY_TYPES
        );
    }

    /**
     * Whether the split belongs to the BUY operation (selects the buy price list). Compare the RAW
     * operation token of the split ({@code splitCount.getFilterValue(TemplateConstants.OPERATIONS)})
     * with the buy token you put in {@link TemplateConstants#OPERATIONS}.
     * If the site has more than two operations with different price scales (BUY, RENT, SHARE rooms),
     * replace this boolean by a switch returning the matching RangeFilter.
     */
    private boolean isBuyOperation(SplitCount splitCount) {
        // TODO(site): return "<buy-token>".equals(splitCount.getFilterValue(TemplateConstants.OPERATIONS));
        return false;
    }

    // ==========================================================================================
    // buildUrlFromFilters
    // ==========================================================================================

    /**
     * Builds the COMPLETE search URL for ANY combination of the filter values - the inverse of how
     * the site encodes its filters. The engine calls it for the basic splits and for every child
     * created by subdividing, with arbitrary subsets of the filters, so it must be total: a missing
     * filter simply means "not applied".
     *
     * <p>Every filter must therefore be settable through the URL ALONE (no clicks/dropdowns). If a
     * candidate filter can only be applied through the UI, do not use it for splitting.
     *
     * <p><b>HOW TO RESOLVE the URL grammar</b> (blueprint "URL grammar"): apply filters one by one in
     * the site's own UI and watch the URL; then confirm by hand-typing the URL in a clean (cookie-less)
     * session. Document, for each filter: where it goes (path segment vs query parameter), its token
     * and the separator, how min/max are named, and:
     * <ul>
     *   <li><b>Parameter ORDER</b>: does the site redirect to a canonical order if you permute?
     *       A redirect costs a request and may break filter validation. Always emit the canonical
     *       order the site itself would produce, and keep it stable (split aliases and the DB use
     *       the URL / filters).</li>
     *   <li><b>Trailing slash</b>, {@code .html} suffix, lower/upper case, URL-encoding of values
     *       (accents, spaces, commas): copy what the site itself emits.</li>
     *   <li><b>Range bounds</b>: how are min and max encoded ({@code precio-desde_100} /
     *       {@code precio-hasta_200}, {@code ?minPrice=100&maxPrice=200}, {@code price=100-200},
     *       {@code price=100-} for open-ended)? Which bound is the FROM and which the TO in
     *       {@link FilterValue}: for a range, {@code fv.getValue()} is the FROM (lower, may be null)
     *       and {@code fv.getToValue()} is the TO (upper, may be null); open-ended = null.</li>
     *   <li><b>Skip null AND "0" bounds</b> (the guard from the reference): a "0" min/max is not a
     *       constraint, and emitting it can make the site ignore the filter or return an error.</li>
     *   <li><b>Single-value vs multi-value</b> filters: some sites encode several filters in ONE
     *       path segment joined by commas ({@code /con-a,b,c/}) with a fixed internal order, others
     *       use separate segments or query parameters.</li>
     *   <li><b>Filters that change the PATH structure</b> (some sites move the property type or the
     *       location into the path, others keep them as query parameters). The builder must produce
     *       the right shape for each subset.</li>
     *   <li><b>Pagination is NOT part of this URL</b>: page N is added later by Phase 2
     *       ({@code resolvePagination}); the split URL is always page 1.</li>
     *   <li><b>Operation-dependent</b> tokens: the price/surface parameter names may differ per
     *       operation (rent vs buy); read the operation from the filters if so.</li>
     * </ul>
     *
     * <p><b>VERIFY</b>: for every filter, an INVALID or off-list value must not be silently ignored by
     * the site (an ignored filter returns the unfiltered set and splitting never converges). Test one
     * URL per filter and confirm the count changes in the expected direction; confirm the builder is
     * deterministic (same filters -> same URL).
     *
     * <p>Use the helper methods below ({@link #getFilterValue}, {@link #getRangeFilter}) to read the
     * values by filter, as the reference does:
     * <pre>
     * String operation = getFilterValue(filters, TemplateConstants.OPERATIONS).orElse(&lt;default&gt;);
     * String region    = getFilterValue(filters, TemplateConstants.REGIONS).orElse(&lt;default&gt;);
     * Optional&lt;FilterValue&gt; price = getRangeFilter(filters, "Price");
     * </pre>
     */
    @Override
    public String buildUrlFromFilters(List<FilterValue> filters) {
        // TODO(site): implement the URL grammar described above.
        throw new UnsupportedOperationException(
                "TemplatePhase1.buildUrlFromFilters() is not implemented - see the method Javadoc");
    }

    // ==========================================================================================
    // shouldSkipSplit
    // ==========================================================================================

    /**
     * OPTIONAL: return {@code true} to drop a split entirely (it is not counted, subdivided or
     * crawled). Default (kept): never skip.
     *
     * <p>Use it for splits that are known to be empty/invalid/duplicated for this site, instead of
     * special-casing them elsewhere, e.g.:
     * <ul>
     *   <li>combinations that do not exist (an operation not offered in some locations, where the site
     *       would redirect to a different page and return a misleading count);</li>
     *   <li>a sub-split that is the SAME result set as its parent (the site ignores a filter for this
     *       combination) - skipping avoids duplicate crawling;</li>
     *   <li>temporary blacklisting of a problematic location while keeping it in the constants.</li>
     * </ul>
     */
    @Override
    public boolean shouldSkipSplit(SplitCount split) {
        return false;
    }

    // ==========================================================================================
    // Helpers (generic, copied from the reference scraper; used by buildUrlFromFilters)
    // ==========================================================================================

    /** Raw value of the (enum) filter, if set in the list. */
    @SuppressWarnings("unused")
    private Optional<String> getFilterValue(List<FilterValue> filters, SplitFilter target) {
        if (filters == null) return Optional.empty();
        return filters.stream()
                .filter(f -> f.getFilter().getShortName().equals(target.getShortName()))
                .map(FilterValue::getValue)
                .findFirst();
    }

    /** The range filter value with the given shortName (e.g. "Price"), if set in the list. */
    @SuppressWarnings("unused")
    private Optional<FilterValue> getRangeFilter(List<FilterValue> filters, String shortName) {
        if (filters == null) return Optional.empty();
        return filters.stream()
                .filter(f -> f.getFilter().getShortName().equals(shortName) && f.getFilter().isRange())
                .findFirst();
    }
}
