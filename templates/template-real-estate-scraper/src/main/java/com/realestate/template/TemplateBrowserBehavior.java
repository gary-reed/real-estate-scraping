package com.realestate.template;

import com.scraper.browser.core.config.BrowserBehaviorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Site-specific BROWSER BEHAVIOR for {@code scraper.mode: BROWSER}: which pop-ups can block the
 * page and how to close them, the cookie banner, and what this site's block page looks like.
 *
 * <p>In {@code scraper.mode: DELEGATED} the same information is sent to the browser delegator as
 * DATA by {@link TemplateSiteBrowserProfile}. BOTH classes must describe the same behavior; keep
 * them in sync (a small unit test comparing the two is a good idea).
 *
 * <p>Everything in this class comes from OBSERVING the live site in a real browser.
 *
 * <p><b>HTTP mode: delete this class</b> (and {@link TemplateSiteBrowserProfile}, with the tests that
 * depend on them).
 */
@Slf4j
@Configuration
public class TemplateBrowserBehavior extends BrowserBehaviorConfig {

    // ------------------------------------------------------------------------------------------
    // Modal IDs: short stable names you choose. They link detection (selector) with closing action,
    // and are the {@code id} of the matching Modal in TemplateSiteBrowserProfile.
    // ------------------------------------------------------------------------------------------
    public static final String MODAL_COOKIE_CONSENT = "cookie-consent";
    // public static final String MODAL_SAVE_SEARCH = "save-search";
    // public static final String MODAL_LOGIN_PROMPT = "login-prompt";

    /**
     * The pop-ups/overlays of the target site that can cover the page, and the CSS selector that is
     * PRESENT AND VISIBLE only while each one is open. Checked in the listed order (put the cookie
     * banner first, it blocks everything else). Default: none.
     *
     * <p><b>HOW TO FIND THEM</b>: browse the site as a first-time visitor (clean profile, no cookies),
     * and note every overlay that appears: on first load (cookie consent, newsletter, "choose your
     * country"), after applying filters ("save this search"), after a few page views (login wall), on a
     * detail page, and on pagination.
     *
     * <p><b>SELECTOR RULES</b>: it must match ONLY while the modal is visible ({@code dialog[open]},
     * {@code [aria-hidden='false']}, {@code .is-open}...); prefer stable {@code id}s and {@code data-*}
     * or ARIA hooks to generated CSS classes. Test it in the console with the modal open and closed.
     */
    @Override
    public List<ModalConfig> getDetectModalsSelectors() {
        return List.of(
                // TODO(site): ModalConfig.of(MODAL_COOKIE_CONSENT, "<selector present only while the banner is visible>")
        );
    }

    /**
     * How to CLOSE the modal with the given id: click a selector, or press a key. Return {@code null}
     * to let the engine use its generic strategies (Escape, click outside).
     *
     * <p>For native {@code <dialog>}s and most overlays {@code ModalCloseAction.pressKey("Escape")}
     * works; otherwise click the close button ({@code [aria-label='Close']}, {@code button.close}).
     * {@code clickOrKey(selector, key)} tries the click and falls back to the key.
     */
    @Override
    public ModalCloseAction closeDetectedModal(String modalId) {
        return switch (modalId) {
            // TODO(site): case MODAL_COOKIE_CONSENT -> ModalCloseAction.clickSelector("<button selector>");
            // TODO(site): case MODAL_SAVE_SEARCH   -> ModalCloseAction.pressKey("Escape");
            default -> null;
        };
    }

    /**
     * The button that closes the cookie banner when the browser lands on the site (tried before the
     * engine's common selectors). Default: none.
     */
    @Override
    public List<String> getCookieConsentSelectors() {
        return List.of(
                // TODO(site): "<cookie banner button selector>"
        );
    }

    /**
     * Lower-case text that only appears on this site's block page ("access to this page has been
     * restricted", the id of the block component...). The engine already recognises the common captcha
     * and "access denied" pages; add markers only when the site shows its own block page.
     */
    @Override
    public List<String> blockedPageMarkers() {
        return List.of(
                // TODO(site): "<text of the site's own block page>"
        );
    }

    // ------------------------------------------------------------------------------------------
    // OPTIONAL overrides (defaults are fine for most sites). Keep commented unless needed.
    // ------------------------------------------------------------------------------------------
    //
    // /** Stay on a page after a successful fetch (default 2000-4000 ms). */
    // @Override public int lingerMinMs() { return 2000; }
    // @Override public int lingerMaxMs() { return 4000; }
}
