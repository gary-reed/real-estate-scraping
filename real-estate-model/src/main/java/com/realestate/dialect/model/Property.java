package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Property-specific information (amenities, dimensions, etc.).
 * Migrated from L_Commons v3 Property.java
 */
public class Property {

    // ============== NESTED ENUMS ==============

    /**
     * Property orientation.
     */
    public enum OrientationEnum implements SerializableEnum {
        NORTH("north", 1),
        SOUTH("south", 2),
        EAST("east", 3),
        WEST("west", 4),
        NORTH_EAST("north_east", 5),
        NORTH_WEST("north_west", 6),
        SOUTH_EAST("south_east", 7),
        SOUTH_WEST("south_west", 8);

        private static final Map<Integer, OrientationEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        OrientationEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static OrientationEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Property condition status.
     */
    public enum PropertyStatusEnum implements SerializableEnum {
        NEW_BUILD("new_build", 1),
        RENOVATED("renovated", 2),
        GOOD_CONDITION("good_condition", 3),
        NEEDS_RENOVATION("needs_renovation", 4),
        UNFINISHED("unfinished", 5);

        private static final Map<Integer, PropertyStatusEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        PropertyStatusEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static PropertyStatusEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Property view type.
     */
    public enum ViewEnum implements SerializableEnum {
        GARDEN_VIEW("garden_view", 1),
        CITY_VIEW("city_view", 2),
        SEA_VIEW("sea_view", 3),
        MOUNTAIN_VIEW("mountain_view", 4),
        PARK_VIEW("park_view", 5),
        STREET_VIEW("street_view", 6),
        COURTYARD_VIEW("courtyard_view", 7),
        MONUMENT_VIEW("monument_view", 8),

        ;

        private static final Map<Integer, ViewEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        ViewEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static ViewEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Energy consumption rating.
     */
    public enum EnergyConsumptionEnum implements SerializableEnum {
        A("a", 1),
        B("b", 2),
        C("c", 3),
        D("d", 4),
        E("e", 5),
        F("f", 6),
        G("g", 7);

        private static final Map<Integer, EnergyConsumptionEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        EnergyConsumptionEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static EnergyConsumptionEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Floor type/material.
     */
    public enum FloorTypeEnum implements SerializableEnum {
        HARDWOOD("hardwood", 1),
        LAMINATE("laminate", 2),
        TILE("tile", 3),
        CARPET("carpet", 4),
        CONCRETE("concrete", 5),
        VINYL("vinyl", 6);

        private static final Map<Integer, FloorTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        FloorTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static FloorTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Floor number level category.
     */
    public enum FloorNumberEnum implements SerializableEnum {
        FLOORNR_BASEMENT("basement", -1),
        FLOORNR_GROUND("ground_floor", 0),
        FLOORNR_LOWER("lower_floor", 1),
        FLOORNR_MIDDLE("middle_floor", 2),
        FLOORNR_HIGH("high_floor", 3),
        FLOORNR_PENTHOUSE("penthouse", 4);

        private static final Map<Integer, FloorNumberEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        FloorNumberEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static FloorNumberEnum valueOf(int value) { return BY_VALUE.get(value); }
        
        /**
         * Resolve floor number category based on floor number, building height, and penthouse flag.
         */
        public static FloorNumberEnum resolveFloorNrValue(Integer floorNr, Integer buildingHeight, boolean isPenthouse) {
            if (floorNr == null) return null;
            
            if (floorNr < 0) return FLOORNR_BASEMENT;
            if (floorNr == 0) return FLOORNR_GROUND;
            
            if (buildingHeight != null && buildingHeight > 2) {
                if (isPenthouse || floorNr.equals(buildingHeight)) {
                    return FLOORNR_PENTHOUSE;
                }
                
                //3+ storeys: thirds of 1..height (e.g. 8 floors → 1–2 lower, 3–5 middle, 6–8 high)
                // Use ceiling division to avoid floating point precision issues
                int third = (int) Math.ceil((buildingHeight - 1) / 3.0);
                if (floorNr <= third) return FLOORNR_LOWER;
                if (floorNr <= 2 * third) return FLOORNR_MIDDLE;
                return FLOORNR_HIGH;

                /*double temp = (buildingHeight - 1.0) / 3;
                if (temp < 1) {
                    if (floorNr == 1) return FLOORNR_LOWER;
                    if (floorNr.equals(buildingHeight)) return FLOORNR_HIGH;
                    return FLOORNR_MIDDLE;
                } else {
                    if (floorNr <= Math.floor((buildingHeight - 1) - 2 * temp)) return FLOORNR_LOWER;
                    if (floorNr <= Math.ceil(buildingHeight - 1 - temp)) return FLOORNR_MIDDLE;
                    return FLOORNR_HIGH;
                }*/
            }
            
            return null; // Not able to resolve
        }
    }

    /**
     * Property amenities.
     */
    public enum PropertyAmenitiesEnum implements SerializableEnum {
        BALCONY_TERRACE("balcony_terrace", 1),
        FIREPLACE("fireplace", 5),
        ALARM("alarm", 6),
        DOUBLE_GLAZING("double_glazing", 7),
        CELLAR("cellar", 8),
        ATTIC("attic", 9),
        UTILITY_ROOM("utility_room", 10),
        INTERPHONE("interphone", 11),
        VAULTED_CEILING("vaulted_ceiling", 12),
        CROWN_MOLDING("crown_molding", 13),
        BAY_WINDOW("bay_window", 14),
        SKYLIGHT("skylight", 15),
        UNDERFLOOR_HEATING("underfloor_heating", 16)
        ;


        private static final Map<Integer, PropertyAmenitiesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        PropertyAmenitiesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static PropertyAmenitiesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    // ============== FIELDS ==============

    @SerializedName("amenities")
    @Expose
    private HashSet<Enum> amenities;

    @SerializedName("blcnSz")
    @Expose
    private Double blcnSz;

    @SerializedName("othrAmens")
    @Expose
    private List<String> othrAmens;

    @SerializedName("bdrms")
    @Expose
    private Integer bedrooms;

    @SerializedName("bthrms")
    @Expose
    private Integer bathrooms;

    @SerializedName("surface")
    @Expose
    private Integer surface;

    @SerializedName("flrNr")
    @Expose
    private Integer flrNr;

    @SerializedName("orient")
    @Expose
    @JsonIgnore
    private Integer orient; // OrientationEnum

    @SerializedName("pStatus")
    @Expose
    @JsonIgnore
    private Integer pStatus; // PropertyStatusEnum

    @SerializedName("view")
    @Expose
    @JsonIgnore
    private Integer view; // ViewEnum

    @SerializedName("roomsTot")
    @Expose
    private Integer roomsTot;

    @SerializedName("energy")
    @Expose
    @JsonIgnore
    private Integer energy; // EnergyConsumptionEnum

    /** Numeric EPC score (e.g. 73, 83) extracted from EPC chart images/PDFs. */
    @SerializedName("energyScore")
    @Expose
    @JsonProperty("energyScore")
    private Integer energyScore;

    @SerializedName("pLvls")
    @Expose
    private Integer pLvls; // Property levels

    @SerializedName("flrType")
    @Expose
    @JsonIgnore
    private Integer flrType; // FloorTypeEnum

    @SerializedName("teamType")
    @Expose
    private String teamType; // Only for office case

    @SerializedName("teamRange")
    @Expose
    private Integer[] teamRange; // Min and max range

    @SerializedName("pSvd")
    @Expose
    @JsonIgnore
    private Integer pSvd;

    // ============== ORIENTATION METHODS ==============

    @JsonIgnore
    public Integer getOrientation() {
        return orient;
    }

    @JsonProperty("orientation")
    public OrientationEnum getOrientationEnum() {
        return orient != null ? OrientationEnum.valueOf(orient) : null;
    }

    public void setOrientation(OrientationEnum orientationEnum) {
        this.orient = orientationEnum != null ? orientationEnum.getValue() : null;
    }

    // ============== PROPERTY STATUS METHODS ==============

    @JsonIgnore
    public Integer getPropertyStatus() {
        return pStatus;
    }

    @JsonProperty("propertyStatus")
    public PropertyStatusEnum getPropertyStatusEnum() {
        return pStatus != null ? PropertyStatusEnum.valueOf(pStatus) : null;
    }

    public void setPropertyStatus(PropertyStatusEnum pStatusEnum) {
        this.pStatus = pStatusEnum != null ? pStatusEnum.getValue() : null;
    }

    // ============== VIEW METHODS ==============

    @JsonIgnore
    public Integer getView() {
        return view;
    }

    @JsonProperty("view")
    public ViewEnum getViewEnum() {
        return view != null ? ViewEnum.valueOf(view) : null;
    }

    public void setView(ViewEnum viewEnum) {
        this.view = viewEnum != null ? viewEnum.getValue() : null;
    }

    // ============== ENERGY CONSUMPTION METHODS ==============

    @JsonIgnore
    public Integer getEnergyConsumption() {
        return energy;
    }

    @JsonProperty("energyConsumption")
    public EnergyConsumptionEnum getEnergyConsumptionEnum() {
        return energy != null ? EnergyConsumptionEnum.valueOf(energy) : null;
    }

    public void setEnergyConsumption(EnergyConsumptionEnum energyConsumpEnum) {
        this.energy = energyConsumpEnum != null ? energyConsumpEnum.getValue() : null;
    }

    public Integer getEnergyScore() {
        return energyScore;
    }

    public void setEnergyScore(Integer energyScore) {
        this.energyScore = energyScore;
    }

    // ============== FLOOR TYPE METHODS ==============

    @JsonIgnore
    public Integer getFloorType() {
        return flrType;
    }

    @JsonProperty("floorType")
    public FloorTypeEnum getFloorTypeEnum() {
        return flrType != null ? FloorTypeEnum.valueOf(flrType) : null;
    }

    public void setFloorType(FloorTypeEnum floorTypeEnum) {
        this.flrType = floorTypeEnum != null ? floorTypeEnum.getValue() : null;
    }

    // ============== AMENITIES METHODS ==============

    public HashSet<Enum> getAmenities() {
        return amenities;
    }

    public void addPropertyAmenity(Enum amenity) {
        if (this.amenities == null) {
            this.amenities = new HashSet<>();
        }
        this.amenities.add(amenity);
    }

    /** Null-safe amenity check — {@code false} when amenities or {@code amenity} is null. */
    public boolean containsAmenity(Enum amenity) {
        return amenity != null && amenities != null && amenities.contains(amenity);
    }

    public void setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum floorNrEnum) {
        if (floorNrEnum == null) return;

        if (amenities == null) {
            addPropertyAmenity(floorNrEnum);
        } else {
            boolean hasFloorSet = doesPropAmenitiesHaveAnyFloorSet();
            if (!hasFloorSet) {
                addPropertyAmenity(floorNrEnum);
            } else if (containsAmenity(FloorNumberEnum.FLOORNR_PENTHOUSE)) {
                // Re-evaluate penthouse based on floor+height
                amenities.remove(FloorNumberEnum.FLOORNR_PENTHOUSE);
                addPropertyAmenity(floorNrEnum);
            }
        }
    }
    
    public boolean doesPropAmenitiesHaveAnyFloorSet() {
        if (amenities == null) return false;
        for (FloorNumberEnum floorEnum : FloorNumberEnum.values()) {
            if (amenities.contains(floorEnum)) return true;
        }
        return false;
    }

    // ============== BASIC GETTERS/SETTERS ==============

    public Integer getBedrooms() {
        return bedrooms;
    }

    public void setBedrooms(Integer bedrooms) {
        this.bedrooms = bedrooms;
    }
    
    public Integer getBathrooms() {
        return bathrooms;
    }

    public void setBathrooms(Integer bathrooms) {
        this.bathrooms = bathrooms;
    }
    
    public Integer getSurface() {
        return surface;
    }

    public void setSurface(Integer surface) {
        this.surface = surface;
    }

    public Integer getFloorNr() {
        return flrNr;
    }

    public void setFloorNr(Integer floorNr) {
        this.flrNr = floorNr;
    }

    public Integer getRoomsTotal() {
        return roomsTot;
    }

    public void setRoomsTotal(Integer roomsTot) {
        this.roomsTot = roomsTot;
    }

    public Integer getPropertyLevels() {
        return pLvls;
    }

    public void setPropertyLevels(Integer pLvls) {
        this.pLvls = pLvls;
    }

    public Double getBalconySz() {
        return blcnSz;
    }

    public void setBalconySz(Double blcnSz) {
        this.blcnSz = blcnSz;
    }

    public List<String> getOthrAmens() {
        return othrAmens;
    }

    public void setOthrAmens(List<String> othrAmens) {
        this.othrAmens = othrAmens;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getSaved() {
        return pSvd;
    }

    public void setSaved(Integer saved) {
        this.pSvd = saved;
    }
}
