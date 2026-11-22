package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Commercial display / street-front characteristics for a listing.
 * Migrated from L_Commons v4 Display.java
 */
public class Display {

    // ============== NESTED ENUMS ==============

    /**
     * Facade exposure relative to the street / commercial context.
     */
    public enum FacadeExposureEnum implements SerializableEnum {
        STREET_CORNER("street_corner", 1),
        STREET_FRONT("street_front", 2),
        COMMERCIAL_CENTER("commercial_center", 3),
        INTERIORS("interiors", 4);

        private static final Map<Integer, FacadeExposureEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        FacadeExposureEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static FacadeExposureEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Pedestrian traffic estimate for the street front.
     */
    public enum PedestriansEstimateEnum implements SerializableEnum {
        VERY_HIGH_TRAFFIC("very_high_traffic", 1),
        HIGH_TRAFFIC("high_traffic", 2),
        MEDIUM_TRAFFIC("medium_traffic", 3),
        LOW_TRAFFIC("low_traffic", 4),
        VERY_LOW_TRAFFIC("very_low_traffic", 5);

        private static final Map<Integer, PedestriansEstimateEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        PedestriansEstimateEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static PedestriansEstimateEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Street / pavement type in front of the property.
     */
    public enum StreetPavementTypeEnum implements SerializableEnum {
        SMALL_PEDESTRIAN_STREET("small_pedestrian_street", 1),
        BIG_PEDESTRIAN_STREET("big_pedestrian_street", 2),
        PURELY_PEDESTRIAN_STREET("purely_pedestrian_street", 3);

        private static final Map<Integer, StreetPavementTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        StreetPavementTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static StreetPavementTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Dominant nearby commerce character.
     */
    public enum NearbyCommerceEnum implements SerializableEnum {
        MIXED_COMM("mixed", 1),
        CLOTH_COMM("cloth", 2),
        FOOD_COMM("food", 3),
        ELECTRONIC_COMM("electronic", 4),
        ENTERTAINMENT_COMM("entertainment", 5),
        MOSTLY_RESIDENTIAL("mostly_residential", 6),
        MOSTLY_INDUSTRIAL("mostly_industrial", 7);

        private static final Map<Integer, NearbyCommerceEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        NearbyCommerceEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static NearbyCommerceEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    // ============== FIELDS ==============

    @SerializedName("facadeExp")
    @Expose
    @JsonIgnore
    private Integer facadeExp; // FacadeExposureEnum

    @SerializedName("pedestrEst")
    @Expose
    @JsonIgnore
    private Integer pedestrEst; // PedestriansEstimateEnum

    @SerializedName("facadeSz")
    @Expose
    private Double facadeSz;

    @SerializedName("strtType")
    @Expose
    @JsonIgnore
    private Integer strtType; // StreetPavementTypeEnum

    @SerializedName("nearbyCom")
    @Expose
    @JsonIgnore
    private Integer nearbyCom; // NearbyCommerceEnum

    @SerializedName("dSvd")
    @Expose
    @JsonIgnore
    private Integer dSvd;

    // ============== FACADE EXPOSURE METHODS ==============

    @JsonIgnore
    public Integer getFacadeExp() {
        return facadeExp;
    }

    @JsonProperty("facadeExp")
    public FacadeExposureEnum getFacadeExpEnum() {
        return facadeExp != null ? FacadeExposureEnum.valueOf(facadeExp) : null;
    }

    public void setFacadeExp(FacadeExposureEnum facadeExposureEnum) {
        this.facadeExp = facadeExposureEnum != null ? facadeExposureEnum.getValue() : null;
    }

    // ============== PEDESTRIANS ESTIMATE METHODS ==============

    @JsonIgnore
    public Integer getPedestriansEstimate() {
        return pedestrEst;
    }

    @JsonProperty("pedestrEst")
    public PedestriansEstimateEnum getPedestriansEstimateEnum() {
        return pedestrEst != null ? PedestriansEstimateEnum.valueOf(pedestrEst) : null;
    }

    public void setPedestriansEstimate(PedestriansEstimateEnum pedestrEstEnum) {
        this.pedestrEst = pedestrEstEnum != null ? pedestrEstEnum.getValue() : null;
    }

    // ============== FACADE SIZE ==============

    public Double getFacadeSz() {
        return facadeSz;
    }

    public void setFacadeSz(Double facadeSz) {
        this.facadeSz = facadeSz;
    }

    // ============== STREET PAVEMENT TYPE METHODS ==============

    @JsonIgnore
    public Integer getStreetPavementType() {
        return strtType;
    }

    @JsonProperty("strtType")
    public StreetPavementTypeEnum getStreetPavementTypeEnum() {
        return strtType != null ? StreetPavementTypeEnum.valueOf(strtType) : null;
    }

    public void setStreetPavementType(StreetPavementTypeEnum streetPavementTypeEnum) {
        this.strtType = streetPavementTypeEnum != null ? streetPavementTypeEnum.getValue() : null;
    }

    // ============== NEARBY COMMERCE METHODS ==============

    @JsonIgnore
    public Integer getNearbyCommerce() {
        return nearbyCom;
    }

    @JsonProperty("nearbyCom")
    public NearbyCommerceEnum getNearbyCommerceEnum() {
        return nearbyCom != null ? NearbyCommerceEnum.valueOf(nearbyCom) : null;
    }

    public void setNearbyCommerce(NearbyCommerceEnum nearbyComEnum) {
        this.nearbyCom = nearbyComEnum != null ? nearbyComEnum.getValue() : null;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getSaved() {
        return dSvd;
    }

    public void setSaved(Integer saved) {
        this.dSvd = saved;
    }
}
