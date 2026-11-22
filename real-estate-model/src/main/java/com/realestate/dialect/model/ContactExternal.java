package com.realestate.dialect.model;

import java.util.Map;
import com.scraper.commons.core.model.SerializableEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * External listing details (agency info, source site, etc.).
 * Real-estate dialect: who published the listing on the source site (agency or private seller).
 */
public class ContactExternal {

    /**
     * Who published the listing (a private person, an agency, a developer).
     */
    public enum UserTypeEnum implements SerializableEnum {
        PRIVATE("private", 1),
        AGENCY("agency", 2),
        DEVELOPER("developer", 3);

        private static final Map<Integer, UserTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        UserTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static UserTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }


    @JsonProperty("usrType")
    @Expose
    private UserTypeEnum usrType;

    @SerializedName("contactName")
    @Expose
    private String contactName;

    @SerializedName("agencyName")
    @Expose
    private String agencyName;

    @SerializedName("agencyLogo")
    @Expose
    private String agencyLogo;

    @SerializedName("agencyUrl")
    @Expose
    private String agencyUrl;

    @SerializedName("extPhone")
    @Expose
    private String extPhone;

    @SerializedName("extEmail")
    @Expose
    private String extEmail;

    @SerializedName("extRefId")
    @Expose
    private String extRefId;


    // ============== GETTERS/SETTERS ==============
    public UserTypeEnum getUsrType() {
        return usrType;
    }
    public void setUsrType(UserTypeEnum usrType) {
        this.usrType = usrType;
    }
    public String getContactName() {
        return contactName;
    }
    public void setContactName(String contactName) {
        this.contactName = contactName;
    }
    public String getAgencyName() {
        return agencyName;
    }
    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }
    public String getAgencyLogo() {
        return agencyLogo;
    }
    public void setAgencyLogo(String agencyLogo) {
        this.agencyLogo = agencyLogo;
    }
    public String getAgencyUrl() {
        return agencyUrl;
    }
    public void setAgencyUrl(String agencyUrl) {
        this.agencyUrl = agencyUrl;
    }
    public String getExtEmail() {
        return extEmail;
    }
    public void setExtEmail(String extEmail) {
        this.extEmail = extEmail;
    }
    public String getExtPhone() {
        return extPhone;
    }
    public void setExtPhone(String extPhone) {
        this.extPhone = extPhone;
    }
    public String getExtRefId() {
        return extRefId;
    }
    public void setExtRefId(String extRefId) {
        this.extRefId = extRefId;
    }

}
