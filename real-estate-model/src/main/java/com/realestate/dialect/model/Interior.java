package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.EnumSet;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Interior details of a property.
 * Migrated from L_Commons v4 Interior.java
 */
public class Interior {

    // ============== NESTED ENUMS ==============

    /**
     * Bedroom type for shared accommodation.
     */
    public enum BedroomTypeEnum implements SerializableEnum {
        PRIVATE("private", 1),
        SHARED("shared", 2),
        LIVING_ROOM("living_room", 3);

        private static final Map<Integer, BedroomTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        BedroomTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static BedroomTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Bathroom type.
     */
    public enum BathroomTypeEnum implements SerializableEnum {
        FULL("full", 1),
        HALF("half", 2),
        ENSUITE("ensuite", 3),
        SHARED("shared", 4);

        private static final Map<Integer, BathroomTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        BathroomTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static BathroomTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Kitchen type.
     */
    public enum KitchenTypeEnum implements SerializableEnum {
        FITTED("fitted", 1),
        OPEN_PLAN("open_plan", 2),
        SEPARATE("separate", 3),
        GALLEY("galley", 4);

        private static final Map<Integer, KitchenTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        KitchenTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static KitchenTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Shower/bath type.
     */
    public enum ShowerBathEnum implements SerializableEnum {
        NORMAL_SHOWER("normal_shower", 1),
        MASSAGE_SHOWER("massage_shower", 2),
        WALK_IN_SHOWER("walk_in_shower", 3),
        BATHTUB("bathtub", 4),
        JACUZZI("jacuzzi", 5),
        ;

        private static final Map<Integer, ShowerBathEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        ShowerBathEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static ShowerBathEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Bathroom facilities.
     */
    public enum BathFacilitiesEnum implements SerializableEnum {
        HEATED_TOWEL_RAIL("heated_towel_rail", 1),
        UNDERFLOOR_HEATING("underfloor_heating", 2),
        BIDET("bidet", 3),
        SEPARATE_TOILET("separate_toilet", 4),
        EN_SUITE("en_suite", 5),
        DOUBLE_SINK("double_sink", 6)
        ;

        private static final Map<Integer, BathFacilitiesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        BathFacilitiesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static BathFacilitiesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    // ============== FIELDS ==============

    @SerializedName("bdrmSz")
    @Expose
    private Double bdrmSz;

    @SerializedName("bdrmSecSz")
    @Expose
    private Double bdrmSecSz;

    @SerializedName("bdrmType")
    @Expose
    @JsonIgnore
    private Integer bdrmType; // BedroomTypeEnum

    @SerializedName("bthrmType")
    @Expose
    private Object bthrmType; // Can be array for BUY-RENT or integer for SHARE

    @SerializedName("bthrmSz")
    @Expose
    private Double bthrmSz;

    @SerializedName("tiles")
    @Expose
    private Integer tiles;

    @SerializedName("showerBath")
    @Expose
    private EnumSet<ShowerBathEnum> showerBath;

    @SerializedName("bthFacils")
    @Expose
    private EnumSet<BathFacilitiesEnum> bthFacils;

    @SerializedName("ktchnSz")
    @Expose
    private Double ktchnSz;

    @SerializedName("ktchnType")
    @Expose
    @JsonIgnore
    private Integer ktchnType; // KitchenTypeEnum

    @SerializedName("ktchnLayout")
    @Expose
    private Integer ktchnLayout;

    @SerializedName("ktchnCntrTop")
    @Expose
    private Integer ktchnCntrTop;

    @SerializedName("fullBathroomCount")
    @Expose
    private Integer fullBathroomCount;

    @SerializedName("iSvd")
    @Expose
    @JsonIgnore
    private Integer iSvd;

    // ============== BEDROOM TYPE METHODS ==============

    @JsonIgnore
    public Integer getBdrmType() {
        return bdrmType;
    }

    @JsonProperty("bedroomType")
    public BedroomTypeEnum getBdrmTypeEnum() {
        return bdrmType != null ? BedroomTypeEnum.valueOf(bdrmType) : null;
    }

    public void setBdrmType(BedroomTypeEnum bdrmTypeEnum) {
        this.bdrmType = bdrmTypeEnum != null ? bdrmTypeEnum.getValue() : null;
    }

    // ============== KITCHEN TYPE METHODS ==============

    @JsonIgnore
    public Integer getKitchenType() {
        return ktchnType;
    }

    @JsonProperty("kitchenType")
    public KitchenTypeEnum getKitchenTypeEnum() {
        return ktchnType != null ? KitchenTypeEnum.valueOf(ktchnType) : null;
    }

    public void setKitchenType(KitchenTypeEnum kitchenTypeEnum) {
        this.ktchnType = kitchenTypeEnum != null ? kitchenTypeEnum.getValue() : null;
    }

    // ============== SHOWER/BATH METHODS ==============

    public EnumSet<ShowerBathEnum> getShowerBath() {
        return showerBath;
    }

    public void addShowerBath(ShowerBathEnum showerBathType) {
        if (this.showerBath == null) {
            this.showerBath = EnumSet.noneOf(ShowerBathEnum.class);
        }
        this.showerBath.add(showerBathType);
    }

    // ============== BATH FACILITIES METHODS ==============

    public EnumSet<BathFacilitiesEnum> getBathFacilities() {
        return bthFacils;
    }

    public void addBathFacility(BathFacilitiesEnum bathFacility) {
        if (this.bthFacils == null) {
            this.bthFacils = EnumSet.noneOf(BathFacilitiesEnum.class);
        }
        this.bthFacils.add(bathFacility);
    }

    // ============== BASIC GETTERS/SETTERS ==============

    public Double getBdrmSz() {
        return bdrmSz;
    }

    public void setBdrmSz(Double bdrmSz) {
        this.bdrmSz = bdrmSz;
    }

    public Double getBdrmSecSz() {
        return bdrmSecSz;
    }

    public void setBdrmSecSz(Double bdrmSecSz) {
        this.bdrmSecSz = bdrmSecSz;
    }

    public Object getBthrmType() {
        return bthrmType;
    }

    public void setBthrmType(Object bthrmType) {
        this.bthrmType = bthrmType;
    }

    public Double getBthrmSz() {
        return bthrmSz;
    }

    public void setBthrmSz(Double bthrmSz) {
        this.bthrmSz = bthrmSz;
    }

    public Double getKtchnSz() {
        return ktchnSz;
    }

    public void setKtchnSz(Double ktchnSz) {
        this.ktchnSz = ktchnSz;
    }

    public Integer getKtchnLayout() {
        return ktchnLayout;
    }

    public void setKtchnLayout(Integer ktchnLayout) {
        this.ktchnLayout = ktchnLayout;
    }

    public Integer getKtchnCntrTop() {
        return ktchnCntrTop;
    }

    public void setKtchnCntrTop(Integer ktchnCntrTop) {
        this.ktchnCntrTop = ktchnCntrTop;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getSaved() {
        return iSvd;
    }

    public void setSaved(Integer saved) {
        this.iSvd = saved;
    }
}
