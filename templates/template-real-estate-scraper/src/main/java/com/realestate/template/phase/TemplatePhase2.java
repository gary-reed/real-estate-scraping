package com.realestate.template.phase;

import com.scraper.flavor.cms.domain.ListingIdUrl;
import com.scraper.flavor.cms.domain.SplitCount;
import com.scraper.commons.core.domain.Work;
import com.scraper.commons.core.domain.WorkResult;
import com.scraper.flavor.cms.phase.NewestFirstSort;
import com.scraper.flavor.cms.phase.Phase2Crawling;
import com.scraper.commons.core.phase.ScraperBasicDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 2 - CRAWLING: for every final split of Phase 1, page through ALL its result pages and
 * collect the listing ids + URLs (and optionally the card price / image count for change detection).
 * The engine then diffs the ids against the known ones (NEW / DELETED / existing) to decide what
 * Phase 3 has to fetch.
 *
 * <h2>The engine's algorithm (generic, in flavor-cms {@code Phase2Executor})</h2>
 * <ol>
 *   <li>For each split: {@code pages = ceil(count / expectedListingsPerPage)}.</li>
 *   <li>For {@code pageIndex0 = 0..pages-1}: {@link #resolvePagination} gives the URL, the worker
 *       fetches it, {@link #resolveListingIDs} extracts the cards.</li>
 *   <li>One worker paginates a whole split sequentially (that is why splits are kept small).</li>
 * </ol>
 *
 * <p>The site-specific work of this phase: the pagination URL pattern, the card/link selectors, the
 * listing-id extraction (and tracking-parameter clean-up), and (optionally) the crawl enrichment.
 * See the blueprint sections "Pagination and caps" and "Result cards".
 *
 * <h2>Things to measure on the live site FIRST</h2>
 * <ul>
 *   <li><b>Pagination kind</b>: numbered pages in the URL (path or query), "load more" /
 *       infinite scroll, or a JSON page API. This template assumes URL-addressable numbered pages
 *       (the supported model of {@code resolvePagination}). If the site only has infinite scroll,
 *       look for the underlying JSON/XHR endpoint and address its pages instead; it changes what the
 *       "page content" is and therefore {@link #resolveListingIDs}.</li>
 *   <li><b>Hard cap of reachable pages</b> (bisect on a huge search): record what happens past it
 *       (empty page, last page repeated, redirect to page 1, error). Phase 1's
 *       {@code maxCountPerSearch} must keep every split under it.</li>
 *   <li><b>Promoted/sponsored listings repeated on EVERY page</b>, or injected at fixed positions:
 *       they must be recognised (and either dropped or de-duplicated; ids are de-duplicated per page
 *       here, and by the engine across pages).</li>
 *   <li><b>Order stability</b>: is the order stable when reloading a page? Is there a deterministic
 *       sort option (e.g. newest first / price ascending) that can be forced via the URL? Unstable
 *       order across page loads makes listings appear on two pages or on none (a crawl-time race).
 *       If a deterministic sort exists and costs nothing, add it to every page URL.
 *       A NEWEST-FIRST sort is a separate, optional capability: see the end of this class.</li>
 *   <li><b>"Nearby / similar" results appended when results are few</b>: they leak listings from
 *       OUTSIDE the split into the last page. Detect their container and exclude it in
 *       {@link #resolveListingIDs} (otherwise the same listing shows up in many splits, and the
 *       engine sees phantom NEW/DELETED churn).</li>
 * </ul>
 */
@Component
public class TemplatePhase2 implements Phase2Crawling {

    @SuppressWarnings("unused") // available for the implementation (e.g. log.debug("Extracted {} listing IDs from page", n))
    private static final Logger log = LoggerFactory.getLogger(TemplatePhase2.class);

    private final ScraperBasicDetails scraperDetails;

    public TemplatePhase2(ScraperBasicDetails scraperDetails) {
        this.scraperDetails = scraperDetails;
    }

    /** Site alias used as the id prefix. Generic: delegates to {@link ScraperBasicDetails}. */
    @Override
    public String getSiteAlias() {
        return scraperDetails.getSiteAlias();
    }

    // ==========================================================================================
    // getPhaseBaseUrl
    // ==========================================================================================

    /**
     * OPTIONAL. Derives from a split URL its PAGE-1 / "base search" URL: the same split, without any
     * pagination part. Default (kept): {@code null} = not applicable.
     *
     * <p>The browser worker uses it for "split affinity" warm navigation: homepage -> this base URL ->
     * the filtered work URL (so each request arrives with a believable referrer and cookie state, as
     * a real user drilling down would). It is fed through
     * {@code Phase2Executor#getWorkerConfig} into the worker's {@code phaseBaseUrl}.
     *
     * <p>Implement it when the page URLs contain a pagination segment/parameter that must be stripped
     * to get page 1 (reference: remove {@code pagina-N.htm}, ensure the trailing slash). If the split
     * URLs never carry pagination (page 1 = the split URL itself), return the split URL unchanged,
     * or leave the default null. Keep it total: never throw; return the input on any parsing problem.
     */
    @Override
    public String getPhaseBaseUrl(String splitUrl) {
        // TODO(site) (optional): strip the pagination part of splitUrl and return page 1's URL.
        return null;
    }

    // ==========================================================================================
    // resolveIdFromListingUrl
    // ==========================================================================================

    /**
     * Extracts the RAW (unprefixed) listing id from a listing URL, or {@code null} when the URL is
     * not a listing URL. The engine prefixes it with the site alias ({@code createListingIdUrl}).
     *
     * <p>HOW TO RESOLVE: open ~20 real detail URLs and find where the id is:
     * <ul>
     *   <li>a path segment ({@code /inmueble/12345678/}), the tail of a slug
     *       ({@code /for-sale/house-123456}), a query parameter ({@code ?id=123}), or only in the page;</li>
     *   <li>write the STRICTEST regex that matches only that token (reference:
     *       {@code "/(\\d{7,})/?"}), anchored enough that a number elsewhere in the path/query
     *       (page numbers, price filters, postcode) cannot match;</li>
     *   <li>URLs may be ABSOLUTE or RELATIVE, with tracking parameters ({@code ?utm_*}, {@code ?ref=},
     *       {@code #fragment}) - ignore them; the same listing must always yield the same id;</li>
     *   <li>the id must be STABLE (not a slug that changes when the title is edited) and UNIQUE
     *       across the site; if the site has several id spaces (e.g. ordinary listings vs new-build
     *       "projects" with their own ids), make the id disjoint (prefix the kind) or skip them (see
     *       the special cards in {@link #resolveListingIDs}).</li>
     * </ul>
     *
     * <p>Keep this consistent with {@code TemplatePhase0#validateListingId}.
     */
    @Override
    public String resolveIdFromListingUrl(String url) {
        // TODO(site): implement. e.g.
        //   private static final Pattern ID_PATTERN = Pattern.compile("/(\\d{7,})/?");
        //   Matcher m = ID_PATTERN.matcher(url);
        //   return m.find() ? m.group(1) : null;
        throw new UnsupportedOperationException(
                "TemplatePhase2.resolveIdFromListingUrl() is not implemented - see the method Javadoc");
    }

    // ==========================================================================================
    // resolvePagination
    // ==========================================================================================

    /**
     * Builds the {@link Work} that fetches result page {@code pageNumber} (0-INDEXED: 0 = first page)
     * of a split.
     *
     * <p>Page 0 is ALWAYS the split URL itself ({@code new Work(split.getSplitUrl())}); do not add a
     * page-1 suffix: many sites redirect {@code page=1} to the canonical URL (an extra request) or
     * even treat it as a different, "duplicate" page.
     *
     * <p>For {@code pageNumber >= 1} append the site's pagination token to the split URL, converting
     * to the SITE's indexing (this is the classic off-by-one: the site's page 2 is
     * {@code pageNumber = 1}). Cases seen in the wild:
     * <ul>
     *   <li>Path suffix: {@code <split>/pagina-2.htm}, {@code <split>/page-2/}, {@code <split>/p2};</li>
     *   <li>Query parameter: {@code ?page=2}, {@code ?pn=2}, {@code &p=2}: append with {@code ?} or
     *       {@code &} depending on whether the split URL already has a query string;</li>
     *   <li>Offset based: {@code ?start=24}, {@code ?offset=48} = {@code pageNumber * listingsPerPage};
     *       in that case {@code scraperDetails.expectedListingsPerPage()} must be exact;</li>
     *   <li>Cursor based (opaque "next page" token from the previous response): NOT expressible here
     *       (the URL depends on the previous page); look for a numbered alternative or a JSON endpoint.</li>
     * </ul>
     * Preserve ALL the split's filters and keep the parameter order stable. Verify the page-N URL by
     * hand, and verify that the LAST page (e.g. count=7 results on 30/page: page index 0 only) never
     * 404s because of a rounding mistake. Optionally force a deterministic sort here (see class notes).
     *
     * <p>Optionally also set a referer: the previous page of the same split is the natural referrer
     * ({@code Work.builder(url).referer(...)}).
     */
    @Override
    public Work resolvePagination(SplitCount split, int pageNumber) {
        // Page 0 is the split URL itself. This line is generic and safe to keep.
        if (pageNumber == 0) {
            return new Work(split.getSplitUrl());
        }
        // TODO(site): build the page-N URL (see Javadoc). Remember: pageNumber is 0-indexed.
        throw new UnsupportedOperationException(
                "TemplatePhase2.resolvePagination() is not implemented for pageNumber > 0 - see the method Javadoc");
    }

    // ==========================================================================================
    // resolveListingIDs
    // ==========================================================================================

    /**
     * Extracts the listings found on ONE result page: a {@link ListingIdUrl} per listing card
     * (prefixed id + absolute detail URL + referrer), de-duplicated by id, in page order.
     *
     * <p>Skeleton (what the reference does, keep the same flow):
     * <pre>
     * if (result.getContent() == null) return List.of();
     * String searchPageUrl = result.getWork() != null ? result.getWork().getUrl() : null; // natural referrer
     * Document doc = Jsoup.parse(result.getContent());
     * Set&lt;String&gt; seenIds = new LinkedHashSet&lt;&gt;();
     * List&lt;ListingIdUrl&gt; listings = new ArrayList&lt;&gt;();
     * for (Element link : doc.select("&lt;card link selector&gt;")) {
     *     String href = link.attr("href");
     *     if (!href.startsWith("http")) href = scraperDetails.getBaseDomain() + href;   // absolutize
     *     String id = resolveIdFromListingUrl(href);
     *     if (id != null &amp;&amp; seenIds.add(id)) {
     *         ListingIdUrl listing = createListingIdUrl(href);   // generic: prefixes id, sets originalListingId
     *         if (listing != null) { listing.setReferrer(searchPageUrl); listings.add(listing); }
     *     }
     * }
     * return listings;
     * </pre>
     *
     * <p><b>HOW TO RESOLVE the selectors</b> (blueprint "Result cards"): inspect the real DOM and give
     * a PRIMARY selector plus 1-2 FALLBACKS tried in order when the primary matches nothing (the
     * reference tries three). Prefer stable hooks ({@code id}, {@code data-*} attributes, semantic
     * tags, itemprop/schema.org) over generated/hashed CSS class names (CSS-in-JS builds like
     * {@code .sc-1x2y3z} change on every deploy).
     * <ul>
     *   <li><b>Where are the cards</b>: the container of the real result list; scope your selector to
     *       it so that "similar properties", "recently viewed", ads, footers and recommendation
     *       carousels are NOT picked up.</li>
     *   <li><b>Link</b>: the {@code a[href]} that opens the detail page (often the title or the main
     *       image link). Match on the detail-URL PATTERN (e.g. {@code a[href*='/inmueble/']}).
     *       Strip tracking parameters / fragments from the final URL when they would make the same
     *       listing look different, but KEEP parameters the detail page needs.</li>
     *   <li><b>Embedded JSON alternative</b>: some sites ship the whole result list as JSON
     *       ({@code __NEXT_DATA__}, {@code window.__PRELOADED_STATE__}, {@code ld+json ItemList}) -
     *       sturdier than the DOM and it usually also contains price and image count. Prefer it if
     *       present, with the DOM selector as fallback.</li>
     *   <li><b>Special cards</b>: (a) NEW-BUILD "projects"/developments that group several units
     *       under one card (decide: skip them, or expand them to the unit ids - the units usually
     *       appear as separate listings elsewhere), (b) ads / native advertising, (c) promoted
     *       listings repeated on every page, or featured extras added on top of the organic results,
     *       (d) "nearby results" blocks. Filter them out here and document each decision. The result
     *       must be exactly the ORGANIC listings: on a full page its size must equal
     *       {@code expectedListingsPerPage} (Phase 0 checks it), so test it on saved pages.</li>
     *   <li><b>Empty page</b>: an out-of-range page must return an empty list (not throw), and the
     *       engine uses the count-based page total, so an empty last page just yields nothing. A
     *       BLOCK page also yields an empty list: the blocked state is detected upstream
     *       ({@code WorkResult.isBlocked()}) - do not try to detect it here.</li>
     * </ul>
     *
     * <p><b>CRAWL ENRICHMENT (optional)</b>: if {@link #supportsCrawlEnrichment()} returns true, set
     * the card's displayed price and image count on each {@code ListingIdUrl}
     * ({@code new ListingIdUrl(listingId, url, siteAlias, crawlPrice, crawlImageCount)}):
     * <ul>
     *   <li>{@code crawlPrice} is a PLAIN NUMBER in the SITE's currency, as on the card
     *       ({@code "250000"}, {@code "1250.50"}): no symbols, no thousand separators, no "from"
     *       prices. {@code null} = unknown (and an unknown value never counts as a change).</li>
     *   <li>{@code crawlImageCount} is the number of photos shown on the card, or {@code null} when
     *       the card does not show it.</li>
     * </ul>
     * They are used for DELTA detection: a card price that differs from the stored one, or more
     * photos than stored, marks the listing as UPDATE, and Phase 3 re-fetches it. Only do it if the
     * card values are reliable and stable across page loads (no rounding that differs from the detail
     * page). Phase 3 logs {@code [CRAWL_PRICE_MISMATCH]} when re-fetched listings still differ from
     * their card, which is the sign of a wrong card parse.
     */
    @Override
    public List<ListingIdUrl> resolveListingIDs(WorkResult result) {
        // TODO(site): implement as described above.
        throw new UnsupportedOperationException(
                "TemplatePhase2.resolveListingIDs() is not implemented - see the method Javadoc");
    }

    // ==========================================================================================
    // supportsCrawlEnrichment
    // ==========================================================================================

    /**
     * Whether {@link #resolveListingIDs} fills the crawl-time price and image count. Default
     * (kept): {@code false} = these fields are ignored by the delta calculation.
     *
     * <p>Return {@code true} ONLY when the result cards reliably show the price (and ideally the
     * number of images) and you actually populate them (see above). Needed for a
     * {@link NewestFirstSort#LAST_MODIFIED} site, whose changes must be visible on the cards.
     */
    @Override
    public boolean supportsCrawlEnrichment() {
        return false;
    }

    // ==========================================================================================
    // newestFirstSort / resolveNewestFirstPagination
    // ==========================================================================================

    /*
     * OPTIONAL: newest-first crawl. If the site can sort its results newest first through the URL,
     * declaring it lets the user switch on "Newest-first crawl": each split then stops as soon as its
     * pages hold nothing new, which makes Phase 2 much faster (but deleted listings are not detected
     * in such a run). Measure on the live site FIRST, with a sorted results page:
     *
     *   - PUBLICATION_DATE: the sort is by FIRST publication. Edited or renewed listings do NOT
     *     move to the top. A split stops after 1 page without new listings.
     *   - LAST_MODIFIED: the sort is by last modification. Edited or renewed listings rise to the top.
     *     Needs crawl enrichment (supportsCrawlEnrichment() == true) so changes show on the cards.
     *     A split stops after 2 pages in a row without new or changed listings.
     *
     * If unsure which one it is, declare LAST_MODIFIED: it is always correct, just slower.
     *
     * Uncomment both methods and set the site's sort parameter. Example:
     *
     *   private static final String NEWEST_FIRST = "sort=date-desc";
     *
     *   @Override public NewestFirstSort newestFirstSort() { return NewestFirstSort.LAST_MODIFIED; }
     *
     *   @Override
     *   public Work resolveNewestFirstPagination(SplitCount split, int pageNumber) {
     *       String url = resolvePagination(split, pageNumber).getUrl();
     *       return new Work(url + (url.contains("?") ? "&" : "?") + NEWEST_FIRST);
     *   }
     *
     * Check that the split URLs from Phase 1 do not already carry the sort parameter.
     */
}
