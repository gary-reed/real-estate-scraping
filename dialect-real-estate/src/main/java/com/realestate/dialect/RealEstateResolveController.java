package com.realestate.dialect;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scraper.flavor.cms.model.ScrapingDetails;
import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.Furnish;
import com.realestate.dialect.model.Interior;
import com.realestate.dialect.model.Property;
import com.realestate.dialect.model.RealEstateListing;
import com.realestate.dialect.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Runs the real-estate resolvers on a piece of text.
 * GET /api/test/resolve-text?text=... Registered by {@link RealEstateDialectConfiguration}.
 */
@RestController
@RequestMapping("/api/test")
public class RealEstateResolveController {

    private static final Logger log = LoggerFactory.getLogger(RealEstateResolveController.class);

    private final RealEstateParser parser;
    private final ObjectMapper json;

    public RealEstateResolveController(RealEstateParser parser) {
        this.parser = parser;
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @GetMapping("/resolve-text")
    public ResponseEntity<?> resolveText(@RequestParam String text) {
        log.info("[TEST] Resolving real-estate fields from text (length={})", text.length());
        try {
            RealEstateListing listing = new RealEstateListing();
            listing.setBasic(new Basic());
            listing.setProperty(new Property());
            listing.setBuilding(new Building());
            listing.setInterior(new Interior());
            listing.setFurnish(new Furnish());
            listing.setTransaction(new Transaction());
            listing.setScrapingDetails(new ScrapingDetails());
            listing.setDescription(text);
            parser.postProcess(listing);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/json")
                    .body(json.writeValueAsString(listing));
        } catch (Exception e) {
            log.error("[TEST] Error resolving text: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
