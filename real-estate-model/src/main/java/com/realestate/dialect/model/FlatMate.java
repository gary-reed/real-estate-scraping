package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.EnumSet;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Flatmate/roommate details for shared accommodation.
 * Migrated from L_Commons FlatMate.java
 */
public class FlatMate {

    // ============== NESTED ENUMS ==============

    /**
     * Flatmate extra preferences.
     * These refer to what already exists in the flatmate situation, not what the user is looking for. 
     * For example, "have_kids" means the current flatmates have kids, not that the user wants flatmates with kids.
     */
    public enum FlatmateExtraEnum implements SerializableEnum {
        COUPLE("couple", 1),
        HAVE_KIDS("have_kids", 2),
        LIVE_WITH_LANDLORD("live_with_landlord", 3),
        HAVE_PETS("have_pets", 4),
        LANDLORD("landlord", 5),

        ;

        private static final Map<Integer, FlatmateExtraEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        FlatmateExtraEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static FlatmateExtraEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Gender preference.
     * These refer to what gender/s are allowed for the new flatmate.
     */
    public enum AllowedGenderEnum implements SerializableEnum {
        ACCEPT_MALE("accept_male", 1),
        ACCEPT_FEMALE("accept_female", 2),
        ACCEPT_COUPLE("accept_couple", 3),
        ;

        private static final Map<Integer, AllowedGenderEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        AllowedGenderEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static AllowedGenderEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    /**
     * Occupation type.
     */
    public enum AllowedOccupationEnum implements SerializableEnum {
        ACCEPT_PROFESSIONAL("accept_professional", 1),
        ACCEPT_STUDENT("accept_student", 2),
        ACCEPT_TRAVELLER("accept_traveller", 3),
        ACCEPT_RETIREE("accept_retiree", 4),
        ;

        private static final Map<Integer, AllowedOccupationEnum> BY_VALUE = SerializableEnum.buildValueMap(values());
        
        private final String name;
        private final int value;

        AllowedOccupationEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }
        
        @Override
        public int getValue() { return value; }
        
        public static AllowedOccupationEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    // ============== FIELDS ==============

    @SerializedName("flatMateSaved")
    @Expose
    @JsonIgnore
    private Integer flatMateSaved;

    // Lifestyle ratings (1-5 scale)
    @SerializedName("privacy")
    @Expose
    private Integer privacy;

    @SerializedName("eating")
    @Expose
    private Integer eating;

    @SerializedName("guest")
    @Expose
    private Integer guest;

    @SerializedName("sharing")
    @Expose
    private Integer sharing;

    @SerializedName("drinking")
    @Expose
    private Integer drinking;

    @SerializedName("drugUse")
    @Expose
    private Integer drugUse;

    @SerializedName("smoking")
    @Expose
    private Integer smoking;

    @SerializedName("dayschedule")
    @Expose
    private Integer dayschedule;

    @SerializedName("sleepschedule")
    @Expose
    private Integer sleepschedule;

    @SerializedName("cleanliness")
    @Expose
    private Integer cleanliness;

    @SerializedName("flatmateExtra")
    @Expose
    private EnumSet<FlatmateExtraEnum> flatmateExtra;

    @SerializedName("flatmatesGender")
    @Expose
    @JsonIgnore
    private Integer flatmatesGender; // GenderEnum

    @SerializedName("occupation")
    @Expose
    @JsonIgnore
    private Integer occupation; // OccupationEnum

    @SerializedName("residentAmount")
    @Expose
    private Integer residentAmount;

    @SerializedName("flatMatesAvgAge")
    @Expose
    private Integer flatMatesAvgAge;

    // ============== FLATMATE EXTRA METHODS ==============

    public EnumSet<FlatmateExtraEnum> getFlatmateExtra() {
        return flatmateExtra;
    }

    public void addFlatmateExtra(FlatmateExtraEnum flatmateExtraItem) {
        if (this.flatmateExtra == null) {
            this.flatmateExtra = EnumSet.noneOf(FlatmateExtraEnum.class);
        }
        this.flatmateExtra.add(flatmateExtraItem);
    }

    // ============== GENDER METHODS ==============

    @JsonIgnore
    public Integer getFlatmatesGender() {
        return flatmatesGender;
    }

    @JsonProperty("flatmatesGender")
    public AllowedGenderEnum getFlatmatesGenderEnum() {
        return flatmatesGender != null ? AllowedGenderEnum.valueOf(flatmatesGender) : null;
    }

    public void setFlatmatesGender(AllowedGenderEnum gender) {
        this.flatmatesGender = gender != null ? gender.getValue() : null;
    }

    // ============== OCCUPATION METHODS ==============

    @JsonIgnore
    public Integer getOccupation() {
        return occupation;
    }

    @JsonProperty("occupation")
    public AllowedOccupationEnum getOccupationEnum() {
        return occupation != null ? AllowedOccupationEnum.valueOf(occupation) : null;
    }

    public void setOccupation(AllowedOccupationEnum occ) {
        this.occupation = occ != null ? occ.getValue() : null;
    }

    // ============== LIFESTYLE GETTERS/SETTERS ==============

    public Integer getPrivacy() {
        return privacy;
    }

    public void setPrivacy(Integer privacy) {
        this.privacy = privacy;
    }

    public Integer getEating() {
        return eating;
    }

    public void setEating(Integer eating) {
        this.eating = eating;
    }

    public Integer getGuest() {
        return guest;
    }

    public void setGuest(Integer guest) {
        this.guest = guest;
    }

    public Integer getSharing() {
        return sharing;
    }

    public void setSharing(Integer sharing) {
        this.sharing = sharing;
    }

    public Integer getDrinking() {
        return drinking;
    }

    public void setDrinking(Integer drinking) {
        this.drinking = drinking;
    }

    public Integer getDrugUse() {
        return drugUse;
    }

    public void setDrugUse(Integer drugUse) {
        this.drugUse = drugUse;
    }

    public Integer getSmoking() {
        return smoking;
    }

    public void setSmoking(Integer smoking) {
        this.smoking = smoking;
    }

    public Integer getDayschedule() {
        return dayschedule;
    }

    public void setDayschedule(Integer dayschedule) {
        this.dayschedule = dayschedule;
    }

    public Integer getSleepschedule() {
        return sleepschedule;
    }

    public void setSleepschedule(Integer sleepschedule) {
        this.sleepschedule = sleepschedule;
    }

    public Integer getCleanliness() {
        return cleanliness;
    }

    public void setCleanliness(Integer cleanliness) {
        this.cleanliness = cleanliness;
    }

    // ============== OTHER GETTERS/SETTERS ==============

    public Integer getResidentAmount() {
        return residentAmount;
    }

    public void setResidentAmount(Integer residentAmount) {
        this.residentAmount = residentAmount;
    }

    public Integer getFlatMatesAvgAge() {
        return flatMatesAvgAge;
    }

    public void setFlatMatesAvgAge(Integer flatMatesAvgAge) {
        this.flatMatesAvgAge = flatMatesAvgAge;
    }

    // ============== SAVED STATE ==============

    @JsonIgnore
    public Integer getFlatMateSaved() {
        return flatMateSaved;
    }

    public void setFlatMateSaved(Integer flatMateSaved) {
        this.flatMateSaved = flatMateSaved;
    }
}
