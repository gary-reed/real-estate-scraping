package com.realestate.template.phase;

import com.scraper.flavor.cms.config.ScraperConfig;
import com.scraper.commons.core.domain.AjaxCapture;
import com.scraper.commons.core.domain.Work;
import com.scraper.commons.core.domain.WorkResult;
import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.ContactExternal;
import com.realestate.dialect.model.Display;
import com.realestate.dialect.model.FlatMate;
import com.realestate.dialect.model.Furnish;
import com.realestate.dialect.model.Interior;
import com.scraper.flavor.cms.model.Location;
import com.realestate.dialect.model.Property;
import com.scraper.flavor.cms.model.ScrapingDetails;
import com.realestate.dialect.model.Transaction;
import com.scraper.flavor.cms.model._ListingDetails;
import com.realestate.dialect.model.RealEstateListing;
import com.realestate.dialect.model.RealEstateListing.AnnounceTypeEnum;
import com.scraper.commons.core.phase.ScraperBasicDetails;
import com.realestate.dialect.RealEstateParser;
import com.realestate.dialect.RealEstateSettings;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.realestate.dialect.parsing.validation.AnomalyBounds;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 3 - DETAIL PARSING: turn the HTML of ONE listing detail page into our {@link RealEstateListing}
 * model. This is by far the biggest site-specific piece: everything else is wiring.
 *
 * <h2>The pipeline (generic, in {@link ScraperParser#executeParseLogic})</h2>
 * <ol>
 *   <li>{@code wasDelisted(result)}: if true the listing is stored as DELETED and parsing is skipped.</li>
 *   <li>{@link #parseListingDetails} (THIS CLASS): scraper-specific extraction from the page.
 *       It must only EXTRACT. It must NOT do anomaly checks, recalculation or description-based
 *       guessing: the next steps do that for every scraper alike.</li>
 *   <li>Site-agnostic cleanup, anomaly detection (resets absurd values: price/surface ratio, bedrooms
 *       out of range...), keyword RESOLVERS over the description (language dictionaries fill missing
 *       fields such as amenities, bedrooms, furnished, pets...), anomaly re-check,
 *       USD price conversion when the site currency is not USD ({@code originalPrice} = site amount,
 *       {@code price} = USD), and recalculation of derived values (price ratio, floor amenity, term
 *       prices).</li>
 * </ol>
 * So the parser should fill what the page states explicitly and structured, and leave the rest null:
 * a wrong value is far worse than a missing one (the resolvers run only on MISSING fields).
 *
 * <h2>Where to get each field (rank the sources best -> worst for THIS site, per field)</h2>
 * <ol>
 *   <li><b>JSON-LD</b> ({@code <script type="application/ld+json">}: schema.org
 *       {@code Product / Offer / RealEstateListing / Residence / Accommodation / SingleFamilyResidence}:
 *       price, priceCurrency, address, geo, numberOfRooms, floorSize, image...).</li>
 *   <li><b>Embedded state JSON</b>: {@code __NEXT_DATA__}, {@code window.__INITIAL_STATE__ /
 *       __PRELOADED_STATE__ / __APOLLO_STATE__}, {@code dataLayer.push(...)}, a {@code config} blob.
 *       Often has EVERY field, in machine form (coordinates, ids, enums, full image list). Parse it
 *       with Jackson ({@code JsonMapper} with {@code ALLOW_UNQUOTED_FIELD_NAMES / ALLOW_SINGLE_QUOTES}
 *       if it is JS-literal rather than strict JSON - see {@link TemplateMediaParser}). Sturdier than
 *       the DOM because it ignores styling/layout changes.</li>
 *   <li><b>Meta tags</b> ({@code og:title, og:image, product:price:amount, place:location:latitude}).</li>
 *   <li><b>DOM</b> (stable {@code id}/{@code data-*}/semantic tags; give a PRIMARY selector and 1-2
 *       FALLBACKS, never hashed CSS-in-JS class names).</li>
 *   <li><b>Background XHR/JSON</b> loaded lazily (a map call carrying coordinates, an advertiser
 *       call, a "show more" call): captured through {@link #getPhase3AjaxCaptures(String)} and
 *       read with {@code result.getInterceptedResponse(key)}.</li>
 *   <li><b>Free text</b> (title, description, feature bullets): last resort, via
 *       {@code resolveFromText(details, text, "features")} (see below).</li>
 * </ol>
 *
 * <h2>Conventions (same as the reference scraper)</h2>
 * <ul>
 *   <li>Log {@code [PARSING_SPECIFIC]} (WARN/ERROR) when an element this site ALWAYS has is missing
 *       (= the layout probably changed: alert-worthy) and {@code [PARSING_PENDING]} when something
 *       unhandled/unknown shows up (an unmapped subtype, an unexpected number of address parts...):
 *       these markers are grepped to find what to implement next.</li>
 *   <li>One fact per element: parse feature lists item by item, never from concatenated text (that
 *       mis-assigns neighbouring numbers: bathrooms from the kWh value...). Fold each item first
 *       (strip accents, lowercase, collapse spaces) and match with word-aware needles.</li>
 *   <li>Normalise text with {@code StringUtils.stripAccents}; parse numbers with
 *       {@code NumberParsingUtils.strNumberToInt/Double} (they handle locale separators).</li>
 *   <li>Report whether a field is ALWAYS / SOMETIMES / NEVER present on the site, in the README, so
 *       missing values are not mistaken for parser bugs.</li>
 *   <li><b>Reject rather than store.</b> When something important is unresolved, log
 *       {@code [PARSING_PENDING]} and return {@code null} from {@link #parseListingDetails}: the engine
 *       counts the listing as failed and does NOT save it, so it comes back in the next run until the
 *       parser is fixed (stored incomplete data tends to be forgotten). This applies to the mandatory
 *       fields checked in {@link #mandatoryGaps} and, by default, to any other {@code [PARSING_PENDING]}
 *       (an unmapped enum value, an unknown wording of a known field): collect those per parse and reject
 *       at the end. Only explicit, documented exceptions may be stored with a pending log (e.g. an
 *       unknown amenity label kept in {@code othrAmens}). When a requested capture FAILED (coordinates,
 *       phone, email), throw {@code DetailParseRetryException} instead, so the listing is retried.</li>
 *   <li><b>Tags</b> ({@code ScrapingTaggingEnum}, {@code scrapingDetails.addTag(...)}): the pipeline adds
 *       the {@code GENERIC_*} tags itself. When THIS parser fills a value through a site-specific fix or
 *       inference instead of a structured field (subtype from title keywords, weekly price converted to
 *       monthly, operation inferred from text...), add a tag: the matching
 *       {@code GENERIC_RESOLVED_*_FROM_DESC} when resolved from the description, otherwise one of the
 *       reserved {@code SCRAPER_FIXED_*} slots. Document in the README what each slot used means for
 *       the site. Never add enum values (the enum lives in the shared lib).</li>
 * </ul>
 */
@Component
public class TemplatePhase3 extends RealEstateParser {

    private final ScraperConfig scraperConfig;
    private final RealEstateSettings realEstateSettings;
    private final ScraperBasicDetails scraperDetails;

    public TemplatePhase3(ScraperConfig scraperConfig, RealEstateSettings realEstateSettings,
                          ScraperBasicDetails scraperDetails) {
        this.scraperConfig = scraperConfig;
        this.realEstateSettings = realEstateSettings;
        this.scraperDetails = scraperDetails;
    }

    // ==========================================================================================
    // getLanguage / getAnomalyBounds
    // ==========================================================================================

    /**
     * Language of the site's TEXT, which selects the keyword dictionaries and number-word classes
     * used by the description resolvers ("three bedrooms", "con terraza", "mit Balkon"...), and the
     * default area unit system (ENGLISH = imperial/sq ft, the others metric). It must describe the
     * same language as {@code TemplateScraperDetails#getLanguage()} (the {@code ScraperLanguage}).
     * Available: ENGLISH, SPANISH, GERMAN, FRENCH, ITALIAN. Do not add a {@code ParserLanguage}.
     * If the descriptions are another language, say so in {@code RECON.md} and pick one of these
     * only after checking that its keywords do not false-match the sample descriptions.
     *
     * <p>If the site shows descriptions in a language different from its UI (common on multilingual
     * sites: listings written by the agent in a local language), choose the language of the
     * DESCRIPTIONS - that is the text the resolvers read.
     */
    @Override
    public ParserLanguage getLanguage() {
        // TODO(site): ParserLanguage.SPANISH | ENGLISH | GERMAN | FRENCH | ITALIAN
        return ParserLanguage.ENGLISH;
    }

    /**
     * Bounds for the anomaly detector (max price, max price/surface ratio, maximum bedrooms, ...),
     * taken from {@code scraper.anomaly-bounds.*} (see {@code RealEstateSettings}). The defaults are
     * absolute numbers, sized like USD. A site whose currency is a different size scales every money bound here
     * (see {@code docs/dialect.md}) and leaves bedrooms and square-metre bounds alone.
     * Customise per city only by copying the bounds, as the base class Javadoc shows:
     * <pre>
     * AnomalyBounds bounds = realEstateSettings.getAnomalyBounds().copy();
     * if ("Some expensive city".equals(city)) bounds.setMaxRatioBuy(300000);
     * </pre>
     * (the hook for per-city logic is the base class: this method receives no listing).
     */
    @Override
    public AnomalyBounds getAnomalyBounds() {
        return realEstateSettings.getAnomalyBounds();
    }

    /**
     * Fallback only. A running scraper converts with {@code UsdRateClient} (USD per 1 unit of the site
     * currency), because the framework's stored rate is an EUR quote and stays {@code 1.0} for an EUR site.
     * Keep this override so a parser constructed without the client still reads {@code scraperConfig}.
     * The parser sets {@code basic.price} to the amount printed on the site and leaves {@code originalPrice}
     * null. For a non-USD currency, {@code postProcess} copies that amount into {@code originalPrice} and
     * stores the USD amount in {@code price}.
     */
    @Override
    protected double getExchangeRate() {
        return scraperConfig != null ? scraperConfig.getExchangeRate() : 1.0;
    }

    // ==========================================================================================
    // Browser mechanics: what the browser must do on a detail page BEFORE the HTML is captured
    // ==========================================================================================

    /**
     * Background responses (AJAX/XHR/fetch) to CAPTURE from a detail page, in execution order
     * (browser modes {@code BROWSER} / {@code DELEGATED} only; HTTP mode ignores them). Each
     * {@link AjaxCapture} says WHICH request (a URL substring, also the key you read it back with) and
     * HOW to make the page fire it (an ordered fallback chain of triggers). Read the bodies in the
     * parser with {@code result.getInterceptedResponse(KEY)}, using a constant for the key.
     * Default (kept): none.
     *
     * <p><b>WHEN YOU NEED IT</b>: data that is NOT in the initial HTML but in a background call. The
     * classic: the map/coordinates (a "static map URL" or "location" endpoint fired lazily when the
     * map scrolls into view). Also: the advertiser's phone or email behind a "show phone/email"
     * button (see {@link #parseContactInfo}), price history, full gallery lists, energy certificate.
     *
     * <p><b>HOW TO FIND IT</b>: open a detail page with DevTools > Network (filter Fetch/XHR), clear,
     * reload, scroll through the page like a user, and note every call whose response carries a field
     * we need (search the responses for a known latitude, the agent name...). Choose a SHORT but
     * UNIQUE substring of the URL path as the key (no ids, no tokens, no query string values that
     * change), e.g. {@code "/ajax/detailController/staticMapUrl.ajax"}. Record in a comment: what
     * triggers it (page load / scroll / click), what the response looks like, and how to parse it.
     * Check that it also fires for listings with a hidden/approximate address (some sites do not load
     * it at all then).
     *
     * <p><b>TRIGGERS</b> (how the page is made to fire the request; the next one runs only if the
     * previous did not yield a usable body):
     * <ul>
     *   <li>{@code scrollTo(selectors...)}: a real mouse-wheel scroll until one selector is in the
     *       viewport. For IntersectionObserver-gated lazy data (map, carousel, contact panel). Prefer
     *       the element that holds the lazy content (the map placeholder, NOT the image gallery that is
     *       already loaded), list one or two ALTERNATIVES for robustness across layouts. Programmatic
     *       {@code window.scrollBy} often does NOT trigger IntersectionObserver in occluded windows;
     *       that is why the scraper config keeps {@code anti-throttle-flags: true}.</li>
     *   <li>{@code fetch(absoluteUrl)}: one quiet same-origin GET from the page (cookies included).
     *       PREFERRED when the endpoint is known: build the absolute URL from {@code pageUrl}
     *       (relative URLs were seen returning captcha interstitials).</li>
     *   <li>{@code click(selectors...)}: a human-cursor click, e.g. a "show phone" button. FALLBACK
     *       ONLY: it is a detection surface.</li>
     * </ul>
     * No triggers ({@code AjaxCapture.passive(key)}) means the page fires the request by itself and the
     * engine only listens.
     *
     * <p><b>RULES</b>: captures run in list order (put the mandatory ones first); a challenge/captcha
     * response stops all remaining captures; every trigger costs time and is a (small) detection
     * surface, so add only what is needed for mandatory fields (coordinates, ids) and list the
     * NICE-TO-HAVE ones last. {@code usableWhen(regex)} declares what a REAL body looks like (so an error
     * or empty body is not accepted); the real parsing stays in this class.
     *
     * <p>Example: map via scroll, phone via a quiet fetch then a click fallback.
     * <pre>{@code
     * List<AjaxCapture> captures = new ArrayList<>();
     * captures.add(AjaxCapture.of(MAP_KEY, AjaxCapture.scrollTo("#map-placeholder", "#map")));
     * captures.add(AjaxCapture.of(PHONE_KEY,
     *                 AjaxCapture.fetch("https://www.example.com/ajax/phone/" + id),
     *                 AjaxCapture.click("button.show-phone"))
     *         .usableWhen("\\d{6,}"));
     * return captures;
     * }</pre>
     *
     * @param pageUrl the detail page URL (lets you build per-listing FETCH URLs)
     */
    @Override
    public List<AjaxCapture> getPhase3AjaxCaptures(String pageUrl) {
        // TODO(site) (optional): see the Javadoc for the trigger types and an example.
        return List.of();
    }

    // ==========================================================================================
    // wasDelisted
    // ==========================================================================================

    /**
     * Decides, from the page captured right after navigation (BEFORE the AJAX captures), whether the
     * listing is REMOVED from the site (sold, rented, expired, withdrawn). Workers skip the AJAX captures and
     * Phase 3 stores the listing as DELETED instead of parsing it.
     *
     * <p>The base implementation already returns {@code true} for HTTP 404 and 410. Keep calling it
     * ({@code super.wasDelisted(...)}) and ADD the site's own signals. Most sites do NOT return 404 for a
     * removed listing: they return HTTP 200 with a message, redirect to the search results / the
     * homepage / a "similar properties" page, or return a soft 404. A missed delisting = a dead listing
     * stays active forever; a FALSE delisting (a block/challenge page read as "removed") deletes live
     * listings. Both are costly, so be precise.
     *
     * <p><b>HOW TO RESOLVE</b> (blueprint "Detail page": delisted detection):
     * <ol>
     *   <li>Open a FAKE id (a plausible but non-existent id) and, if you can find one, a GENUINELY
     *       expired listing (old links from search engines, archived URLs, a listing you saw one run
     *       and was gone the next). Record for each: HTTP status, FINAL URL after redirects, and the
     *       exact text/markers in the HTML.</li>
     *   <li><b>Status codes</b>: besides 404/410, some sites use 301/302 (redirect away) or even 200.</li>
     *   <li><b>Redirect away</b> ("left detail URL"): the requested URL contains the listing id but the
     *       FINAL URL ({@code pageUrl}) no longer contains it AND is still on the site (not on a
     *       challenge/CAPTCHA page: exclude URLs containing challenge markers, e.g. "captcha",
     *       "challenge"). This is the
     *       most reliable signal when the site redirects; the reference does it.</li>
     *   <li><b>Text markers</b> in the lower-cased HTML, in the SITE'S LANGUAGE and in English
     *       ("no longer available", "listing has been removed", "ya no está disponible",
     *       "nicht mehr verfügbar", "n'est plus disponible", "non è più disponibile"...), preferably a
     *       stable id/class of the error component ({@code id="error-page"}, {@code class="expired"})
     *       rather than prose. Beware: the same words can appear in a footer, in cookie-banner copy, in
     *       the search page or in "similar listings" blocks: anchor the check (specific element, or
     *       only when the main listing container is ABSENT).</li>
     *   <li><b>Structural signal</b>: absence of the main listing container/price/JSON state on a
     *       page that is not a block page can also mean "removed". Use together with another signal.</li>
     *   <li>NEVER treat a challenge / CAPTCHA / consent-wall / network error page as delisted: those are
     *       retried upstream (they are blocked pages, not removed listings).</li>
     * </ol>
     *
     * <p>Mirror the same rules in the declarative {@code DelistedRules} of
     * {@code TemplateSiteBrowserProfile} (used when {@code scraper.mode} is DELEGATED, where the delegator cannot run this
     * Java method) and keep both in sync; put a unit test next to this class with saved HTML of a
     * live listing, a removed one and a block page.
     *
     * @param html         the captured page HTML (may be null/blank)
     * @param httpStatus   HTTP status of the main document
     * @param pageUrl      final URL after redirects
     * @param requestedUrl the URL that was requested
     */
    @Override
    public boolean wasDelisted(String html, int httpStatus, String pageUrl, String requestedUrl) {
        if (super.wasDelisted(html, httpStatus, pageUrl, requestedUrl)) {
            return true; // 404 / 410
        }
        // TODO(site): add redirect-away check (requested id missing in pageUrl, still on site, not a challenge page)
        // TODO(site): add text/element markers of the site's "listing removed" page
        return false;
    }

    // ==========================================================================================
    // enableSplitGrouping
    // ==========================================================================================

    /**
     * Whether Phase 3 should hand detail work to workers GROUPED by split (all listings of one split
     * to the same worker, one split after another) instead of interleaved. Default (kept): false.
     *
     * <p>Enable it only if the site benefits from locality: e.g. a worker that just crawled
     * a split keeps a believable navigation history/referrer, or per-split cookies matter for the
     * anti-bot. Decide from live observation (do detail fetches from "cold" sessions get blocked
     * more often than fetches that follow a search page?). It reduces parallelism across splits.
     */
    @Override
    public boolean enableSplitGrouping() {
        return false;
    }

    // ==========================================================================================
    // parseListingDetails
    // ==========================================================================================

    /**
     * Parses ONE detail page. The skeleton below is the same flow as the reference scraper: create the
     * model, fill each block with a dedicated method, return it. Keep that shape (one small method per
     * block makes site layouts changes cheap to fix).
     *
     * <p>Return {@code null} (after logging {@code [PARSING_PENDING]} with the reason) when the page is not
     * a listing, when a mandatory field is unresolved ({@link #mandatoryGaps}), or when any other pending
     * issue was collected during the parse (see "Reject rather than store" in the class Javadoc): the
     * listing is then not saved and comes back in the next run. Throw {@code DetailParseRetryException}
     * instead when a requested CAPTURE failed (coordinates, phone, email), so the engine retries it.
     *
     * <p>Inputs: {@code result.getContent()} = rendered HTML; {@code result.getWork().getUrl()} = the
     * requested URL; {@code result.getFinalUrl()} = URL after redirects (use it for the stored
     * external URL); {@code result.getWork().getProperty("listingId")} = the PREFIXED listing id (set by
     * Phase 3 from the {@code ListingIdUrl}); {@code result.getInterceptedResponse(key)} = captured XHR.
     */
    @Override
    public RealEstateListing parseListingDetails(WorkResult result) {
        if (result.getContent() == null) return null;

        // listingId is already prefixed with the site alias (set by Phase3Executor from ListingIdUrl)
        String listingId = result.getWork() != null
                ? (String) result.getWork().getProperty("listingId")
                : null;

        RealEstateListing details = new RealEstateListing(listingId);
        details.setAnnType(AnnounceTypeEnum.EXTERNAL); // ALWAYS EXTERNAL for scraped listings
        Document doc = Jsoup.parse(result.getContent());
        String url = result.getWork() != null ? result.getWork().getUrl() : "";

        // ==================== Basic ====================
        Basic basic = new Basic();
        basic.setCurrency(scraperDetails.getCurrency());
        parseBasic(doc, url, basic);
        details.setBasic(basic);

        // ==================== Location ====================
        Location location = new Location();
        parseAddress(doc, location);
        parseCoordinates(doc, result, location);
        details.setLocation(location);

        // ==================== Property / Building / Interior / Furnish / Transaction ====================
        Property property = new Property();
        details.setProperty(property);
        Building building = new Building();
        details.setBuilding(building);
        Interior interior = new Interior();
        details.setInterior(interior);
        Furnish furnish = new Furnish();
        details.setFurnish(furnish);
        Transaction transaction = new Transaction();
        details.setTransaction(transaction);

        // Create early so description-resolve tags can attach without replacing ScrapingDetails later.
        ScrapingDetails scrapingDetails = new ScrapingDetails();
        String listingUrl = result.getFinalUrl() != null ? result.getFinalUrl() : url;
        scrapingDetails.setExtListingUrl(listingUrl);
        scrapingDetails.setSiteId(scraperDetails.getSiteAlias());
        // The SITE's own listing id, raw (without the site-alias prefix). Not the advertiser's own
        // reference: that one goes to contact.extRefId (see parseExternalReference).
        String alias = scraperDetails.getSiteAlias();
        if (listingId != null && alias != null && listingId.startsWith(alias)) {
            scrapingDetails.setExternalId(listingId.substring(alias.length()));
        }
        details.setScrapingDetails(scrapingDetails);

        parseFeatures(doc, details);

        // ==================== Contact / Agency ====================
        ContactExternal contact = new ContactExternal();
        parseContactInfo(doc, url, contact);
        details.setContactExternal(contact);

        // ==================== FlatMate (SHARE operation only) ====================
        if (basic.getOperation() == Basic.OperationEnum.SHARE) {
            FlatMate flatMate = new FlatMate();
            parseShareDetails(doc, flatMate, transaction, details);
            details.setFlatMate(flatMate);
        }

        // ==================== Media (gallery / floorplan / video / virtual tour) ====================
        details.setMedia(TemplateMediaParser.parse(result.getContent()));

        // ==================== Display ====================
        Display display = new Display();
        parseDisplay(doc, display);
        details.setDisplay(display);

        // ==================== Description ====================
        parseDescription(doc, details);

        // ==================== External reference ====================
        String extRef = parseExternalReference(doc);
        if (extRef != null) contact.setExtRefId(extRef);

        // ==================== Dates ====================
        parseDates(doc, details);

        // ==================== Mandatory fields: reject rather than store ====================
        List<String> gaps = mandatoryGaps(details);
        if (!gaps.isEmpty()) {
            log.warn("[PARSING_PENDING] Listing rejected, not stored (it comes back next run): {} missing {} - {}",
                    listingId, gaps, url);
            return null;
        }

        return details;
    }

    /**
     * The fields every stored listing must have; returns what is missing (empty = OK). A listing with a
     * gap is rejected by {@link #parseListingDetails} (not saved), so it is reviewed and fixed.
     *
     * <p>Always mandatory: operation, property type, property subtype, and a way to contact the
     * advertiser (phone OR email, see {@link #parseContactInfo}). Add the site-specific mandatory fields
     * decided in the recon: coordinates and address when the site always provides them, the price when
     * one is shown, and the pending issues collected while parsing (unmapped values, unknown wordings),
     * unless they are documented exceptions.
     */
    private List<String> mandatoryGaps(RealEstateListing details) {
        List<String> gaps = new ArrayList<>();
        Basic basic = details.getBasic();
        if (basic == null || basic.getOperation() == null) gaps.add("operation");
        if (basic == null || basic.getPropertyType() == null) gaps.add("type");
        if (basic == null || basic.getPropertySubType() == null) gaps.add("subtype");
        ContactExternal contact = details.getContactExternal();
        boolean hasPhone = contact != null && contact.getExtPhone() != null && !contact.getExtPhone().isBlank();
        boolean hasEmail = contact != null && contact.getExtEmail() != null && !contact.getExtEmail().isBlank();
        if (!hasPhone && !hasEmail) gaps.add("contact (neither phone nor email)");
        // TODO(site): add the site-specific mandatory fields and the pending issues collected while parsing.
        return gaps;
    }

    // ==========================================================================================
    // Basic: operation, term, type, subtype, price
    // ==========================================================================================

    /**
     * Fills {@link Basic}: property type, subtype, operation, term and price.
     * (Currency is already set by the caller from {@code ScraperBasicDetails}.)
     *
     * <ul>
     *   <li><b>Operation</b> ({@code basic.setOperation(OperationEnum.BUY | RENT | SHARE)}): MANDATORY.
     *       Best source: the URL token / JSON field of the listing itself ({@code "rent"/"sale"} in
     *       state JSON, a breadcrumb, the canonical URL path), cross-checked with the page title.
     *       Avoid inferring it from words in the whole body (mortgage calculators and "similar listings"
     *       contain "per month" even on sale pages) and avoid defaulting silently: log
     *       {@code [PARSING_PENDING]} when it is ambiguous. Room-share listings ("a room in a shared
     *       flat") are {@code SHARE}: they have their own page sections, and they trigger
     *       {@link #parseShareDetails}.</li>
     *   <li><b>Term</b> ({@code basic.setTopTerm(TermEnum.LONG_TERM | MID_TERM | SHORT_TERM | LEASE)}):
     *       rent duration category. Many sites expose it (holiday vs long let; "lease" for commercial).
     *       Leave null if the site does not distinguish; the pipeline can derive the top term from the
     *       term prices.</li>
     *   <li><b>Type</b> and <b>subtype</b> are MANDATORY (a listing without them is rejected, see
     *       {@link #mandatoryGaps}). Build a mapping table of every site value observed in the recon.
     *       <b>Type</b> ({@code basic.setPropertyType(TypeEnum.RESIDENTIAL | COMMERCIAL)}) and
     *       <b>subtype</b> ({@code basic.setPropertySubType(SubTypeEnum.RES_APARTMENT,
     *       RES_DETACHEDHOUSE_VILLA, RES_ATTACH_SEMIHOUSE, RES_LOFT, RES_DUPLEX, RES_BUILDINGCASTLE,
     *       COM_OFFICE, COM_RETAIL, COM_WAREHOUSE, COM_INDUSTRIAL, COM_HOSPITALITY, COM_HEALTHCARE,
     *       COM_OTHER)}). Prefer a machine-readable category (JSON "propertyType", breadcrumb,
     *       schema.org @type); fall back to keywords in the TITLE (never the whole body), in the site
     *       language, longest/most specific word first (penthouse before apartment; "semi-detached /
     *       terraced / town house" -> RES_ATTACH_SEMIHOUSE; "villa / chalet / farmhouse / cottage" ->
     *       RES_DETACHEDHOUSE_VILLA; "loft / studio" -> RES_LOFT or RES_APARTMENT; "duplex / triplex" ->
     *       RES_DUPLEX; "building / block / castle" -> RES_BUILDINGCASTLE). Penthouse is NOT a subtype:
     *       it is the {@code FloorNumberEnum.FLOORNR_PENTHOUSE} amenity of the property (see
     *       {@link #parseFeatures}). For an unmapped value, log {@code [PARSING_PENDING]} with the raw
     *       value and leave it null (the listing is rejected): never guess a default. When the subtype is
     *       taken from title keywords instead of a structured field, tag it ({@code SCRAPER_FIXED_TYPE_*}).</li>
     *   <li><b>Price</b> ({@code basic.setPrice(Double)}): the CURRENT asking price as a number, in the
     *       site's currency, for the listing's operation (monthly for long rents, total for sales).
     *       Prefer JSON-LD {@code offers.price} / state JSON; else the main price element (primary
     *       selector + fallback). Parse with {@code NumberParsingUtils.strNumberToInt/Double}, which
     *       copes with "1.250,50 €" / "€1,250" / "£1,250 pcm". Pitfalls: ranges ("from X"), "price on
     *       request" / POA (leave {@code basic.price} null — there is no price-qualifier field on
     *       {@link Display}), per-week prices (UK: "pw" - convert to monthly or store as the term price
     *       consistently), price per m2 mistaken for the price, a different currency (a listing priced
     *       in a currency other than the site's cannot be converted with the site rate: log
     *       {@code [PARSING_PENDING]} and reject it), and the mortgage-calculator numbers.
     *       <p>For non-USD sites, set only this site-currency amount and leave
     *       {@code originalPrice} null: {@code postProcess} copies it to
     *       {@code originalPrice} and converts {@code price} to USD. Do not put a "was / reduced from"
     *       figure into {@code originalPrice} — that is not what the field means, and a price drop has
     *       no model field (note it in the README/blueprint as a gap if the site shows one).</p></li>
     * </ul>
     */
    private void parseBasic(Document doc, String url, Basic basic) {
        // TODO(site): operation, term, property type, subtype, price (see Javadoc).
    }

    // ==========================================================================================
    // Location: address + coordinates
    // ==========================================================================================

    /**
     * Fills the textual part of {@link Location}.
     *
     * <ul>
     *   <li>{@code location.setCountry(...)}: constant for the site (display name of the country).</li>
     *   <li>{@code setProvince}, {@code setCity}, {@code setAddress}, {@code setPostcode}: map the site's
     *       address breadcrumb/structured address (JSON-LD {@code address.addressRegion /
     *       addressLocality / streetAddress / postalCode}) onto our levels. Sites vary a lot: the number
     *       of address parts depends on how precisely the owner exposes the address, so DO NOT assume a
     *       fixed position; resolve by the number of parts and log {@code [PARSING_PENDING]} for a count
     *       you have not seen. The hierarchy is usually
     *       {@code [street, neighbourhood/district, city, province/county]}, but a short list drops the
     *       street first. City = the municipality (the Phase 0 assertion checks it); province/county/state
     *       = the next administrative level above.</li>
     *   <li>{@code location.setPrecisionLvl(PrecisionLevelEnum.X)}: HOW exact the published location is:
     *       {@code EXACT_PRECISE} (exact point, street + number shown), {@code STREET_PRECISE},
     *       {@code POSTCODE_PRECISE}, {@code DISTRICT_PRECISE}, {@code CITY_PRECISE},
     *       {@code PROVINCE_PRECISE}, {@code NOT_PRECISE}. Many sites deliberately hide the exact address
     *       (an approximate pin, a circle, "address not shown"): detect that marker (a flag in the JSON,
     *       a "approximate location" notice, a map shown as an area) and degrade the precision
     *       accordingly. Wrong precision makes downstream geocoding/duplicate detection trust a fake
     *       point.</li>
     *   <li>{@code setHideStNr(true)} when the street number is hidden.</li>
     *   <li>{@code setIsPrecise(boolean)}: SET IT HERE, with a rule RELATIVE TO THE SITE. When it is
     *       {@code false}, the later POI enrichment may skip the listing. Measure the distribution of
     *       precision levels over many listings in the recon: if the site OFTEN gives street-number or
     *       street precision, only EXACT/STREET are precise (POSTCODE or worse = false); if the site never
     *       or rarely does better than postcode, POSTCODE is good enough to be true. Implement the rule in
     *       one place and document it with the numbers. ({@code setWasGeocoded} is set by the pipeline.)</li>
     * </ul>
     */
    private void parseAddress(Document doc, Location location) {
        // TODO(site): country, province, city, address, postcode, precision (see Javadoc).
    }

    /**
     * Fills {@code location.setCoordinates(lat, lon)} (WGS84 decimal degrees). Coordinates are the most
     * valuable and the most fragile field of a listing; treat them as MANDATORY if the site always
     * publishes them, and make a missing value LOUD (see the retry note at the end).
     *
     * <p><b>Sources, best first</b> (try them as a chain, stop at the first that yields a valid pair):
     * <ol>
     *   <li>JSON-LD {@code geo.latitude / geo.longitude} or embedded state JSON
     *       ({@code "latitude"/"longitude"}, {@code "lat"/"lng"|"lon"}, {@code coordinates:[lon,lat]} -
     *       NOTE GeoJSON order is [lon, lat]).</li>
     *   <li>An INTERCEPTED background response ({@code result.getInterceptedResponse(key)}): a "map" /
     *       "location" endpoint returning JSON or a static-map URL (Google/OSM/Mapbox) with
     *       {@code center=lat,lon}, {@code markers=lat,lon} (maybe URL-encoded {@code %2C}). Needs
     *       {@link #getPhase3AjaxCaptures(String)} (often a SCROLL_TO trigger).</li>
     *   <li>Data attributes on the map element ({@code data-lat/data-lng}, {@code data-latitude/...}).</li>
     *   <li>The static-map {@code <img src>} or an inline {@code style} background-image URL.</li>
     *   <li>A static-map URL anywhere in the raw HTML (regex on {@code result.getContent()}).</li>
     *   <li>Geocoding the address (last resort, not done here: the pipeline marks
     *       {@code wasGeocoded} and the precision must reflect it).</li>
     * </ol>
     *
     * <p><b>Validate</b> before setting: latitude in [-90, 90], longitude in [-180, 180], and (cheap
     * sanity) inside the country's bounding box; reject {@code 0,0}; watch for swapped lat/lon and for
     * the map's DEFAULT centre (the site's own city-centre fallback when the real position is hidden:
     * identical coordinates across many listings = suspicious, precision should be CITY/DISTRICT).
     *
     * <p><b>Missing coordinates policy</b> (reference): if the page was fetched WITH the coordinates
     * capture ({@code work.hasAjaxCapture(MAP_KEY)}) and no coordinates were
     * found, the capture failed (blocked XHR, scroll did not trigger the observer): log
     * {@code [PARSING_SPECIFIC]} at ERROR and throw
     * {@code new com.scraper.flavor.cms.phase.DetailParseRetryException("Missing coordinates ...")}
     * so the engine retries the listing instead of storing it without coordinates. If the capture was NOT
     * requested (fast test fetches), only warn.
     */
    private void parseCoordinates(Document doc, WorkResult result, Location location) {
        // TODO(site): coordinates chain + validation + retry policy (see Javadoc).
    }

    // ==========================================================================================
    // Features: bedrooms, bathrooms, surface, floor, orientation, energy, amenities, building, ...
    // ==========================================================================================

    /**
     * Fills {@link Property}, {@link Building}, {@link Interior}, {@link Furnish} and {@link Transaction}
     * from the page's facts: the headline facts (beds/baths/size), the "features" / "characteristics"
     * lists and the "key details" tables.
     *
     * <h3>Approach (the one that avoids wrong values)</h3>
     * <ol>
     *   <li>Prefer STRUCTURED sources (state JSON / JSON-LD fields like {@code numberOfBedrooms},
     *       {@code floorSize}, {@code yearBuilt}) - map each directly, no text matching needed.</li>
     *   <li>For LIST/TABLE features: select every feature item element, then handle EACH item
     *       independently ("one li = one fact"): fold it ({@code StringUtils.stripAccents} + lowercase +
     *       collapse spaces; normalise ordinal marks), apply every field matcher to that single item, and
     *       only then run the generic keyword resolver on the item:
     *       {@code resolveFromText(details, rawItemText, "features")} (inherited; fills amenities /
     *       furnished / pool / parking / A-C / heating etc. from the language dictionaries). Skip the
     *       generic resolver for items that other matchers own (energy, land, negations like
     *       "no lift"/"sin ascensor").</li>
     *   <li>After the per-item pass, run once the resolver over ALL items joined by a period
     *       ({@code String.join(".", itemTexts)}) so the resolvers treat each as its own context.</li>
     *   <li>Log {@code [PARSING_SPECIFIC]} (WARN) when the features container is not found at all.</li>
     * </ol>
     *
     * <h3>Field-by-field guide</h3>
     * <ul>
     *   <li><b>Property</b>:
     *     <ul>
     *       <li>{@code setBedrooms(Integer)}: bedrooms. "Studio" = 0. A "rooms" count (living room
     *           included, common in Europe) is NOT bedrooms: it goes to {@code setRoomsTotal}.</li>
     *       <li>{@code setBathrooms(Integer)}: bathrooms + toilets/aseos counted as the site counts them;
     *           be explicit in a comment about what the site includes.</li>
     *       <li>{@code setSurface(Integer)}: m2 of the property. Sites show several: BUILT/gross vs
     *           USABLE/net vs plot. Decide ONE rule (the reference: built surface; "usable" is used when
     *           it is the only one) and document it. Convert sq ft to m2 if the site is imperial
     *           ({@code * 0.0929}) or keep the site's unit consistently - never mix. Plot/land size goes to
     *           {@code building.setLandSz} (+ {@code setLandSzM("m2"|"hectare")}).</li>
     *       <li>{@code setFloorNr(Integer)} and the floor category through
     *           {@code setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum.FLOORNR_BASEMENT | _GROUND | _LOWER |
     *           _MIDDLE | _HIGH | _PENTHOUSE)}: floors are written in many ways ("3rd floor", "floor 3",
     *           "ground floor", "mezzanine", "semi-basement", "top floor", "penthouse"): handle
     *           ordinals, the local word for ground/basement, and "floor X of Y" (Y = building height).
     *           Not applicable to houses.</li>
     *       <li>{@code setOrientation(OrientationEnum.NORTH|SOUTH|EAST|WEST|NORTH_EAST|NORTH_WEST|SOUTH_EAST|
     *           SOUTH_WEST)}: accept the local direction words and combinations.</li>
     *       <li>{@code setPropertyStatus(PropertyStatusEnum.NEW_BUILD|RENOVATED|GOOD_CONDITION|
     *           NEEDS_RENOVATION|UNFINISHED)}: "new development", "to renovate", "good condition", "off plan".</li>
     *       <li>{@code setView(ViewEnum.GARDEN_VIEW|CITY_VIEW|SEA_VIEW|MOUNTAIN_VIEW|PARK_VIEW|STREET_VIEW|
     *           COURTYARD_VIEW|MONUMENT_VIEW)}.</li>
     *       <li>Energy: {@code setEnergyConsumption(EnergyConsumptionEnum.A..G)} for the certificate
     *           LETTER (a graphic: often a CSS class like {@code energy-a} / an {@code alt} text / an
     *           attribute / state JSON {@code epcRating}); {@code setEnergyScore(Integer)} for the
     *           numeric kWh/m2*year value. "Exempt / in process / not available" = leave null.
     *           Consumption and emissions are two scales: map only the consumption one.</li>
     *       <li>{@code addPropertyAmenity(PropertyAmenitiesEnum.X)}: BALCONY_TERRACE, FIREPLACE, ALARM,
     *           DOUBLE_GLAZING, CELLAR, ATTIC, UTILITY_ROOM, INTERPHONE, VAULTED_CEILING, CROWN_MOLDING,
     *           BAY_WINDOW, SKYLIGHT, UNDERFLOOR_HEATING; and {@code setBalconySz(Double)} when the size is
     *           given; {@code setPropertyLevels(Integer)} for multi-level homes; {@code setFloorType(...)}.</li>
     *     </ul></li>
     *   <li><b>Building</b>:
     *     <ul>
     *       <li>{@code setBuildingAge(Integer)} = YEAR BUILT (e.g. 1998); {@code setBuildingAgeAsNewConstruction()}
     *           for new builds; {@code setHeight(Integer)} = floors of the building;
     *           {@code setLandSz(Integer)}/{@code setLandSzM(String)}; {@code setHouseStyle},
     *           {@code setConstMaterial}, {@code setRoof} if exposed.</li>
     *       <li>{@code addBuildingFacility(BuildingFacilitiesEnum.ELEVATOR | CONCIERGE | GYM | POOL_PRIVATE |
     *           POOL_SHARED | GARDEN_PRIVATE | GARDEN_SHARED | PARKING | BIKE_STORAGE | DISABLED_ACCESS | SECURITY |
     *           COMMUNAL_AREAS | EVENT_ROOM | CAVE)}: distinguish PRIVATE vs SHARED pool/garden. If the feature
     *           list only says "swimming pool", decide from the description (the pipeline's resolvers
     *           do this when the field is left unset), not by guess. {@code setParkingCount(int)},
     *           {@code addParkingData(...)}, {@code setPoolSz}, {@code setCaveSz}.</li>
     *     </ul></li>
     *   <li><b>Interior</b>: {@code setBdrmType(BedroomTypeEnum.PRIVATE|SHARED|LIVING_ROOM)} (mostly for room
     *       shares), {@code setKitchenType(KitchenTypeEnum.FITTED|OPEN_PLAN|SEPARATE|GALLEY)},
     *       {@code addShowerBath}, {@code addBathFacility}, {@code setBthrmType}, sizes
     *       ({@code setBdrmSz, setKtchnSz, setBthrmSz}).</li>
     *   <li><b>Furnish</b>: {@code setFurnishType(FurnishingTypeEnum.FURNISHED|UNFURNISHED|SEMI_FURNISHED|BARE|
     *       UNDEFINED)} (rentals: unfurnished/furnished/part-furnished are always stated),
     *       {@code addBasicAppliance(BasicAppliancesEnum.WASHING_MACHINE|DRYER|DISHWASHER|REFRIGERATOR|FREEZER|
     *       OVEN|MICROWAVE|STOVE|HEATERS)}, {@code addFurnishAmenity(FurnishAmenitiesEnum.WASHING_MACHINE|
     *       INTERNET|AIR_CONDITIONER|DISHWASHER|FREEZER|OVEN|SOFA|CURTAINS|WARDROBE|BEDS|DESK)},
     *       {@code setHeatingSystem(HeatingSystemEnum.CENTRAL|COLLECTIVE|GAS|ELECTRIC|OIL|UNDERFLOOR|HEAT_PUMP)}.</li>
     *   <li><b>Transaction</b> (mostly rentals):
     *     <ul>
     *       <li>{@code setSecurityDeposit(SecurityDepositEnum.NONE|ONE_MONTH|TWO_MONTHS|THREE_MONTHS|
     *           SPECIFIC_AMOUNT)} + {@code setSecurityDepositValue(Integer)} when an amount is given.</li>
     *       <li>{@code setCommCharges(Double)}: community / service / maintenance charges (state period).</li>
     *       <li>{@code setAvailable(String)} / {@code setAvailableC(AvailableChoiceEnum.NOW|FROM)} for the
     *           availability date (ISO format {@code yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}). Minimum/maximum stay has NO
     *           field in the model yet: if the site publishes it, note it in the README/blueprint as a model gap
     *           instead of forcing it into another field;</li>
     *       <li>{@code addPolicy(PoliciesEnum.PETS_ALLOWED|SMOKING_ALLOWED|STUDENTS_ALLOWED|DSS_ALLOWED|
     *           FAMILIES_ALLOWED|UTIL_COSTS_INCLUDED)}: ONLY when the page states it positively. "No pets"
     *           means do NOT add PETS_ALLOWED (absence of a policy is not a prohibition flag in the model);
     *           {@code addRequisite(RequisitesEnum.GUARANTOR|PAYSLIPS|REFERENCES|ID_PROOF|CREDIT_CHECK|SEASONAL)};</li>
     *       <li>Term prices {@code setlTerm / setmTerm / setsTerm / setLease} for rentals priced per period
     *           (UK weekly "pw" -> monthly; short-let nightly rates -> short term); {@code setOwnership(OwnershipEnum.
     *           FULL_OWNERSHIP|FREEHOLD|LEASEHOLD|SHARE_FREEHOLD|SHARE_OWNERSHIP|COMMONHOLD)} for sales (UK tenure);
     *           agency fees via {@code setGeneralAgencyFeesFromValue / FromPercentage}; {@code setIsNgtbl}.</li>
     *     </ul></li>
     * </ul>
     *
     * <p><b>Rules</b>: set a field only when the page says it positively; never default a missing value to
     * false/0 (a null stays "unknown" for the resolvers and for the data consumers). Log
     * {@code [PARSING_PENDING]} for feature labels nobody handles yet - that log is how the matcher list
     * grows. Penthouse: take it from the title and the floor category AFTER all features are parsed.
     */
    private void parseFeatures(Document doc, RealEstateListing details) {
        // TODO(site): select feature items and apply the matchers above (see Javadoc).
        // Property property = details.getProperty();
        // Building building = details.getBuilding();
        // Interior interior = details.getInterior();
        // Furnish furnish = details.getFurnish();
        // Transaction transaction = details.getTransaction();
    }

    // ==========================================================================================
    // Contact / agency
    // ==========================================================================================

    /**
     * Fills {@link ContactExternal}: who advertises the listing.
     *
     * <ul>
     *   <li>{@code setUsrType(UserTypeEnum.PRIVATE | AGENCY | DEVELOPER)}: DEVELOPER = new-build promoter /
     *       builder. Signals: a professional/agency block with a name + logo + profile link = AGENCY;
     *       labels like "private seller / particular / owner / Privatanbieter / Provisionsfrei von privat" =
     *       PRIVATE; "developer / promotor / Bauträger" = DEVELOPER; a flag in the state JSON
     *       ({@code advertiserType: "agency"}) beats any text. Do not default silently to PRIVATE when
     *       the block is simply missing (log {@code [PARSING_PENDING]}; a hidden-advertiser listing is a
     *       different case from a private one).</li>
     *   <li>{@code setAgencyName}, {@code setAgencyLogo} (absolute https URL; handle protocol-relative
     *       {@code //cdn...}), {@code setAgencyUrl} (absolute: prefix {@code scraperDetails.getBaseDomain()} for
     *       relative links; the agency's profile page on the target site, not its own website).</li>
     *   <li>{@code setContactName}: the person's name, mainly for private advertisers.</li>
     *   <li>{@code setExtPhone} / {@code setExtEmail}: <b>MANDATORY, at least one of the two</b>. A listing
     *       with neither is rejected (see {@link #mandatoryGaps}). Analyse in the recon, separately for
     *       private and agency listings, WHERE and HOW each is obtained:
     *       <ul>
     *         <li>directly in the HTML / embedded JSON ({@code tel:} / {@code mailto:} links, JSON-LD
     *             {@code telephone} / {@code email}; watch for masked numbers and obfuscated emails);</li>
     *         <li>behind a "show phone / show email" button: record the request it fires (URL, method,
     *             cookies/tokens, response). Capture it with {@link #getPhase3AjaxCaptures}: a quiet
     *             {@code fetch} of the endpoint when its URL can be built from the listing id (preferred),
     *             a {@code click} only as a fallback; declare {@code usableWhen(regex)}. Browser modes
     *             only: in HTTP mode such a contact cannot be obtained;</li>
     *         <li>on the agency's profile page instead of the listing;</li>
     *         <li>only through a contact form / in-site messaging (never submitted), or only after a
     *             login (never done): a site-level finding to report (logged-in sessions are an engine
     *             gap), not something to work around.</li>
     *       </ul>
     *       Note the quality (direct number, call-tracking number, site relay email). Normalise the phone
     *       to one documented format and the email to lower case. If a requested capture failed (blocked
     *       or unusable body) throw {@code DetailParseRetryException}; if the listing genuinely offers no
     *       phone and no email, log {@code [PARSING_PENDING]} with the reason and leave both null (the
     *       listing is rejected). State in the README/blueprint the site-level verdict: OK (almost all
     *       listings have a contact), PARTIAL (e.g. agencies only, with the share that will be rejected)
     *       or BLOCKER (login-only / form-only: almost every listing would be rejected).</li>
     *   <li>{@code setExtRefId}: set in {@link #parseExternalReference}.</li>
     * </ul>
     * Remember the URL pattern shortcut some sites offer: advertiser pages under {@code /pro/},
     * {@code /agent/}, {@code /agency/} paths imply AGENCY.
     */
    private void parseContactInfo(Document doc, String url, ContactExternal contact) {
        // TODO(site): advertiser type, agency name/logo/url, contact name (see Javadoc).
    }

    // ==========================================================================================
    // SHARE (room in a shared flat) specifics
    // ==========================================================================================

    /**
     * Only called when the operation is {@code SHARE} (room/flat-share listings). Skip entirely (empty
     * method) for sites that have no room-share category.
     *
     * <p>Fills {@link FlatMate} (the people already living there / the wanted flatmate profile) and the
     * share-related {@link Transaction} fields. Sources: a dedicated "flatmates" / "house rules" /
     * "who we're looking for" section of the page, usually a key/value list.
     * <ul>
     *   <li>{@code flatMate.setResidentAmount(Integer)}: current residents; {@code setFlatMatesAvgAge(Integer)}.</li>
     *   <li>{@code flatMate.addFlatmateExtra(FlatmateExtraEnum.COUPLE|HAVE_KIDS|LIVE_WITH_LANDLORD|HAVE_PETS|
     *       LANDLORD)}: LANDLORD = the owner lives there (live-in landlord).</li>
     *   <li>{@code setFlatmatesGender(AllowedGenderEnum.ACCEPT_MALE|ACCEPT_FEMALE|ACCEPT_COUPLE)}: only
     *       when restricted ("females only"); no restriction = leave unset.
     *       {@code setOccupation(AllowedOccupationEnum.ACCEPT_PROFESSIONAL|ACCEPT_STUDENT|ACCEPT_TRAVELLER|
     *       ACCEPT_RETIREE)}.</li>
     *   <li>Lifestyle integers ({@code setSmoking, setDrinking, setDrugUse, setGuest, setSharing, setEating,
     *       setPrivacy, setCleanliness, setDayschedule, setSleepschedule}): only when the site publishes these
     *       preferences; check the meaning of each integer in {@code FlatMate} before mapping.</li>
     *   <li>{@code transaction.setAcceptedAge(from, to)}, {@code setAvailable / setAvailableC},
     *       {@code addPolicy(...)} for pets/smoking (minimum/maximum stay: no model field yet, see above).</li>
     * </ul>
     */
    private void parseShareDetails(Document doc, FlatMate flatMate, Transaction transaction,
                                   RealEstateListing details) {
        // TODO(site): flatmate block (see Javadoc). Leave empty if the site has no room-share category.
    }

    // ==========================================================================================
    // Display (commercial street-front)
    // ==========================================================================================

    /**
     * Fills {@link Display}: commercial street-front / facade attributes when the site publishes them.
     * <ul>
     *   <li>{@code setFacadeExp(FacadeExposureEnum)}: street corner / street front / commercial center / interiors.</li>
     *   <li>{@code setPedestriansEstimate(PedestriansEstimateEnum)}: pedestrian traffic band.</li>
     *   <li>{@code setFacadeSz(Double)}: facade width/size when given.</li>
     *   <li>{@code setStreetPavementType(StreetPavementTypeEnum)}: pedestrian-street type.</li>
     *   <li>{@code setNearbyCommerce(NearbyCommerceEnum)}: dominant nearby commerce character.</li>
     * </ul>
     * Most residential portals leave this empty. {@link Display} is <b>not</b> a place for listing
     * headline, featured/premium badges, price-qualifier (POA / guide / offers over), or hide-price
     * flags — those have no model fields (POA → leave {@code basic.price} null; note the rest as
     * blueprint gaps). Description is not set here (see {@link #parseDescription}).
     */
    private void parseDisplay(Document doc, Display display) {
        // TODO(site): commercial facade / street-front attributes when available (see Javadoc).
    }

    // ==========================================================================================
    // External reference, description, dates
    // ==========================================================================================

    /**
     * The advertiser's OWN reference/ID for the property ("Ref: ABC123", "Property ref"), shown near the
     * bottom of the page. Stored in {@code contact.extRefId}; very useful for
     * duplicate detection across portals and agencies. Return {@code null} when the site shows none. Do NOT
     * return the site's own listing id here: that one is stored, raw, in {@code scrapingDetails.externalId}
     * (set in {@link #parseListingDetails}).
     */
    private String parseExternalReference(Document doc) {
        // TODO(site): reference element/label (see Javadoc).
        return null;
    }

    /**
     * The free-text description, set with {@code details.setDescription(String)}. Take the FULL text of the
     * description container (expand "read more" collapsed text: it is normally in the DOM already, only hidden by
     * CSS; check) as plain text with paragraph breaks preserved if the pipeline benefits. Exclude the
     * boilerplate (legal notices, "contact the agent" call-to-action, similar-listings).
     *
     * <p>Important: the description feeds the pipeline's RESOLVERS ({@code postProcess}: it fills missing
     * amenities, bedrooms, surface, furnished, pool type... from keywords in the site's language) and the
     * anomaly re-check. Therefore: return it in the site's native language, untranslated; do not truncate; if the
     * site shows both an original and a machine-translated description, take the ORIGINAL.
     */
    private void parseDescription(Document doc, RealEstateListing details) {
        // TODO(site): description container + fallbacks (see Javadoc).
    }

    /**
     * Publication / update dates when the page states them: {@code details.setCreated(Instant)} (first published),
     * {@code details.setUpdated(Instant)} (last updated / "Added on" / "Reduced on"), {@code details.setActivAt(Instant)}.
     * Sources: JSON-LD {@code datePosted / dateModified}, state JSON, or a "updated on 21 April" text.
     *
     * <p>Pitfalls: month names in the site's language ({@code DateTimeFormatter} with the proper {@code Locale});
     * RELATIVE dates ("3 days ago", "today", "yesterday": compute from now, then truncate to the day, UTC);
     * dates without a year (assume the current year, but roll back one year if that gives a future date);
     * timezone (store UTC). Unparseable = leave null and log {@code [PARSING_PENDING]} at DEBUG.
     * Many sites show no dates: then leave the method empty and note it in the README.
     */
    private void parseDates(Document doc, RealEstateListing details) {
        // TODO(site): created / updated dates (see Javadoc).
    }
}
