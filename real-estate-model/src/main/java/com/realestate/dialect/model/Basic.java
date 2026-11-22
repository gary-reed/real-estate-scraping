package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Basic listing information including type, operation, price, and terms.
 * Migrated from L_Commons v4 Basic.java
 */
public class Basic {

    // ============== FIELDS ==============
    @SerializedName("pType")
    @Expose
    @JsonIgnore
    private Integer pType; // TypeEnum

    @SerializedName("pSubType")
    @Expose
    @JsonProperty("pSubType")
    private SubTypeEnum pSubType;

    @SerializedName("operation")
    @Expose
    @JsonIgnore
    private Integer operation; // OperationEnum

    @SerializedName("topTerm")
    @Expose
    @JsonIgnore
    private Integer topTerm; // TermEnum - The primary term for the listing

    @SerializedName("price")
    @Expose
    @JsonProperty("price")
    private Double price;

    @SerializedName("priceRatio")
    @Expose
    @JsonProperty("priceRatio")
    Double priceRatio; // Package-private for direct access from RealEstateListing

    @SerializedName("currency")
    @Expose
    @JsonProperty("currency")
    private String currency;

    /** Original price in the site's native currency (before USD conversion). */
    @SerializedName("originalPrice")
    @Expose
    @JsonProperty("originalPrice")
    private Double originalPrice;

    @SerializedName("bSvd")
    @Expose
    @JsonIgnore
    private Integer bSvd; // SavedEnum equivalent

    // ============== NESTED ENUMS ==============

    /**
     * Property operation type (buy, rent, share).
     */
    public enum OperationEnum implements SerializableEnum {
        BUY("buy", 1),
        RENT("rent", 2),
        SHARE("share", 3);

        private static final Map<Integer, OperationEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        private static final Map<String, OperationEnum> BY_NAME = SerializableEnum.buildNameMap(values());
        
        private final String name;
        private final int value;

        OperationEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static OperationEnum valueOf(int value) { return BY_VALUE.get(value); }
        public static OperationEnum fromName(String name) { return BY_NAME.get(name.toLowerCase()); }
    }

    /**
     * Rental term length.
     */
    public enum TermEnum implements SerializableEnum {
        LONG_TERM("long", 1),
        MID_TERM("mid", 2),
        SHORT_TERM("short", 3),
        LEASE("lease", 4);

        private static final Map<Integer, TermEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        private static final Map<String, TermEnum> BY_NAME = SerializableEnum.buildNameMap(values());
        
        private final String name;
        private final int value;

        TermEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static TermEnum valueOf(int value) { return BY_VALUE.get(value); }
        public static TermEnum fromName(String name) { return BY_NAME.get(name.toLowerCase()); }
    }

    /**
     * Property type (residential, commercial).
     */
    public enum TypeEnum implements SerializableEnum {
        RESIDENTIAL("residential", 1),
        COMMERCIAL("commercial", 2);

        private static final Map<Integer, TypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        private static final Map<String, TypeEnum> BY_NAME = SerializableEnum.buildNameMap(values());
        
        private final String name;
        private final int value;

        TypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static TypeEnum valueOf(int value) { return BY_VALUE.get(value); }
        public static TypeEnum fromName(String name) { return BY_NAME.get(name.toLowerCase()); }
    }

    /**
     * Property subtype (apartment, house, studio, etc.).
     */
    public enum SubTypeEnum implements SerializableEnum {
        // Residential subtypes
        RES_APARTMENT("apartment", 1),
        RES_DETACHEDHOUSE_VILLA("detachedHouseAndVilla", 2),
        RES_ATTACH_SEMIHOUSE("attachedAndSemiAttachedHouse", 3),
        RES_LOFT("loftAndWorkshopAndShowroom", 4),
        RES_DUPLEX("duplexAndTriplex", 4),
        RES_BUILDINGCASTLE("buildingAndCastle", 9),
        
        // Commercial subtypes
        COM_OFFICE("office", 51),
        COM_RETAIL("retail", 52),
        COM_WAREHOUSE("warehouse", 53),
        COM_INDUSTRIAL("industrial", 54),
        COM_HOSPITALITY("hospitality", 55),
        COM_HEALTHCARE("healthcare", 56),
        COM_OTHER("other_com", 57);

        private static final Map<Integer, SubTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        private static final Map<String, SubTypeEnum> BY_NAME = SerializableEnum.buildNameMap(values());
        
        private final String name;
        private final int value;

        SubTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static SubTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
        public static SubTypeEnum fromName(String name) { return BY_NAME.get(name.toLowerCase()); }
        
        /**
         * Check if subtype is a house type (detached, semi-detached, terraced, etc.)
         */
        public boolean isHouseType() {
            return this == RES_DETACHEDHOUSE_VILLA || this == RES_ATTACH_SEMIHOUSE;
        }
        
        /**
         * Check if subtype requires floor number information
         */
        public boolean isFloorRelated() {
            return this == RES_APARTMENT || this == RES_LOFT || this == RES_DUPLEX;
        }
        
        /**
         * Check if subtype is residential
         */
        public boolean isResidential() {
            return this.value < 50;
        }
        
        /**
         * Check if subtype is commercial
         */
        public boolean isCommercial() {
            return this.value >= 50;
        }
    
    }

    // ============== TYPE GETTERS/SETTERS ==============

    @JsonProperty("propertyType")
    public TypeEnum getPropertyType() {
        return pType != null ? TypeEnum.valueOf(pType) : null;
    }

    public void setPropertyType(TypeEnum type) {
        this.pType = type != null ? type.getValue() : null;
    }

    @JsonIgnore
    public Integer getPropertyTypeValue() {
        return pType;
    }

    // ============== SUBTYPE GETTERS/SETTERS ==============

    // public SubTypeEnum getPropertySubType() {
    //     return pSubType != null ? SubTypeEnum.valueOf(pSubType) : null;
    // }
    public void setPropertySubType(SubTypeEnum subType) {
        //this.pSubType = subType != null ? subType.getValue() : null;
        this.pSubType = subType;
    }
    // public Integer getPropertySubTypeValue() {
    //     return pSubType;
    // }
    public SubTypeEnum getPropertySubType() {
        return pSubType;
    }

    // ============== OPERATION GETTERS/SETTERS ==============

    @JsonProperty("operation")
    public OperationEnum getOperation() {
        return operation != null ? OperationEnum.valueOf(operation) : null;
    }

    public void setOperation(OperationEnum op) {
        this.operation = op != null ? op.getValue() : null;
    }

    @JsonIgnore
    public Integer getOperationValue() {
        return operation;
    }

    // ============== TERM GETTERS/SETTERS ==============

    @JsonProperty("topTerm")
    public TermEnum getTopTermEnum() {
        return topTerm != null ? TermEnum.valueOf(topTerm) : null;
    }

    public void setTopTerm(TermEnum term) {
        this.topTerm = term != null ? term.getValue() : null;
    }

    @JsonIgnore
    public Integer getTopTerm() {
        return topTerm;
    }

    // ============== PRICE GETTERS/SETTERS ==============

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getPriceRatio() {
        return priceRatio;
    }

    public void setPriceRatio(Double priceRatio) {
        this.priceRatio = priceRatio;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Double getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(Double originalPrice) {
        this.originalPrice = originalPrice;
    }

    /**
     * Asking price in the site's native currency for filter / Phase 0 checks.
     * Prefers {@link #originalPrice} when set (non-USD sites after USD conversion);
     * otherwise {@link #price} (USD sites, or before conversion).
     */
    @JsonIgnore
    public Double getPriceInSiteCurrency() {
        return originalPrice != null ? originalPrice : price;
    }

    // ============== HELPER METHODS ==============

    /**
     * Check if this listing is for buying
     */
    @JsonIgnore
    public boolean isBuy() {
        return OperationEnum.BUY.equals(getOperation());
    }

    /**
     * Check if this listing is for renting
     */
    @JsonIgnore
    public boolean isRent() {
        return OperationEnum.RENT.equals(getOperation());
    }

    /**
     * Check if this listing is for sharing
     */
    @JsonIgnore
    public boolean isShare() {
        return OperationEnum.SHARE.equals(getOperation());
    }

    /**
     * Check if this is a residential property type
     */
    @JsonIgnore
    public boolean isResidentialType() {
        return TypeEnum.RESIDENTIAL.equals(getPropertyType());
    }

    /**
     * Check if this is a commercial property type
     */
    @JsonIgnore
    public boolean isCommercialType() {
        return TypeEnum.COMMERCIAL.equals(getPropertyType());
    }

    
	/**
     * Check if the property subtype is a house type (detached, semi-detached, terraced, etc.)
     */
    @JsonIgnore
	public boolean isSubTypeHouseLike() {
		if(this.pSubType==null)
			return false;
		return this.pSubType.isHouseType();
	}
    
    /**
     * Check if the property subtype is floor-related (apartment, loft, duplex, etc.)
     */
    @JsonIgnore
	public boolean isSubTypeFloorRelated() {
		if(this.pSubType==null)
			return false;
		return this.pSubType.isFloorRelated();
	}

	/**
     * Check if the property subtype is Commercial Retail
     */
    @JsonIgnore
    public boolean isCommercialRetail(){
        if (isCommercialType()) {
            if(pSubType==null)
                return false;
            return pSubType.equals(SubTypeEnum.COM_RETAIL);
        }
        return false;
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
