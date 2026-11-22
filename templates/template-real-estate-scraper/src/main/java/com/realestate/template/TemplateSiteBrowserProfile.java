package com.realestate.template;

import com.scraper.browser.client.SiteBrowserProfileProvider;
import com.scraper.commons.core.phase.ScraperBasicDetails;
import com.scraper.websocket.delegation.BrowserBehaviorProfile;
import com.scraper.websocket.delegation.BrowserBehaviorProfile.Content;
import com.scraper.websocket.delegation.BrowserBehaviorProfile.DelistedRules;
import com.scraper.websocket.delegation.BrowserBehaviorProfile.Modal;
import com.scraper.websocket.delegation.BrowserBehaviorProfile.Pacing;
import com.scraper.websocket.delegation.BrowserBehaviorProfile.Site;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The site's browser behavior AS DATA, for {@code scraper.mode: DELEGATED}.
 * Built once by the scraper, sent to the browser delegator and referenced by hash on every task.
 * It must say the same thing as {@link TemplateBrowserBehavior} (modals, cookie banner, block-page
 * markers), plus the delisted rules the delegator needs because it cannot call the scraper's Java
 * code (e.g. {@code TemplatePhase3#wasDelisted}).
 *
 * <p>{@code site.hostMatch} is REQUIRED: the delegator rejects a profile without it (HTTP 400) and the
 * scraper cannot start its session.
 *
 * <p>The values below are placeholders: replace every {@code TODO(site)}.
 *
 * <p><b>HTTP mode: delete this class</b> (and {@link TemplateBrowserBehavior}, with the tests that
 * depend on them).
 */
@Component
public class TemplateSiteBrowserProfile implements SiteBrowserProfileProvider {

    private final ScraperBasicDetails scraperDetails;

    public TemplateSiteBrowserProfile(ScraperBasicDetails scraperDetails) {
        this.scraperDetails = scraperDetails;
    }

    @Override
    public BrowserBehaviorProfile siteProfile() {

        // ---------------------------------------------------------------------------------------
        // SITE: identity, taken from ScraperBasicDetails to stay in sync.
        //   hostMatch   REQUIRED. Loose substring that recognises "we are on the target site" in a URL,
        //               usually the brand name + dot ("brand."): it must match all the site's
        //               TLD/subdomain variants.
        // ---------------------------------------------------------------------------------------
        Site site = new Site(
                scraperDetails.getSiteAlias(),
                scraperDetails.getBaseDomain(),
                scraperDetails.getCountryCode(),
                scraperDetails.getLanguage().getLocaleCode(),
                scraperDetails.getCurrency(),
                "example.");                            // TODO(site): hostMatch (REQUIRED)

        // ---------------------------------------------------------------------------------------
        // MODALS: one per pop-up, same ids/selectors/close actions as TemplateBrowserBehavior.
        //   new Modal(id, detectSelector, closeClickSelector, closePressKey)
        //   closeClickSelector null => close by pressing closePressKey (e.g. "Escape") and vice versa.
        // ---------------------------------------------------------------------------------------
        List<Modal> modals = List.of(
                // TODO(site): new Modal(TemplateBrowserBehavior.MODAL_COOKIE_CONSENT,
                //         "<detect selector>", "<button selector>", null)
        );
        List<String> cookieSelectors = List.of(
                // TODO(site): same as TemplateBrowserBehavior.getCookieConsentSelectors()
        );
        List<String> blockedPageMarkers = List.of(
                // TODO(site): same as TemplateBrowserBehavior.blockedPageMarkers()
        );

        // ---------------------------------------------------------------------------------------
        // DELISTED RULES: the declarative twin of TemplatePhase3.wasDelisted(...). The delegator uses
        // them only to skip AJAX captures on a removed listing; the scraper still runs its own Java
        // check on the result, so these may be a conservative SUBSET, but must never disagree.
        //   statuses       HTTP statuses meaning removed: 404, 410 (+ the site's own, if any).
        //   idUrlRegex     regex with GROUP 1 = the listing id inside the REQUESTED url
        //                  (e.g. "/listing/(\\d+)"): removed if the final URL lost that id while staying
        //                  on the site.
        //   siteHostMatch  same as site.hostMatch: only count the "lost id" signal when still on-site.
        //   htmlMarkers    lower-case substrings of the removed-listing page.
        // ---------------------------------------------------------------------------------------
        DelistedRules delisted = new DelistedRules(
                List.of(404, 410),
                null,                                   // TODO(site): idUrlRegex
                "example.",                             // TODO(site): same as hostMatch
                List.of());                             // TODO(site): htmlMarkers

        // ---------------------------------------------------------------------------------------
        // PACING: on-page linger range after a fetch (ms). Page/detail delays of 0 mean "use
        // scraper.browser.* from application.yaml". Keep in sync with TemplateBrowserBehavior.
        // ---------------------------------------------------------------------------------------
        Pacing pacing = new Pacing(2000, 4000, 0, 0, 0, 0);

        return new BrowserBehaviorProfile(
                scraperDetails.getSiteAlias(),          // profileId: one profile per scraper
                1,                                      // schemaVersion
                null,                                   // hash: computed by the client
                site,
                null,                                   // navigation: from scraper.browser.*
                pacing, modals, cookieSelectors, blockedPageMarkers,
                null,                                   // recycle: from scraper.browser.page-recycle-after
                new Content(delisted));
    }
}
