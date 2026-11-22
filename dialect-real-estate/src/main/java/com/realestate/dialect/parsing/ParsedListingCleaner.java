package com.realestate.dialect.parsing;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Site-agnostic cleanup after scraper-specific {@code parseListingDetails}.
 * Runs in {@link ScraperParser#postProcess} before description resolvers.
 * Add further listing-wide sanitization here as needed.
 */
public final class ParsedListingCleaner {

    private static final Logger log = LoggerFactory.getLogger(ParsedListingCleaner.class);

    public void clean(RealEstateListing listing) {
        if (listing == null) return;
        fillCreatedUpdated(listing);
        clearFloorNrOnHouseLike(listing);
    }

    /**
     * Ensure both listing timestamps are present after the site parser returns.
     * If the site only had one of them, copy it; if it had neither, use now.
     */
    private void fillCreatedUpdated(RealEstateListing listing) {
        Instant created = listing.getCreated();
        Instant updated = listing.getUpdated();
        if (created == null && updated != null) {
            listing.setCreated(updated);
        } else if (updated == null && created != null) {
            listing.setUpdated(created);
        } else if (created == null) {
            Instant now = Instant.now();
            listing.setCreated(now);
            listing.setUpdated(now);
        }
    }

    /**
     * {@code floorNr} is an apartment-style field. House-like subtypes should not keep it
     * even if a site parser or feature list mentioned "planta".
     */
    private void clearFloorNrOnHouseLike(RealEstateListing listing) {
        Basic basic = listing.getBasic();
        Property property = listing.getProperty();
        if (basic == null || property == null || property.getFloorNr() == null) return;
        if (!basic.isSubTypeHouseLike()) return;

        log.debug("[PARSE_CLEAN] Cleared floorNr={} on house-like subtype {}",
                property.getFloorNr(), basic.getPropertySubType());
        property.setFloorNr(null);
    }
}
