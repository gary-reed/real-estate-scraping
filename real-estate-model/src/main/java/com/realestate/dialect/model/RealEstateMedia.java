package com.realestate.dialect.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.scraper.flavor.cms.model.Media;

/**
 * Gallery, optional video, and the two property-only links the framework shell no longer stores:
 * a floor plan and a virtual tour.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RealEstateMedia extends Media {

    private String floorplanUrl;
    private String virtualTourUrl;

    public String getFloorplanUrl() {
        return floorplanUrl;
    }

    public void setFloorplanUrl(String floorplanUrl) {
        this.floorplanUrl = floorplanUrl;
    }

    public String getVirtualTourUrl() {
        return virtualTourUrl;
    }

    public void setVirtualTourUrl(String virtualTourUrl) {
        this.virtualTourUrl = virtualTourUrl;
    }
}
