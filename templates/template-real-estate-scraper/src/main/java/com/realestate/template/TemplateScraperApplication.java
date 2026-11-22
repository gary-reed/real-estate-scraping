package com.realestate.template;

import com.scraper.flavor.cms.EnableCmsFlavor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the TEMPLATE real-estate scraper. Contains no site logic.
 *
 * <p>{@code @EnableCmsFlavor} brings the cms flavor; the real-estate dialect registers itself through its
 * dependency; {@code @SpringBootApplication} scans this package, where the site classes live. When copying,
 * rename the package and the class.</p>
 *
 * <p>The site classes (one bean each): {@code ScraperBasicDetails}, {@code Phase0Validation},
 * {@code Phase1Splitting}, {@code Phase2Crawling}, {@code Phase3DetailParsing}. Plus
 * {@code BrowserBehaviorConfig} for {@code scraper.mode: BROWSER} and {@code SiteBrowserProfileProvider}
 * for {@code scraper.mode: DELEGATED}.</p>
 */
@SpringBootApplication
@EnableCmsFlavor
public class TemplateScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(TemplateScraperApplication.class, args);
    }
}
