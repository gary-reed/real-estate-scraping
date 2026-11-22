package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Property;
import com.realestate.dialect.model.Property.FloorNumberEnum;
import com.realestate.dialect.model.ScrapingTaggingEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.realestate.dialect.parsing.UnitConversion;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import com.scraper.flavor.cms.parsing.text.TextSearchUtils;
import com.scraper.commons.utils.CommonUtils;

import java.util.List;

/**
 * Resolves missing Property fields from text.
 * Handles both simple keyword detection AND heavy number-near-word resolution
 * for: bedrooms, bathrooms, surface (m²/sqft), floor number, amenities, views.
 */
public class PropertyResolver extends TextResolver {

    public PropertyResolver(List<ParserLanguage> languages, NumbersAsText[] numberClasses) {
        super(languages, numberClasses);
    }

    @Override
    public String getSectionName() {
        return "Property";
    }

    @Override
    public RealEstateListing resolve(RealEstateListing listing, NormalizedText text) {
        if (text == null || text.isBlank()) return listing;

        Property property = listing.getProperty();
        if (property == null) {
            property = new Property();
            listing.setProperty(property);
        }

        String textStr = text.value();

        resolveBedrooms(property, textStr);
        resolveBathrooms(listing, property, textStr);
        resolveSurface(listing, property, textStr);
        resolveFloor(listing, property, textStr);
        resolveAmenities(property, textStr);
        resolvePropertyStatus(property, textStr);
        resolveView(property, textStr);

        guessBedroomsFromTotalRooms(property);

        return listing;
    }

    // ==================== Simple resolution ====================

    protected void resolveBedrooms(Property property, String text) {
        if (!isMissing(property.getBedrooms())) return;

        for (ParserLanguage lang : getLanguages()) {
            Double found = findClosestNumber(text, lang.bedroomKeywords());
            if (found != null && found >= 0 && found <= 30) {
                property.setBedrooms(found.intValue());
                logResolved("bedrooms", found.intValue(), lang.name());
                return;
            }
        }
    }

    /**
     * Resolves bathrooms from text. First tries simple keyword+number match,
     * then falls back to heavy stem-based resolution with sanity checks.
     */
    protected void resolveBathrooms(RealEstateListing listing, Property property, String text) {
        if (!isMissing(property.getBathrooms())) return;

        // Simple resolution: direct keyword + number match
        for (ParserLanguage lang : getLanguages()) {
            Double found = findClosestNumber(text, lang.bathroomKeywords());
            if (found != null && found >= 0 && found <= 20) {
                property.setBathrooms(found.intValue());
                logResolved("bathrooms", found.intValue(), lang.name());
                return;
            }
        }

        // Heavy resolution: stem-based with extended search and sanity checks
        NumbersAsText cardinals = getCardinals();
        if (cardinals == null) return;

        for (ParserLanguage lang : getLanguages()) {
            List<String> bathKw = lang.bathroomResolutionKeywords();
            List<String> bathPluralKw = lang.bathroomPluralKeywords();

            if (!containsAny(text, bathKw)) continue;

            Double resolved = findClosestNumberExtended(text, bathKw,
                    true, 4, null, null, cardinals);

            if (resolved != null) {
                Integer amtBdrs = property.getBedrooms();
                if (amtBdrs != null && resolved > amtBdrs) continue;
                if (resolved > 1 && !containsAny(text, bathPluralKw)) continue;
                if (containsAny(text, List.of("wc")) && resolved > 4) continue;

                property.setBathrooms(resolved.intValue());
                tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_BATHROOMS_FROM_DESC);
                logResolved("bathrooms", resolved.intValue(), lang.name() + " (heavy)");
                return;
            }
        }
    }

    /**
     * Resolves surface (m²) from text using language-specific area unit detection.
     * Iterates area units per language, finds the closest number to the matched pattern,
     * and converts to m² if the unit is imperial. Logs exactly which pattern matched.
     */
    protected void resolveSurface(RealEstateListing listing, Property property, String text) {
        if (!isMissing(property.getSurface())) return;

        for (ParserLanguage lang : getLanguages()) {
            for (ParserLanguage.AreaUnit unit : lang.areaUnits()) {
                String matchedPattern = unit.findInText(text);
                if (matchedPattern == null) continue;

                try {
                    // Find the closest number to the matched unit pattern.
                    // Exclude "terrac" contexts (terrace areas are not property surface).
                    Double rawValue = TextSearchUtils.getClosestNumberToWordOnText(
                            text, matchedPattern, null, "terrac", getNumberClasses());
                    if (rawValue == null || rawValue < 5 || rawValue > 100000) continue;

                    // Convert imperial to metric
                    double surface = unit.isImperial()
                            ? CommonUtils.round2Decimals(UnitConversion.sqFtToMetersSq(rawValue))
                            : rawValue;

                    property.setSurface((int) surface);
                    tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_SURFACE_FROM_DESC);
                    logResolved("surface", (int) surface, lang.name()
                            + " ('" + matchedPattern + "'"
                            + (unit.isImperial() ? ", raw " + rawValue.intValue() + " sqft" : "")
                            + ")");
                    return;

                } catch (Exception e) {
                    log.debug("{} {} Error resolving surface near '{}' in [{}]: {}",
                            TAG_TEXT_RESOLVE, getSectionName(), matchedPattern, getWhatText(), e.getMessage());
                }
            }
        }
    }

    /**
     * Resolves floor number from text. Combines simple keyword match, floor level
     * detection (top/high/low/ground), heavy ordinal-based resolution, and penthouse.
     */
    protected void resolveFloor(RealEstateListing listing, Property property, String text) {
        
        // --- Skip floor resolution for non-floor-related subtypes (e.g. houses, land)
        boolean isFloorRelated = listing.getBasic() != null
                && (listing.getBasic().isSubTypeFloorRelated()
                    || listing.getBasic().getPropertySubType() == null);
        if (!isFloorRelated) return;

        // --- Simple resolution: direct keyword + number match ---
        if (isMissing(property.getFloorNr())) {
            for (ParserLanguage lang : getLanguages()) {
                Double found = findClosestNumberIgnoringAreaUnits(
                        text, lang.floorKeywords(), lang, 3, getNumberClasses());
                if (found != null && found >= -1 && found <= 80) {
                    property.setFloorNr(found.intValue());
                    logResolved("floorNr", found.intValue(), lang.name());
                    break;
                }
            }
        }

        // --- Floor level detection (top/high/low/ground from adjacent words) ---
        // Fill-only: features (or an earlier pass) win. Skip the whole group when any floor level
        // is already set so description cannot re-log or accumulate conflicting levels.
        if (!property.doesPropAmenitiesHaveAnyFloorSet()) {
            for (ParserLanguage lang : getLanguages()) {
                List<String> floorKw = lang.floorResolutionKeywords()
                        .stream().map(String::trim).toList();
                if (!containsAny(text, floorKw)) continue;

                List<String> heatingKw = lang.floorHeatingKeywords();
                boolean isHeatingContext = false;
                for (String fkw : floorKw) {
                    if (TextSearchUtils.areWordsAdjacentInText(text, fkw, heatingKw.get(0))) {
                        isHeatingContext = true;
                        break;
                    }
                }
                if (isHeatingContext) continue;

                List<String> topKw = lang.topFloorKeywords();
                List<String> highKw = lang.highFloorKeywords();
                List<String> lowKw = lang.lowFloorKeywords();
                List<String> groundKw = lang.groundFloorKeywords();

                if (TextSearchUtils.areWordsAdjacentInText(text, floorKw, topKw)) {
                    property.setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum.FLOORNR_PENTHOUSE);
                    logResolved("floorLevel", "TOP/PENTHOUSE", lang.name());
                } else if (TextSearchUtils.areWordsAdjacentInText(text, floorKw, highKw)) {
                    property.setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum.FLOORNR_HIGH);
                    logResolved("floorLevel", "HIGH/UPPER", lang.name());
                } else if (TextSearchUtils.areWordsAdjacentInText(text, floorKw, lowKw)) {
                    property.setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum.FLOORNR_LOWER);
                    logResolved("floorLevel", "LOW", lang.name());
                } else if (TextSearchUtils.areWordsAdjacentInText(text, floorKw, groundKw)) {
                    property.setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum.FLOORNR_GROUND);
                    logResolved("floorLevel", "GROUND", lang.name());
                    // Floor number is left to the ordinal/heavy path
                    // so "55 m² en planta baja y planta primera" does not lock floorNr at 0.
                }
            }
        }

        // --- Penthouse detection ---
        Integer floorNr = property.getFloorNr();
        if (!property.containsAmenity(FloorNumberEnum.FLOORNR_PENTHOUSE)
                && (floorNr == null || floorNr > 2)) {
            for (ParserLanguage lang : getLanguages()) {
                if (containsAny(text, lang.penthouseKeywords())) {
                    property.addPropertyAmenity(FloorNumberEnum.FLOORNR_PENTHOUSE);
                    tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_PENTHOUSE_FROM_DESC);
                    logResolved("penthouse", true, lang.name());
                    //return;
                }
            }
        }

        // --- Heavy ordinal-based resolution ("3rd floor", "second floor") ---
        if (isMissing(property.getFloorNr())) {
            boolean isRetail = listing.getBasic().isCommercialRetail();
            if (!isRetail) {
                NumbersAsText ordinals = getOrdinals();
                NumbersAsText ordinalsWithNrs = getOrdinalsWithNrs();
                if (ordinals != null) {
                    for (ParserLanguage lang : getLanguages()) {
                        List<String> floorKw = lang.floorResolutionKeywords();
                        List<String> heatingKw = lang.floorHeatingKeywords();

                        if (!containsAny(text, floorKw)) continue;

                        boolean isHeatingContext = false;
                        for (String fkw : floorKw) {
                            if (TextSearchUtils.areWordsAdjacentInText(text, fkw.trim(), heatingKw.get(0))) {
                                isHeatingContext = true;
                                break;
                            }
                        }
                        if (isHeatingContext) continue;

                        NumbersAsText[] ordinalClasses = ordinalsWithNrs != null
                                ? new NumbersAsText[]{ordinals, ordinalsWithNrs}
                                : new NumbersAsText[]{ordinals};
                        Double resolvedFloor = findClosestNumberIgnoringAreaUnits(
                                text, floorKw, lang, 3, ordinalClasses);

                        if (resolvedFloor != null && (resolvedFloor % 1 == 0)) {
                            property.setFloorNr(resolvedFloor.intValue());
                            tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_FLOOR_FROM_DESC);
                            logResolved("floorNr", resolvedFloor.intValue(), lang.name() + " (heavy)");
                            return;
                        }

                        List<String> groundKw = lang.groundFloorKeywords();
                        if (TextSearchUtils.areWordsAdjacentInText(text, floorKw, groundKw)) {
                            property.setFloorNr(0);
                            property.setFloorNrAmenityIfNotAlreadySet(FloorNumberEnum.FLOORNR_GROUND);
                            logResolved("floorNr", 0, lang.name() + " (heavy ground)");
                            return;
                        }
                    }
                }
            }
        }

    }

    // ==================== Amenities and other simple resolution ====================

    protected void resolveAmenities(Property property, String text) {
        for (ParserLanguage lang : getLanguages()) {
            if (!property.containsAmenity(Property.PropertyAmenitiesEnum.BALCONY_TERRACE)
                    && containsAny(text, lang.balconyKeywords())) {
                property.addPropertyAmenity(Property.PropertyAmenitiesEnum.BALCONY_TERRACE);
                logResolved("amenity:BALCONY_TERRACE", true, lang.name());
            }
            if (!property.containsAmenity(Property.PropertyAmenitiesEnum.FIREPLACE)
                    && containsAny(text, lang.fireplaceKeywords())) {
                property.addPropertyAmenity(Property.PropertyAmenitiesEnum.FIREPLACE);
                logResolved("amenity:FIREPLACE", true, lang.name());
            }
            if (!property.containsAmenity(Property.PropertyAmenitiesEnum.UNDERFLOOR_HEATING)
                    && containsAny(text, lang.underfloorHeatingKeywords())) {
                property.addPropertyAmenity(Property.PropertyAmenitiesEnum.UNDERFLOOR_HEATING);
                logResolved("amenity:UNDERFLOOR_HEATING", true, lang.name());
            }
            if (!property.containsAmenity(Property.PropertyAmenitiesEnum.DOUBLE_GLAZING)
                    && containsAny(text, lang.doubleGlazingKeywords())) {
                property.addPropertyAmenity(Property.PropertyAmenitiesEnum.DOUBLE_GLAZING);
                logResolved("amenity:DOUBLE_GLAZING", true, lang.name());
            }
            if (!property.containsAmenity(Property.PropertyAmenitiesEnum.ALARM)
                    && containsAny(text, lang.alarmKeywords())) {
                property.addPropertyAmenity(Property.PropertyAmenitiesEnum.ALARM);
                logResolved("amenity:ALARM", true, lang.name());
            }
            if (!property.containsAmenity(Property.PropertyAmenitiesEnum.INTERPHONE)
                    && containsAny(text, lang.interphoneKeywords())) {
                property.addPropertyAmenity(Property.PropertyAmenitiesEnum.INTERPHONE);
                logResolved("amenity:INTERPHONE", true, lang.name());
            }
        }
    }

    protected void resolvePropertyStatus(Property property, String text) {
        if (property.getPropertyStatus() != null) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.newBuildKeywords())) {
                property.setPropertyStatus(Property.PropertyStatusEnum.NEW_BUILD);
                logResolved("propertyStatus", "NEW_BUILD", lang.name());
                return;
            }
            if (containsAny(text, lang.renovatedKeywords())) {
                property.setPropertyStatus(Property.PropertyStatusEnum.RENOVATED);
                logResolved("propertyStatus", "RENOVATED", lang.name());
                return;
            }
            if (containsAny(text, lang.goodConditionKeywords())) {
                property.setPropertyStatus(Property.PropertyStatusEnum.GOOD_CONDITION);
                logResolved("propertyStatus", "GOOD_CONDITION", lang.name());
                return;
            }
        }
    }

    protected void resolveView(Property property, String text) {
        if (property.getView() != null) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.seaViewKeywords())) {
                property.setView(Property.ViewEnum.SEA_VIEW);
                logResolved("view", "SEA_VIEW", lang.name());
                return;
            }
            if (containsAny(text, lang.monumentViewKeywords())) {
                property.setView(Property.ViewEnum.MONUMENT_VIEW);
                logResolved("view", "MONUMENT_VIEW", lang.name());
                return;
            }
            if (containsAny(text, lang.mountainViewKeywords())) {
                property.setView(Property.ViewEnum.MOUNTAIN_VIEW);
                logResolved("view", "MOUNTAIN_VIEW", lang.name());
                return;
            }
        }
    }

    protected void guessBedroomsFromTotalRooms(Property property) {
        if (!isMissing(property.getBedrooms())) return;

        Integer totalRooms = property.getRoomsTotal();
        if (totalRooms == null) return;

        if (totalRooms == 1) {
            property.setBedrooms(0);
            logResolved("bedrooms", 0, "guessed from totalRooms=1 (studio)");
        } else {
            double bdrms = totalRooms / 1.8;
            Double surface = property.getSurface() != null ? property.getSurface().doubleValue() : null;
            if (surface != null && surface > 120)
                bdrms = bdrms + 0.5;
            property.setBedrooms((int) Math.round(bdrms));
            logResolved("bedrooms", (int) Math.round(bdrms), "guessed from totalRooms=" + totalRooms);
        }
    }

}
