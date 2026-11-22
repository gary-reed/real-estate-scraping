package com.realestate.template.phase;

import com.scraper.commons.core.ScraperLanguage;
import com.scraper.commons.core.phase.ScraperBasicDetails;
import com.realestate.template.TemplateConstants;
import org.springframework.stereotype.Component;

/**
 * Basic, static facts about the target site. Injected into every Phase class and into the engine.
 *
 * <p>Everything here is answerable from the HOMEPAGE + a couple of search pages of the target site
 * (the "Site identity and access" and "Pagination and caps" sections of the recon blueprint).
 * Nothing here needs parsing logic. Values must be constants: they are read at startup,
 * by the Manager registration, by proxy selection and by all four phases.
 *
 * <p>The placeholder values below are NOT valid for any real site. They only make the template
 * compile and start. Replace every one of them.
 */
@Component
public class TemplateScraperDetails implements ScraperBasicDetails {

    /**
     * Unique identifier of this scraper/site. Format: {@code <site>-<cc>}, lowercase, ASCII,
     * no spaces (e.g. {@code brand-es}, {@code brand-uk}, {@code other-de}).
     *
     * <p>HOW TO RESOLVE: site brand name (from the domain, NOT the legal company name) + the
     * 2-letter country code of the market the site serves. If the same brand runs several country
     * sites (brand.com / brand.it / brand.pt), each one is a separate scraper with its own alias.
     *
     * <p>USED FOR: prefix of every listing id ({@code <alias><rawId>}, see
     * {@code Phase2Crawling.createListingIdUrl}), DB rows, logs, Manager registration and the
     * {@code scraper.name} key in application.yaml (they MUST be identical). Phase 0
     * {@code validateListingId} checks this prefix. CHANGING IT LATER ORPHANS ALL EXISTING DATA
     * (ids change), so pick it once.
     */
    @Override
    public String getSiteAlias() {
        // TODO(site): return "<site>-<cc>";   (can live in TemplateConstants if reused elsewhere)
        return "template-xx";
    }

    /**
     * Base URL of the site WITHOUT trailing slash, including scheme and the canonical host
     * (e.g. {@code https://www.example.com}, NOT {@code http://example.com} and NOT
     * {@code https://www.example.com/}).
     *
     * <p>HOW TO RESOLVE: open the homepage as a normal visitor from the target country and copy the
     * final URL origin after all redirects (http -> https, bare domain -> www, or the reverse).
     * Use the host that the site itself uses in its own links and canonical tags
     * ({@code <link rel="canonical">}). If the site redirects between hosts you must use the final
     * one, otherwise every request pays a redirect (slower, more detectable, and it can change
     * cookies/referrer).
     *
     * <p>USED FOR: building search URLs in Phase 1 ({@code buildUrlFromFilters}), absolutizing the
     * relative {@code href}s found on result cards in Phase 2, building agency/profile URLs in
     * Phase 3, and in the browser profile (homepage / host matching).
     */
    @Override
    public String getBaseDomain() {
        // TODO(site): return "https://www.<site-domain>";
        return "https://www.example.com";
    }

    /**
     * Number of listing cards the site shows on one full search-result page.
     *
     * <p>HOW TO RESOLVE: load a big search (thousands of results), page 1, and count the real
     * ORGANIC listing cards (not ads, not "similar", not featured/promoted extras that are added on
     * top of the results or repeat on every page). Check page 2, a middle page and the last page of
     * several searches: classify every card, and make sure {@code TemplatePhase2.resolveListingIDs}
     * excludes exactly the extras. Phase 0 FAILS unless page 1 of its fixture yields exactly this
     * number of ids, so extras that inflate the count break the validation. Some sites show a different number per device / per
     * sort order; measure it with the SAME browser profile the scraper uses (desktop viewport).
     * Some sites let you ask for more per page via a parameter ({@code ?pageSize=50}); if that is
     * supported by URL alone, and stable, using the bigger value reduces Phase 2 requests - but
     * then {@code TemplatePhase2.resolvePagination} must add that parameter to every page URL.
     *
     * <p>USED FOR: {@code pages = ceil(count / expectedListingsPerPage)} in Phase 2
     * ({@code SplitCount.calculatePageCount}). If it is too SMALL you fetch pages beyond the last
     * (empty pages, wasted requests, possible "nearby results" leakage). If it is too BIG you stop
     * early and MISS listings. When in doubt, use the real value and let it be exact.
     */
    @Override
    public int expectedListingsPerPage() {
        return TemplateConstants.LISTINGS_PER_PAGE;
    }

    /**
     * Maximum number of results a single split (search URL) may have before Phase 1 subdivides it
     * further with {@code getFilterPriority()}.
     *
     * <p>Choose {@code min(hardCap, cappedCountLimit, ~8 pages * expectedListingsPerPage)}:
     * <ul>
     *   <li><b>hardCap</b>: the maximum number of pages/results the site lets you reach for ONE
     *       search (many sites stop at e.g. 1000 results / 40 pages / page 100 even if the count
     *       says more; bisect on a huge search to find the last reachable page and note what
     *       happens past it: empty page, error, redirect to page 1, or silently the last page).</li>
     *   <li><b>cappedCountLimit</b>: some sites show a capped total ("1000+", "10.000+ results").
     *       A capped count can never be trusted above its cap, so the split limit must be BELOW
     *       the cap, otherwise a "1000+" split would be accepted although it holds many more.</li>
     *   <li><b>~8 pages</b>: one worker paginates a whole split sequentially, so splits are kept
     *       small (reference: 30 per page x 8 pages = 240). More splits = more parallelism and
     *       less loss on failures; fewer splits = fewer Phase 1 requests. 6-10 pages is the sweet
     *       spot.</li>
     * </ul>
     *
     * <p>USED FOR: Phase 1 stops subdividing when {@code count <= getMaxCountPerSearch()}.
     */
    @Override
    public int getMaxCountPerSearch() {
        return TemplateConstants.MAX_RESULTS_PER_SEARCH;
    }

    /**
     * Language of the site's UI/content (NOT of the developer, and NOT necessarily the country's
     * main language: a site in Belgium may be in Dutch or French; Switzerland: German/French/Italian).
     * Choose the {@link ScraperLanguage} enum entry matching the version of the site we scrape
     * (prefer the native-language version of the site).
     *
     * <p>USED FOR: Chrome locale / Accept-Language, proxy-country resolution and, if warmup is
     * enabled, the localized search queries typed on the search engine. It is a SEPARATE choice
     * from {@code ParserLanguage} in {@code TemplatePhase3#getLanguage()} (which selects the
     * keyword dictionaries for description parsing); they must describe the same language.
     * If the needed language does not exist in the enum, add it to {@code ScraperLanguage}
     * in scraper-commons (and a matching {@code ParserLanguage} + dictionaries in dialect-real-estate).
     */
    @Override
    public ScraperLanguage getLanguage() {
        // TODO(site): ScraperLanguage.SPANISH | ENGLISH | ENGLISH_US | GERMAN | ITALIAN | FRENCH | PORTUGUESE | DUTCH | POLISH
        return ScraperLanguage.ENGLISH;
    }

    /**
     * 2-letter ISO-3166 country code, LOWERCASE, of the market the site serves
     * (e.g. {@code "es"}, {@code "gb"} - note: United Kingdom is {@code gb}, not {@code uk}).
     *
     * <p>USED FOR: choosing proxies located in that country and browser locale/timezone. A wrong
     * country usually means instant blocks or geo-redirects, because most listing portals only
     * serve (or only trust) visitors from their own country.
     */
    @Override
    public String getCountryCode() {
        // TODO(site): return "<cc>";
        return "xx";
    }

    /**
     * ISO-4217 currency code in which the site shows prices (e.g. {@code "EUR"}, {@code "GBP"}).
     * Must match {@code scraper.currency} in application.yaml. It is stamped on
     * {@code Basic.currency} for every listing in Phase 3.
     *
     * <p>If the site lets the user switch currency, make sure the currency the scraper sees by
     * default (from the target country, without cookies) is the one declared here.
     */
    @Override
    public String getCurrency() {
        // TODO(site): return "<ISO-4217>";
        return "EUR";
    }

    // ------------------------------------------------------------------------------------------
    // OPTIONAL override (default: null = none). Uncomment only if the site REQUIRES extra HTTP
    // headers for every request (e.g. an API key header, a specific Accept header, or a mandatory
    // 'X-Requested-With' on a JSON endpoint). Browser-based scraping normally needs none, because
    // the real Chrome sends its own consistent headers; adding custom ones can make the browser
    // fingerprint inconsistent and trigger anti-bot detection.
    // ------------------------------------------------------------------------------------------
    // @Override
    // public java.util.Map<String, String> getCustomHeaders() {
    //     return java.util.Map.of();
    // }
}
