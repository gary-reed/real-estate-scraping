package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Transaction details including pricing terms, fees, and rental policies.
 * Migrated from L_Commons v4 Transaction.java
 */
public class Transaction {

    // ============== NESTED ENUMS ==============

    /**
     * Availability choice for the property.
     */
    public enum AvailableChoiceEnum implements SerializableEnum {
        NOW("now", 1),
        FROM("from", 2);

        private static final Map<Integer, AvailableChoiceEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        AvailableChoiceEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static AvailableChoiceEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Ownership type for the property.
     */
    public enum OwnershipEnum implements SerializableEnum {
        FULL_OWNERSHIP("full_ownership", 1),
        FREEHOLD("freehold", 2),
        LEASEHOLD("leasehold", 3),
        SHARE_FREEHOLD("share_freehold", 4),
        SHARE_OWNERSHIP("share_ownership", 5),
        COMMONHOLD("commonhold", 6);

        private static final Map<Integer, OwnershipEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        private static final Map<String, OwnershipEnum> BY_NAME = SerializableEnum.buildNameMap(values());
        
        private final String name;
        private final int value;

        OwnershipEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static OwnershipEnum valueOf(int value) { return BY_VALUE.get(value); }
        public static OwnershipEnum fromName(String name) { 
            return name != null ? BY_NAME.get(name.toLowerCase()) : null; 
        }
    }

    /**
     * Security deposit type.
     */
    public enum SecurityDepositEnum implements SerializableEnum {
        NONE("none", 0),
        ONE_MONTH("one_month", 1),
        TWO_MONTHS("two_months", 2),
        THREE_MONTHS("three_months", 3),
        SPECIFIC_AMOUNT("specific_amount", 99);

        private static final Map<Integer, SecurityDepositEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        SecurityDepositEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static SecurityDepositEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Rental requisites/requirements.
     */
    public enum RequisitesEnum implements SerializableEnum {
        GUARANTOR("guarantor", 1),
        PAYSLIPS("payslips", 2),
        REFERENCES("references", 3),
        ID_PROOF("id_proof", 4),
        CREDIT_CHECK("credit_check", 5),
        SEASONAL("seasonal", 6);

        private static final Map<Integer, RequisitesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        RequisitesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static RequisitesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Rental policies (pets, smoking, students, etc.).
     */
    public enum PoliciesEnum implements SerializableEnum {
        PETS_ALLOWED("pets_allowed", 1),
        SMOKING_ALLOWED("smoking_allowed", 2),
        STUDENTS_ALLOWED("students_allowed", 3),
        DSS_ALLOWED("dss_allowed", 4),
        FAMILIES_ALLOWED("families_allowed", 5),
        UTIL_COSTS_INCLUDED("util_costs", 6);

        private static final Map<Integer, PoliciesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        PoliciesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static PoliciesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Saved state indicator.
     * Used only for real Listings to indicate what sections of its details were already saved or not
     * in the create new Listing Workflow of the site. For scraper Listings this should always be set as SAVED_COMPLETE.
     */
    public enum SavedEnum implements SerializableEnum {
        NOT_SAVED("not_saved", 0),
        SAVED_COMPLETE("saved_complete", 1),
        SAVED_INCOMPLETE("saved_incomplete", 2);

        private static final Map<Integer, SavedEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        SavedEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static SavedEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    // ============== NESTED CLASSES ==============

    /**
     * Generic range with min/max values (used for age ranges, etc.)
     */
    public static class GenericRange {
        @SerializedName("min")
        @Expose
        private Integer min;
        
        @SerializedName("max")
        @Expose
        private Integer max;

        public Integer getMin() { return min; }
        public void setMin(Integer min) { this.min = min; }
        public Integer getMax() { return max; }
        public void setMax(Integer max) { this.max = max; }
    }

    // ============== FIELDS ==============

    //top price is set in the Basic Object
	// Seasonal case will have set value of "seasonal" inside Requisites array

    // Rental term prices
    @SerializedName("lTerm")
    @Expose
    private Double lTerm; // Long term price

    @SerializedName("mTerm")
    @Expose
    private Double mTerm; // Mid term price

    @SerializedName("sTerm")
    @Expose
    private Double sTerm; // Short term price

    @SerializedName("lease")
    @Expose
    private Double lease; // Lease price

    // Availability
    @SerializedName("availableC")
    @Expose
    private AvailableChoiceEnum availableC;

    @SerializedName("available")
    @Expose
    private String available; // Format yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
                              //will be considered as 'Possesion' for New Construction

    // Ownership
    @SerializedName("ownership")
    @Expose
    @JsonIgnore
    private Integer ownership; // OwnershipEnum

    // Policies and requisites
    @SerializedName("policies")
    @Expose
    private HashSet<Enum> policies;

    @SerializedName("othrPolicies")
    @Expose
    private List<String> othrPolicies;

    @SerializedName("requisites")
    @Expose
    private EnumSet<RequisitesEnum> requisites;

    @SerializedName("othrRequisites")
    @Expose
    private List<String> othrRequisites;

    // Agency fees
    @SerializedName("autoFees")
    @Expose
    private boolean autoFees;

    @SerializedName("saleAgencyFees")
    @Expose
    private String saleAgencyFees;

    @SerializedName("lTermFees")
    @Expose
    private String lTermFees;

    @SerializedName("mTermFees")
    @Expose
    private String mTermFees;

    @SerializedName("sTermFees")
    @Expose
    private String sTermFees;

    @SerializedName("leaseFees")
    @Expose
    private String leaseFees;

    // Charges and deposits
    @SerializedName("commCharges")
    @Expose
    private Double commCharges; // Service/communal charges

    @SerializedName("secDep")
    @Expose
    @JsonIgnore
    private Integer securityDeposit; // SecurityDepositEnum

    @SerializedName("secDepVal")
    @Expose
    private Integer securityDepositValue;

    // Share-specific fields
    @SerializedName("ageRange")
    @Expose
    private GenericRange ageRange;

    @SerializedName("utilCostVal")
    @Expose
    private Integer utilCostVal;

    // Commercial-specific fields
    @SerializedName("midtermPriceUnit")
    @Expose
    private String midtermPriceUnit;

    @SerializedName("leasePriceUnit")
    @Expose
    private String leasePriceUnit;

    @SerializedName("lTrnsfrPrice")
    @Expose
    private Double leaseTransferPrice;

    @SerializedName("leaseEndDate")
    @Expose
    private Date leaseEndDate;

    @SerializedName("activity")
    @Expose
    @JsonIgnore
    private Integer activity; // CommercialActivityEnum

    @SerializedName("specActivity")
    @Expose
    private String specActivity;

    @SerializedName("propUse")
    @Expose
    @JsonIgnore
    private Integer propUse; // PropertyUseEnum

    @SerializedName("capRate")
    @Expose
    private Double capRate;

    // Other
    @SerializedName("isNgtbl")
    @Expose
    private Boolean isNgtbl; // Is negotiable

    @SerializedName("rSvd")
    @Expose
    @JsonIgnore
    private Integer rSvd; // SavedEnum

    // ============== OWNERSHIP METHODS ==============

    @JsonIgnore
    public Integer getOwnership() {
        return ownership;
    }

    @JsonProperty("ownership")
    public OwnershipEnum getOwnershipEnum() {
        return ownership != null ? OwnershipEnum.valueOf(ownership) : null;
    }

    public void setOwnership(OwnershipEnum ownershipEnum) {
        this.ownership = ownershipEnum != null ? ownershipEnum.getValue() : null;
    }

    // ============== SECURITY DEPOSIT METHODS ==============

    @JsonIgnore
    public Integer getSecurityDeposit() {
        return securityDeposit;
    }

    @JsonProperty("securityDeposit")
    public SecurityDepositEnum getSecurityDepositEnum() {
        return securityDeposit != null ? SecurityDepositEnum.valueOf(securityDeposit) : null;
    }

    public void setSecurityDeposit(SecurityDepositEnum securityDepositEnum) {
        this.securityDeposit = securityDepositEnum != null ? securityDepositEnum.getValue() : null;
    }

    public Integer getSecurityDepositValue() {
        return securityDepositValue;
    }

    public void setSecurityDepositValue(Integer securityDepositValue) {
        this.securityDepositValue = securityDepositValue;
    }

    // ============== POLICIES METHODS ==============

    public HashSet<Enum> getPolicies() {
        return policies;
    }

    public void addPolicy(Enum policy) {
        if (this.policies == null) {
            this.policies = new HashSet<>();
        }
        this.policies.add(policy);
    }

    public void setPolicies(HashSet<Enum> policies) {
        this.policies = policies;
    }

    // ============== REQUISITES METHODS ==============

    public EnumSet<RequisitesEnum> getRequisites() {
        return requisites;
    }

    public void addRequisite(RequisitesEnum requisite) {
        if (this.requisites == null) {
            this.requisites = EnumSet.noneOf(RequisitesEnum.class);
        }
        this.requisites.add(requisite);
    }

    // ============== TERM PRICE GETTERS/SETTERS ==============

    public Double getlTerm() {
        return lTerm;
    }

    public void setlTerm(Double lTerm) {
        this.lTerm = lTerm;
    }

    public Double getmTerm() {
        return mTerm;
    }

    public void setmTerm(Double mTerm) {
        this.mTerm = mTerm;
    }

    public Double getsTerm() {
        return sTerm;
    }

    public void setsTerm(Double sTerm) {
        this.sTerm = sTerm;
    }

    public Double getLease() {
        return lease;
    }

    public void setLease(Double lease) {
        this.lease = lease;
    }

    public Double getCommCharges() {
        return commCharges;
    }

    public void setCommCharges(Double commCharges) {
        this.commCharges = commCharges;
    }

    // ============== AVAILABILITY METHODS ==============

    public AvailableChoiceEnum getAvailableC() {
        return availableC;
    }

    public void setAvailableC(AvailableChoiceEnum availableC) {
        this.availableC = availableC;
    }

    public String getAvailable() {
        return available;
    }

    public void setAvailable(String available) {
        this.available = available;
    }

    private static final SimpleDateFormat sdf = 
    		new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); 
    public void setAvailability(Date availability) {
		if(availability!=null){
            // Convert Date to ISO string format for Elasticsearch using 'sdf' defined above.
            this.available = sdf.format(availability);
        }
    }

    // ============== AGE RANGE METHODS ==============

    public void setAcceptedAge(int ageFrom, int ageTo) {
        this.ageRange = new GenericRange();
        this.ageRange.setMin(ageFrom);
        this.ageRange.setMax(ageTo);
    }

    public GenericRange getAcceptedAge() {
        return ageRange;
    }

    // ============== AGENCY FEES BUSINESS METHODS ==============

    /**
     * Calculate agency fees percentage from a fee price amount.
     * 
     * @param price The property price
     * @param feesAmount The fees amount
     * @param isRent Whether this is a rental (calculates based on 1 year rent)
     * @return The calculated fee percentage
     */
    public double calculateAgencyFeesFromFeePrice(int price, int feesAmount, boolean isRent) {
        double feePercentage = -1;
        if (isRent) {
            // Calculate based on 1 year rent
            feePercentage = Math.round((feesAmount * 100.0) / (price * 12.0));
        } else {
            feePercentage = (feesAmount * 100.0) / price;
            feePercentage = Math.round(feePercentage * 10) / 10.0;
        }
        return feePercentage;
    }

    /**
     * Set agency fees from a value amount.
     */
    public void setGeneralAgencyFeesFromValue(double price, int feesAmount, boolean isRent) {
        double feesPercent = calculateAgencyFeesFromFeePrice(
                Double.valueOf(price).intValue(), feesAmount, isRent);
        setGeneralAgencyFeesFromPercentage(feesPercent, isRent);
    }

    /**
     * Set agency fees from a percentage value.
     */
    public void setGeneralAgencyFeesFromPercentage(double feesPercentage, boolean isRent) {
        String feesPercentageStr = String.valueOf(feesPercentage);
        if (isRent) {
            this.leaseFees = feesPercentageStr;
            this.lTermFees = feesPercentageStr;
            this.mTermFees = feesPercentageStr;
            this.sTermFees = feesPercentageStr;
        } else {
            this.saleAgencyFees = feesPercentageStr;
        }
    }

    /**
     * Get the current agency fees percentage.
     */
    public Double getAgencyFees() {
        if (this.lTermFees != null) {
            return Double.valueOf(this.lTermFees);
        } else if (this.saleAgencyFees != null) {
            return Double.valueOf(this.saleAgencyFees);
        }
        return null;
    }

    /**
     * Clear all agency fees.
     */
    public void unsetAgencyFees() {
        this.leaseFees = null;
        this.lTermFees = null;
        this.mTermFees = null;
        this.sTermFees = null;
        this.saleAgencyFees = null;
    }

    // ============== OTHER GETTERS/SETTERS ==============

    public Integer getUtilCostVal() {
        return utilCostVal;
    }

    public void setUtilCostVal(Integer utilCostVal) {
        this.utilCostVal = utilCostVal;
    }

    public Double getLeaseTransferPrice() {
        return leaseTransferPrice;
    }

    public void setLeaseTransferPrice(Double leaseTransferPrice) {
        this.leaseTransferPrice = leaseTransferPrice;
    }

    public Boolean getIsNgtbl() {
        return isNgtbl;
    }

    public void setIsNgtbl(Boolean isNgtbl) {
        this.isNgtbl = isNgtbl;
    }

    public boolean isAutoFees() {
        return autoFees;
    }

    public void setAutoFees(boolean autoFees) {
        this.autoFees = autoFees;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getSaved() {
        return rSvd;
    }

    @JsonIgnore // For scraper listings this is ignored anyway as it should always be SAVED_COMPLETE
    @JsonProperty("saved")
    public SavedEnum getSavedEnum() {
        return rSvd != null ? SavedEnum.valueOf(rSvd) : null;
    }

    public void setSaved(SavedEnum savedEnum) {
        this.rSvd = savedEnum != null ? savedEnum.getValue() : null;
    }
}
