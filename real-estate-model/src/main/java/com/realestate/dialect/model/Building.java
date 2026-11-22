package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import lombok.Data;

/**
 * Building information for a listing.
 * Migrated from L_Commons v4 Building.java
 */
public class Building {

    // Constant for inner construction listing appender
    public static final String INNER_CONSTRUCTION_LISTING_APPENDER = "__";

    // ============== NESTED ENUMS ==============

    /**
     * House architectural style.
     */
    public enum HouseStyleEnum implements SerializableEnum {

        A_FRAME("a_frame", 1),
        BUNGALOW("bungalow", 2),
        COLONIAL("colonial", 3),
        CONTEMPORARY("contemporary", 4),
        COTTAGE("cottage", 5),
        DOME("dome", 6),
        LOG("log", 7),
        MEDITERRANEAN("mediterranean", 8),
        RANCH("ranch", 9),
        SPANISH("spanish", 10),
        TUDOR("tudor", 11),
        VICTORIAN("victorian", 12),
        NONE_OTHER("none_other", 13);
        
        private static final Map<Integer, HouseStyleEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        HouseStyleEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static HouseStyleEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Construction material type.
     */
    public enum ConstructionMaterialEnum implements SerializableEnum {
        BRICK("brick", 1),
        STONE("stone", 2),
        CONCRETE("concrete", 3),
        WOOD("wood", 4),
        STEEL("steel", 5),
        MIXED("mixed", 6);

        private static final Map<Integer, ConstructionMaterialEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        ConstructionMaterialEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static ConstructionMaterialEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Roof type.
     */
    public enum RoofEnum implements SerializableEnum {
        FLAT("flat", 1),
        PITCHED("pitched", 2),
        TILED("tiled", 3),
        SLATE("slate", 4),
        METAL("metal", 5);

        private static final Map<Integer, RoofEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        RoofEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static RoofEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Building facilities/amenities.
     */
    public enum BuildingFacilitiesEnum implements SerializableEnum {
        ELEVATOR("elevator", 1),
        CONCIERGE("concierge", 2),
        GYM("gym", 3),
        POOL_PRIVATE("pool_private", 4),
        POOL_SHARED("pool_shared", 13),
        GARDEN_PRIVATE("garden_private", 5),
        GARDEN_SHARED("garden_shared", 14),
        PARKING("parking", 6),
        BIKE_STORAGE("bike_storage", 7),
        DISABLED_ACCESS("disabled_access", 8),
        SECURITY("security", 9),
        COMMUNAL_AREAS("communal_areas", 10),
        EVENT_ROOM("event_room", 11),
        CAVE("cave", 12)
        ;

        private static final Map<Integer, BuildingFacilitiesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        BuildingFacilitiesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static BuildingFacilitiesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Parking data details.
     */
    @Data
    public static class ParkingData {

        @SerializedName("price")
        @Expose
        private String price;
        @SerializedName("comment")
        @Expose
        private String comment;
        @SerializedName("space")
        @Expose
        private Integer space;
        @SerializedName("type")
        @Expose
        private String type;
        @SerializedName("included")
        @Expose
        private Boolean included;


    }

    // ============== FIELDS ==============

    @SerializedName("buildingAge")
    @Expose
    private Integer buildingAge; // Building age/year built

    @SerializedName("height")
    @Expose
    private Integer height; // Number of floors in building

    @SerializedName("unitsPerflr")
    @Expose
    private Integer unitsPerflr;

    @SerializedName("landSz")
    @Expose
    private Integer landSz;

    @SerializedName("landSzM")
    @Expose
    private String landSzM; // m2 or hectare

    @SerializedName("style")
    @Expose
    @JsonIgnore
    private Integer houseStyle; // HouseStyleEnum

    @SerializedName("material")
    @Expose
    @JsonIgnore
    private Integer constMaterial; // ConstructionMaterialEnum

    @SerializedName("roof")
    @Expose
    @JsonIgnore
    private Integer roof; // RoofEnum

    @SerializedName("bFacils")
    @Expose
    private EnumSet<BuildingFacilitiesEnum> bFacils;

    @SerializedName("othrFacils")
    @Expose
    private List<String> othrFacils;

    @SerializedName("caveSz")
    @Expose
    private Double caveSz;

    @SerializedName("poolSz")
    @Expose
    private Double poolSz;

    @SerializedName("prkngCnt")
    @Expose
    private Integer prkngCnt;

    @SerializedName("prkngData")
    @Expose
    private List<ParkingData> prkngData;

    @SerializedName("bSvd")
    @Expose
    @JsonIgnore
    private Integer bSvd;

    // ============== BUILDING FACILITIES METHODS ==============

    public EnumSet<BuildingFacilitiesEnum> getBuildingFacilities() {
        return bFacils;
    }

    public void addBuildingFacility(BuildingFacilitiesEnum buildingFacility) {
        if (this.bFacils == null) {
            this.bFacils = EnumSet.noneOf(BuildingFacilitiesEnum.class);
        }
        this.bFacils.add(buildingFacility);
    }

    // ============== PARKING METHODS ==============

    public List<ParkingData> getParkingData() {
        return prkngData;
    }

    public void addParkingData(ParkingData prkng) {
        if (this.prkngData == null) {
            this.prkngData = new ArrayList<>();
            this.prkngCnt = 0;
        }
        this.prkngData.add(prkng);
        this.prkngCnt = this.prkngCnt + prkng.getSpace();
    }

    public Integer getParkingCount() {
        return prkngCnt;
    }

    public void setParkingCount(int parkingCount) {
        this.prkngCnt = parkingCount;
    }

    // ============== STYLE/MATERIAL/ROOF METHODS ==============

    @JsonIgnore
    public Integer getHouseStyle() {
        return houseStyle;
    }

    @JsonProperty("style")
    public HouseStyleEnum getHouseStyleEnum() {
        return houseStyle != null ? HouseStyleEnum.valueOf(houseStyle) : null;
    }

    public void setHouseStyle(HouseStyleEnum style) {
        this.houseStyle = style != null ? style.getValue() : null;
    }

    @JsonIgnore
    public Integer getConstMaterial() {
        return constMaterial;
    }

    @JsonProperty("material")
    public ConstructionMaterialEnum getConstMaterialEnum() {
        return constMaterial != null ? ConstructionMaterialEnum.valueOf(constMaterial) : null;
    }

    public void setConstMaterial(ConstructionMaterialEnum material) {
        this.constMaterial = material != null ? material.getValue() : null;
    }

    @JsonIgnore
    public Integer getRoof() {
        return roof;
    }

    @JsonProperty("roof")
    public RoofEnum getRoofEnum() {
        return roof != null ? RoofEnum.valueOf(roof) : null;
    }

    public void setRoof(RoofEnum roofType) {
        this.roof = roofType != null ? roofType.getValue() : null;
    }

    // ============== BASIC GETTERS/SETTERS ==============

    public Integer getBuildingAge() {
        return buildingAge;
    }

    public void setBuildingAge(Integer buildingAge) {
        this.buildingAge = buildingAge;
    }

    //Fixed value defined for new Construction case. To be easily recognizable
	public void setBuildingAgeAsNewConstruction() {
		this.buildingAge = 10000;
	}


    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getLandSz() {
        return landSz;
    }

    public void setLandSz(Integer landSz) {
        this.landSz = landSz;
    }

    public String getLandSzM() {
        return landSzM;
    }

    public void setLandSzM(String landSzM) {
        this.landSzM = landSzM;
    }

    public Double getCaveSz() {
        return caveSz;
    }

    public void setCaveSz(Double caveSz) {
        this.caveSz = caveSz;
    }

    public Double getPoolSz() {
        return poolSz;
    }

    public void setPoolSz(Double poolSz) {
        this.poolSz = poolSz;
    }

    public List<String> getOthrFacils() {
        return othrFacils;
    }

    public void setOthrFacils(List<String> othrFacils) {
        this.othrFacils = othrFacils;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getSaved() {
        return bSvd;
    }

    public void setSaved(Integer saved) {
        this.bSvd = saved;
    }
}
