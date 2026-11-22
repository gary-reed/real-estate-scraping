package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Furnish;
import com.realestate.dialect.model.Furnish.FurnishingTypeEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.scraper.flavor.cms.parsing.text.NormalizedText;

import java.util.EnumSet;
import java.util.List;

/**
 * Resolves missing Furnish fields from text.
 * Focuses on: furnishing type, heating system, amenities, appliances.
 */
public class FurnishResolver extends TextResolver {

    public FurnishResolver(List<ParserLanguage> languages, NumbersAsText[] numberClasses) {
        super(languages, numberClasses);
    }

    @Override
    public String getSectionName() {
        return "Furnish";
    }

    @Override
    public RealEstateListing resolve(RealEstateListing listing, NormalizedText text) {
        if (text == null || text.isBlank()) return listing;

        Furnish furnish = listing.getFurnish();
        if (furnish == null) {
            furnish = new Furnish();
            listing.setFurnish(furnish);
        }

        String textStr = text.value();

        resolveFurnishingType(furnish, textStr);
        resolveHeating(furnish, textStr);
        resolveAmenities(furnish, textStr);
        resolveAppliances(furnish, textStr);

        return listing;
    }

    protected void resolveFurnishingType(Furnish furnish, String text) {
        FurnishingTypeEnum current = furnish.getFurnishType();
        if (current != null && current != FurnishingTypeEnum.UNDEFINED) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.semiFurnishedKeywords())) {
                furnish.setFurnishType(FurnishingTypeEnum.SEMI_FURNISHED);
                logResolved("furnishingType", "SEMI_FURNISHED", lang.name());
                return;
            }

            // Furnished keywords use |word-boundary| where needed (e.g. ENGLISH "|furnished")
            // so "unfurnished" does not also count as furnished.
            boolean unfurnished = containsAny(text, lang.unfurnishedKeywords());
            boolean furnished = containsAny(text, lang.furnishedKeywords());

            if (unfurnished && furnished) {
                // Flexible / both offered - use FURNISHED as default
                furnish.setFurnishType(FurnishingTypeEnum.FURNISHED);
                log.debug("{} {} skip 'furnishingType' — both furnished and unfurnished in [{}] ({})",
                        TAG_TEXT_RESOLVE, getSectionName(), getWhatText(), lang.name());
                return;
            }
            if (unfurnished) {
                furnish.setFurnishType(FurnishingTypeEnum.UNFURNISHED);
                logResolved("furnishingType", "UNFURNISHED", lang.name());
                return;
            }
            if (furnished) {
                furnish.setFurnishType(FurnishingTypeEnum.FURNISHED);
                logResolved("furnishingType", "FURNISHED", lang.name());
                return;
            }
        }
    }

    protected void resolveHeating(Furnish furnish, String text) {
        if (!isMissing(furnish.getHeatingSystem())) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.underfloorHeatingKeywords())) {
                furnish.setHeatingSystem(Furnish.HeatingSystemEnum.UNDERFLOOR);
                logResolved("heatingSystem", "UNDERFLOOR", lang.name());
                return;
            }
            if (containsAny(text, lang.gasHeatingKeywords())) {
                furnish.setHeatingSystem(Furnish.HeatingSystemEnum.GAS);
                logResolved("heatingSystem", "GAS", lang.name());
                return;
            }
            if (containsAny(text, lang.electricHeatingKeywords())) {
                furnish.setHeatingSystem(Furnish.HeatingSystemEnum.ELECTRIC);
                logResolved("heatingSystem", "ELECTRIC", lang.name());
                return;
            }
            if (containsAny(text, lang.collectiveHeatingKeywords())) {
                furnish.setHeatingSystem(Furnish.HeatingSystemEnum.COLLECTIVE);
                logResolved("heatingSystem", "COLLECTIVE", lang.name());
                return;
            }
            if (containsAny(text, lang.centralHeatingKeywords())) {
                furnish.setHeatingSystem(Furnish.HeatingSystemEnum.CENTRAL);
                logResolved("heatingSystem", "CENTRAL", lang.name());
                return;
            }
        }
    }

    protected void resolveAmenities(Furnish furnish, String text) {
        for (ParserLanguage lang : getLanguages()) {
            EnumSet<Furnish.FurnishAmenitiesEnum> amenities = furnish.getFurnishAmenities();

            if ((amenities == null || !amenities.contains(Furnish.FurnishAmenitiesEnum.AIR_CONDITIONER))
                    && containsAny(text, lang.airConditioningKeywords())) {
                furnish.addFurnishAmenity(Furnish.FurnishAmenitiesEnum.AIR_CONDITIONER);
                logResolved("amenity:AIR_CONDITIONER", true, lang.name());
            }
            if ((amenities == null || !amenities.contains(Furnish.FurnishAmenitiesEnum.INTERNET))
                    && containsAny(text, lang.internetKeywords())) {
                furnish.addFurnishAmenity(Furnish.FurnishAmenitiesEnum.INTERNET);
                logResolved("amenity:INTERNET", true, lang.name());
            }
            if ((amenities == null || !amenities.contains(Furnish.FurnishAmenitiesEnum.DISHWASHER))
                    && containsAny(text, lang.dishwasherKeywords())) {
                furnish.addFurnishAmenity(Furnish.FurnishAmenitiesEnum.DISHWASHER);
                logResolved("amenity:DISHWASHER", true, lang.name());
            }
            if ((amenities == null || !amenities.contains(Furnish.FurnishAmenitiesEnum.FREEZER))
                    && containsAny(text, lang.freezerKeywords())) {
                furnish.addFurnishAmenity(Furnish.FurnishAmenitiesEnum.FREEZER);
                logResolved("amenity:FREEZER", true, lang.name());
            }
            if ((amenities == null || !amenities.contains(Furnish.FurnishAmenitiesEnum.OVEN))
                    && containsAny(text, lang.ovenKeywords())) {
                furnish.addFurnishAmenity(Furnish.FurnishAmenitiesEnum.OVEN);
                logResolved("amenity:OVEN", true, lang.name());
            }
            if ((amenities == null || !amenities.contains(Furnish.FurnishAmenitiesEnum.WASHING_MACHINE))
                    && containsAny(text, lang.washingMachineKeywords())) {
                furnish.addFurnishAmenity(Furnish.FurnishAmenitiesEnum.WASHING_MACHINE);
                logResolved("amenity:WASHING_MACHINE", true, lang.name());
            }
        }
    }

    protected void resolveAppliances(Furnish furnish, String text) {
        for (ParserLanguage lang : getLanguages()) {
            if (furnish.getBasicAppliances() == null
                    || !furnish.getBasicAppliances().contains(Furnish.BasicAppliancesEnum.REFRIGERATOR)) {
                if (containsAny(text, lang.fridgeKeywords())) {
                    furnish.addBasicAppliance(Furnish.BasicAppliancesEnum.REFRIGERATOR);
                    logResolved("appliance:REFRIGERATOR", true, lang.name());
                }
            }
        }
    }
}
