package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.scraper.flavor.cms.model.Media;
import com.scraper.flavor.cms.model._ListingDetails;

import java.util.EnumSet;
import java.util.HashSet;

/**
 * A property listing: the generic {@link _ListingDetails} plus the real-estate fields
 * (operation, property, building, furnishing, transaction, agency contact, shop display).
 *
 * <p>JSON names match the historical {@code details_json} document, so a serialized
 * {@code RealEstateListing} is the same object the warehouse already stores.</p>
 */
public class RealEstateListing extends _ListingDetails {

    /**
     * Whether the listing was published on this site ({@code external}) or created inside our own system
     * ({@code internal}). Scraped rows are {@code external}. JSON name stays {@code annType}.
     */
    public enum AnnounceTypeEnum implements SerializableEnum {
        INTERNAL("internal", 1),
        EXTERNAL("external", 2);

        private static final Map<Integer, AnnounceTypeEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        AnnounceTypeEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static AnnounceTypeEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    @JsonProperty("annType")
    private AnnounceTypeEnum annType;

    @JsonProperty("prjctId")
    private String prjctId;

    @JsonProperty("prjctUrl")
    private String prjctUrl;

    @JsonProperty("tstSeed")
    private Integer tstSeed;

    @JsonProperty("perToList")
    private Integer perToList;

    @JsonProperty("perToCmplt")
    private Integer perToCmplt;

    @JsonProperty("lngSpkn")
    private EnumSet<SpokenLanguagesEnum> lngSpkn;

    @JsonProperty("contactExternal")
    private ContactExternal contactExternal;

    @JsonProperty("display")
    private Display display;

    @JsonProperty("basic")
    private Basic basic;

    @JsonProperty("building")
    private Building building;

    @JsonProperty("property")
    private Property property;

    @JsonProperty("interior")
    private Interior interior;

    @JsonProperty("furnish")
    private Furnish furnish;

    @JsonProperty("transaction")
    private Transaction transaction;

    @JsonProperty("flatMate")
    private FlatMate flatMate;


    /**
     * Languages spoken by the people attached to a listing.
     */
    public enum SpokenLanguagesEnum implements SerializableEnum {
        ENGLISH("english", 1),
        FRENCH("french", 2),
        SPANISH("spanish", 3),
        GERMAN("german", 4),
        ITALIAN("italian", 5),
        PORTUGUESE("portuguese", 6),
        POLISH("polish", 7),
        CHINESE("chinese", 8),
        ARABIC("arabic", 9),
        HINDI("hindi", 10);

        private static final Map<Integer, SpokenLanguagesEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

        private final String name;
        private final int value;

        SpokenLanguagesEnum(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getValue() { return value; }

        public static SpokenLanguagesEnum valueOf(int value) { return BY_VALUE.get(value); }
    }

    public RealEstateListing() {
    }

    public RealEstateListing(String listingId) {
        super(listingId);
    }

    /**
     * Price per m², weighted toward the land when the plot is large relative to the building.
     */
    public void calculateAndSetPriceRatio() {
        double proport = 0.5;

        if (basic != null) {
            basic.priceRatio = null;
        }

        if (basic == null || property == null) return;

        Double price = basic.getPrice();
        Double surface = property.getSurface() != null ? property.getSurface().doubleValue() : null;
        if (price == null || surface == null || surface < 1) return;

        if (building != null && building.getLandSz() != null && building.getLandSz() > 0) {
            int landSz = building.getLandSz();

            if (surface < 25) {
                basic.priceRatio = price / (surface + landSz);
                return;
            }

            if (landSz <= surface) {
                basic.priceRatio = price / surface;
                return;
            }

            double y = 100 / ((landSz / (surface * 70)) + 1);
            proport = y / 100;

            double propRatio = price * proport / surface;
            double landRatio = price * (1 - proport) / landSz;
            basic.priceRatio = (propRatio * proport) + (landRatio * (1 - proport));
        } else {
            basic.priceRatio = price / surface;
        }

        if (basic.priceRatio != null) {
            basic.priceRatio = Math.round(basic.priceRatio * 100.0) / 100.0;
        }
    }

    public Double calculatePriceRatioForGivenValues(Double price, Integer surface, Integer landSz) {
        double proport = 0.5;

        if (price == null || surface == null || surface < 1) return null;

        if (landSz != null && landSz > 0) {
            if (surface < 25) return price / (surface + landSz);
            if (landSz <= surface) return price / surface;

            double y = 100 / ((landSz / (surface * 70.0)) + 1);
            proport = y / 100;

            double propRatio = price * proport / surface;
            double landRatio = price * (1 - proport) / landSz;
            return (propRatio * proport) + (landRatio * (1 - proport));
        } else {
            return price / surface;
        }
    }

    public void calculateAndSetFloorNrAmenity() {
        if (property == null) return;

        Integer floorNr = property.getFloorNr();
        Integer bHeight = building != null ? building.getHeight() : null;

        boolean isSetPenthouse = false;
        HashSet<Enum> amenities = property.getAmenities();
        if (amenities != null) {
            isSetPenthouse = amenities.contains(Property.FloorNumberEnum.FLOORNR_PENTHOUSE);
        }

        Property.FloorNumberEnum resolved = Property.FloorNumberEnum.resolveFloorNrValue(floorNr, bHeight, isSetPenthouse);
        property.setFloorNrAmenityIfNotAlreadySet(resolved);
    }

    /** Term price for rent and share. Buy listings have no term price. */
    public void resolveAndSetTopTermPrice() {
        if (basic == null || basic.getOperation() == null || basic.getPrice() == null) {
            return;
        }

        Basic.OperationEnum operation = basic.getOperation();
        if (Basic.OperationEnum.BUY.equals(operation)) {
            return;
        }

        Basic.TermEnum topTerm = basic.getTopTermEnum();
        if (topTerm == null) {
            basic.setTopTerm(Basic.TermEnum.LONG_TERM);
            topTerm = Basic.TermEnum.LONG_TERM;
        }

        if (transaction == null) {
            transaction = new Transaction();
        }

        Double price = basic.getPrice();
        switch (topTerm) {
            case LONG_TERM -> transaction.setlTerm(price);
            case MID_TERM -> transaction.setmTerm(price);
            case SHORT_TERM -> transaction.setsTerm(price);
            case LEASE -> transaction.setLease(price);
        }
    }

    @JsonIgnore
    public boolean isProjectInConstruction() {
        return building != null && building.getBuildingAge() != null && building.getBuildingAge() > 9999;
    }

    public AnnounceTypeEnum getAnnType() { return annType; }
    public void setAnnType(AnnounceTypeEnum annType) { this.annType = annType; }

    /** Floor plan and virtual tour live on {@link RealEstateMedia}. A plain {@link Media} is wrapped. */
    @Override
    public RealEstateMedia getMedia() {
        Media media = super.getMedia();
        if (media == null || media instanceof RealEstateMedia) {
            return (RealEstateMedia) media;
        }
        RealEstateMedia copy = new RealEstateMedia();
        copy.setMediaCount(media.getMediaCount());
        copy.setImagesArray(media.getImagesArray());
        copy.setThumbImagesArray(media.getThumbImagesArray());
        copy.setVideoUrl(media.getVideoUrl());
        copy.setMediaSaved(media.getMediaSaved());
        super.setMedia(copy);
        return copy;
    }

    public String getPrjctId() { return prjctId; }
    public void setPrjctId(String prjctId) { this.prjctId = prjctId; }

    public String getPrjctUrl() { return prjctUrl; }
    public void setPrjctUrl(String prjctUrl) { this.prjctUrl = prjctUrl; }

    public Integer getTstSeed() { return tstSeed; }
    public void setTstSeed(Integer tstSeed) { this.tstSeed = tstSeed; }

    public Integer getPerToList() { return perToList; }
    public void setPerToList(Integer perToList) { this.perToList = perToList; }

    public Integer getPerToCmplt() { return perToCmplt; }
    public void setPerToCmplt(Integer perToCmplt) { this.perToCmplt = perToCmplt; }

    public ContactExternal getContactExternal() { return contactExternal; }
    public void setContactExternal(ContactExternal contactExternal) { this.contactExternal = contactExternal; }

    public Display getDisplay() { return display; }
    public void setDisplay(Display display) { this.display = display; }

    public EnumSet<SpokenLanguagesEnum> getLngSpkn() { return lngSpkn; }
    public void addSpokenLanguage(SpokenLanguagesEnum language) {
        if (lngSpkn == null) lngSpkn = EnumSet.noneOf(SpokenLanguagesEnum.class);
        lngSpkn.add(language);
    }

    public Basic getBasic() { return basic; }
    public void setBasic(Basic basic) { this.basic = basic; }

    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }

    public Property getProperty() { return property; }
    public void setProperty(Property property) { this.property = property; }

    public Interior getInterior() { return interior; }
    public void setInterior(Interior interior) { this.interior = interior; }

    public Furnish getFurnish() { return furnish; }
    public void setFurnish(Furnish furnish) { this.furnish = furnish; }

    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }

    public FlatMate getFlatMate() { return flatMate; }
    public void setFlatMate(FlatMate flatMate) { this.flatMate = flatMate; }

}
