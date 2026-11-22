package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.EnumSet;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Furnishing details of a property.
 * Migrated from L_Commons v3 Furnish.java
 */
public class Furnish {

    // ============== NESTED ENUMS ==============

    /**
     * Furnishing type/level.
     */
    public enum FurnishingTypeEnum implements SerializableEnum {
        FURNISHED("furnished", 1),
        UNFURNISHED("unfurnished", 2),
        SEMI_FURNISHED("semi", 3),
        BARE("bare", 4),
        UNDEFINED("undefined", 0);

        private static final Map<Integer, FurnishingTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        private static final Map<String, FurnishingTypeEnum> BY_NAME = SerializableEnum.buildNameMap(values());
        
        private final String name;
        private final int value;

        FurnishingTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static FurnishingTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
        public static FurnishingTypeEnum fromName(String name) { 
            return name != null ? BY_NAME.get(name.toLowerCase()) : null; 
        }
    }

    /**
     * Basic appliances.
     */
    public enum BasicAppliancesEnum implements SerializableEnum {
        WASHING_MACHINE("washing_machine", 1),
        DRYER("dryer", 2),
        DISHWASHER("dishwasher", 3),
        REFRIGERATOR("refrigerator", 4),
        FREEZER("freezer", 5),
        OVEN("oven", 6),
        MICROWAVE("microwave", 7),
        STOVE("stove", 8),
        HEATERS("heaters", 9),  
        ;

        private static final Map<Integer, BasicAppliancesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        BasicAppliancesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static BasicAppliancesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Furnish amenities.
     */
    public enum FurnishAmenitiesEnum implements SerializableEnum {
        WASHING_MACHINE("washing_machine", 1),
        TV("tv", 2),
        INTERNET("internet", 3),
        AIR_CONDITIONER("air_conditioner", 4),
        DISHWASHER("dishwasher", 5),
        FREEZER("freezer", 6),
        OVEN("oven", 7),
        SOFA("sofa", 8),
        CURTAINS("curtains", 9),
        WARDROBE("wardrobe", 10),
        BEDS("beds", 11),
        DESK("desk", 12),

        ;

        private static final Map<Integer, FurnishAmenitiesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        FurnishAmenitiesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static FurnishAmenitiesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Heating system type.
     */
    public enum HeatingSystemEnum implements SerializableEnum {
        CENTRAL("central", 1),
        COLLECTIVE("collective", 2),
        GAS("gas", 3),
        ELECTRIC("electric", 4),
        OIL("oil", 5),
        UNDERFLOOR("underfloor", 6),
        HEAT_PUMP("heat_pump", 7);

        private static final Map<Integer, HeatingSystemEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        HeatingSystemEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static HeatingSystemEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    // ============== FIELDS ==============

    @SerializedName("fType")
    @Expose
    private FurnishingTypeEnum fType = FurnishingTypeEnum.UNDEFINED;

    @SerializedName("basicAppls")
    @Expose
    private EnumSet<BasicAppliancesEnum> basicAppls;

    @SerializedName("bdrmBed")
    @Expose
    private Integer bdrmBed;

    @SerializedName("bdrmSecBed")
    @Expose
    private Integer bdrmSecBed;

    @SerializedName("heatSys")
    @Expose
    @JsonIgnore
    private Integer heatSys; // HeatingSystemEnum

    @SerializedName("fAmenities")
    @Expose
    private EnumSet<FurnishAmenitiesEnum> fAmenities;

    @SerializedName("ovenType")
    @Expose
    private Integer ovenType;

    @SerializedName("fSvd")
    @Expose
    @JsonIgnore
    private Integer fSvd;

    // ============== FURNISHING TYPE METHODS ==============

    public FurnishingTypeEnum getFurnishType() {
        return this.fType;
    }

    public void setFurnishType(FurnishingTypeEnum furnishType) {
        this.fType = furnishType;
    }

    public void setFurnishType(int isFurnished) {
        setFurnished(isFurnished != 0);
    }

    public void setFurnished(boolean isFurnished) {
        this.fType = isFurnished ? FurnishingTypeEnum.FURNISHED : FurnishingTypeEnum.UNFURNISHED;
    }

    public boolean isFurnished() {
        return this.fType != null && fType.equals(FurnishingTypeEnum.FURNISHED);
    }

    public boolean needsCheckBasicAppl() {
        if (this.fType != null) {
            return fType.equals(FurnishingTypeEnum.SEMI_FURNISHED)
                    || fType.equals(FurnishingTypeEnum.UNFURNISHED);
        }
        return false;
    }

    // ============== BASIC APPLIANCES METHODS ==============

    public EnumSet<BasicAppliancesEnum> getBasicAppliances() {
        return basicAppls;
    }

    public void addBasicAppliance(BasicAppliancesEnum basicAppliance) {
        if (this.basicAppls == null) {
            this.basicAppls = EnumSet.noneOf(BasicAppliancesEnum.class);
        }
        this.basicAppls.add(basicAppliance);
    }

    // ============== FURNISH AMENITIES METHODS ==============

    public EnumSet<FurnishAmenitiesEnum> getFurnishAmenities() {
        return fAmenities;
    }

    public void addFurnishAmenity(FurnishAmenitiesEnum furnishAmenity) {
        if (this.fAmenities == null) {
            this.fAmenities = EnumSet.noneOf(FurnishAmenitiesEnum.class);
        }
        this.fAmenities.add(furnishAmenity);
    }

    // ============== HEATING SYSTEM METHODS ==============

    @JsonIgnore
    public Integer getHeatingSystem() {
        return heatSys;
    }

    @JsonProperty("heatingSystem")
    public HeatingSystemEnum getHeatingSystemEnum() {
        return heatSys != null ? HeatingSystemEnum.valueOf(heatSys) : null;
    }

    public void setHeatingSystem(HeatingSystemEnum heatingSystem) {
        this.heatSys = heatingSystem != null ? heatingSystem.getValue() : null;
    }

    // ============== BED TYPE METHODS ==============

    public Integer getMainBedType() {
        return bdrmBed;
    }

    public void setMainBedType(Integer bedType) {
        this.bdrmBed = bedType;
    }

    public Integer getSecondaryBedType() {
        return bdrmSecBed;
    }

    public void setSecondaryBedType(Integer bedType) {
        this.bdrmSecBed = bedType;
    }

    // ============== OVEN TYPE ==============

    public Integer getOvenType() {
        return ovenType;
    }

    public void setOvenType(Integer ovenType) {
        this.ovenType = ovenType;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getSaved() {
        return fSvd;
    }

    public void setSaved(Integer saved) {
        this.fSvd = saved;
    }
}
