package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Basic.SubTypeEnum;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.ScrapingTaggingEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.realestate.dialect.parsing.UnitConversion;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import com.scraper.flavor.cms.parsing.text.TextSearchUtils;

import java.util.EnumSet;
import java.util.List;

/**
 * Resolves missing Building fields from text.
 * Handles both simple keyword detection AND heavy number-near-word resolution
 * for: parking, facilities (elevator, pool, garden, etc.), land size, building height, storeys.
 */
public class BuildingResolver extends TextResolver {

    public BuildingResolver(List<ParserLanguage> languages, NumbersAsText[] numberClasses) {
        super(languages, numberClasses);
    }

    @Override
    public String getSectionName() {
        return "Building";
    }

    @Override
    public RealEstateListing resolve(RealEstateListing listing, NormalizedText text) {
        if (text == null || text.isBlank()) return listing;

        Building building = listing.getBuilding();
        if (building == null) {
            building = new Building();
            listing.setBuilding(building);
        }

        String textStr = text.value();

        resolveParking(building, textStr);
        resolveFacilities(listing, building, textStr);
        resolveStoreys(listing, building, textStr);
        resolveLand(listing, building, textStr);
        resolveBuildingHeight(listing, building, textStr);
        resolveBuildingAge(listing, building, textStr);

        return listing;
    }

    // ==================== Simple resolution ====================

    protected void resolveParking(Building building, String text) {
        if (!isMissing(building.getParkingCount())) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.parkingKeywords())) {
                Double found = findClosestNumber(text, lang.parkingKeywords());
                if (found != null && found >= 0 && found <= 50) {
                    building.setParkingCount(found.intValue());
                    logResolved("parkingCount", found.intValue(), lang.name());
                } else {
                    building.setParkingCount(1);
                    logResolved("parkingCount", 1, lang.name() + " (keyword detected, default 1)");
                }
                return;
            }
        }
    }

    protected void resolveFacilities(RealEstateListing listing, Building building, String text) {
        boolean isFloorRelated = listing.getBasic() != null && listing.getBasic().isSubTypeFloorRelated();

        for (ParserLanguage lang : getLanguages()) {
            EnumSet<Building.BuildingFacilitiesEnum> facils = building.getBuildingFacilities();

            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.ELEVATOR))
                    && containsAny(text, lang.elevatorKeywords())) {
                building.addBuildingFacility(Building.BuildingFacilitiesEnum.ELEVATOR);
                logResolved("facility:ELEVATOR", true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.POOL_PRIVATE))
                    && (facils == null || !facils.contains(Building.BuildingFacilitiesEnum.POOL_SHARED))
                    && containsAny(text, lang.poolKeywords())) {
                Building.BuildingFacilitiesEnum poolType = resolvePrivateOrShared(text, lang, isFloorRelated,
                        lang.poolKeywords(),
                        Building.BuildingFacilitiesEnum.POOL_PRIVATE, Building.BuildingFacilitiesEnum.POOL_SHARED);
                building.addBuildingFacility(poolType);
                logResolved("facility:" + poolType.getName(), true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.GARDEN_PRIVATE))
                    && (facils == null || !facils.contains(Building.BuildingFacilitiesEnum.GARDEN_SHARED))
                    && containsAny(text, lang.gardenKeywords())) {
                Building.BuildingFacilitiesEnum gardenType = resolvePrivateOrShared(text, lang, isFloorRelated,
                        lang.gardenKeywords(),
                        Building.BuildingFacilitiesEnum.GARDEN_PRIVATE, Building.BuildingFacilitiesEnum.GARDEN_SHARED);
                building.addBuildingFacility(gardenType);
                logResolved("facility:" + gardenType.getName(), true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.PARKING))
                    && containsAny(text, lang.parkingKeywords())) {
                building.addBuildingFacility(Building.BuildingFacilitiesEnum.PARKING);
                logResolved("facility:PARKING", true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.CAVE))
                    && containsAny(text, lang.cellarKeywords())) {
                building.addBuildingFacility(Building.BuildingFacilitiesEnum.CAVE);
                logResolved("facility:CAVE", true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.CONCIERGE))
                    && containsAny(text, lang.conciergeKeywords())) {
                building.addBuildingFacility(Building.BuildingFacilitiesEnum.CONCIERGE);
                logResolved("facility:CONCIERGE", true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.GYM))
                    && containsAny(text, lang.gymKeywords())) {
                building.addBuildingFacility(Building.BuildingFacilitiesEnum.GYM);
                logResolved("facility:GYM", true, lang.name());
            }
            if ((facils == null || !facils.contains(Building.BuildingFacilitiesEnum.EVENT_ROOM))
                    && isFloorRelated
                    && containsAny(text, lang.eventRoomKeywords())) {
                building.addBuildingFacility(Building.BuildingFacilitiesEnum.EVENT_ROOM);
                logResolved("facility:EVENT_ROOM", true, lang.name());
            }
        }
    }

    private Building.BuildingFacilitiesEnum resolvePrivateOrShared(String text, ParserLanguage lang,
            boolean isFloorRelated, List<String> facilityKw,
            Building.BuildingFacilitiesEnum privateEnum, Building.BuildingFacilitiesEnum sharedEnum) {

        List<String> privateKw = lang.privateKeywords();
        List<String> sharedKw = lang.sharedKeywords();

        if (TextSearchUtils.areWordsAdjacentInText(text, facilityKw, privateKw))
            return privateEnum;
        if (TextSearchUtils.areWordsAdjacentInText(text, facilityKw, sharedKw))
            return sharedEnum;

        return isFloorRelated ? sharedEnum : privateEnum;
    }

    /**
     * Resolves storeys/height for house-like properties. First tries simple keyword+number,
     * then falls back to heavy resolution with plural validation and 3-storey cap.
     */
    protected void resolveStoreys(RealEstateListing listing, Building building, String text) {
        if (building.getHeight() != null) return;
        if (!listing.getBasic().isSubTypeHouseLike()
                && listing.getBasic().getPropertySubType() != null) return;

        // Simple resolution: direct keyword + number match
        for (ParserLanguage lang : getLanguages()) {
            List<String> storeysKw = lang.storeysKeywords();
            if (!containsAny(text, storeysKw)) continue;

            Double found = findClosestNumberIgnoringAreaUnits(
                    text, storeysKw, lang, 3, getNumberClasses());
            if (found != null && found >= 1 && found <= 5) {
                building.setHeight(found.intValue());
                logResolved("storeys/height", found.intValue(), lang.name());
                return;
            }
        }

        // Heavy resolution: extended search with plural validation
        boolean isRetail = listing.getBasic().isCommercialRetail();
        if (isRetail) return;
        if (!listing.getBasic().isSubTypeHouseLike()) return;

        NumbersAsText cardinals = getCardinals();
        NumbersAsText ordinalsWithNrs = getOrdinalsWithNrs();
        if (cardinals == null) return;

        for (ParserLanguage lang : getLanguages()) {
            List<String> floorKw = lang.floorResolutionKeywords();
            List<String> storeysKw = lang.storeysKeywords();
            List<String> heatingKw = lang.floorHeatingKeywords();
            List<String> storeysPluralKw = lang.storeysPluralKeywords();
            List<String> heightKw = lang.buildingHeightKeywords();

            boolean hasFloor = containsAny(text, floorKw);
            boolean hasStorey = containsAny(text, storeysKw);
            if (!hasFloor && !hasStorey) continue;

            // Skip "underfloor heating" contexts
            if (hasFloor) {
                boolean isHeating = false;
                for (String fkw : floorKw) {
                    if (TextSearchUtils.areWordsAdjacentInText(text, fkw.trim(), heatingKw.get(0))) {
                        isHeating = true;
                        break;
                    }
                }
                if (isHeating) continue;
            }

            List<String> keywords = new java.util.ArrayList<>();
            if (hasStorey) keywords.addAll(storeysKw);
            if (hasFloor) keywords.addAll(floorKw);

            List<String> exclusions = ordinalsWithNrs != null
                    ? ordinalsWithNrs.getAsListOfString() : null;
            Double resolvedStoreys = findClosestNumberIgnoringAreaUnits(
                    text, keywords, lang, 3, exclusions, cardinals);

            if (resolvedStoreys != null && resolvedStoreys > 1
                    && !containsAny(text, storeysPluralKw) && !containsAny(text, heightKw)) {
                if (!hasStorey) resolvedStoreys = null;
            }

            if (resolvedStoreys != null && resolvedStoreys < 4) {
                building.setHeight(resolvedStoreys.intValue());
                tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_STOREYS_FROM_DESC);
                logResolved("storeys", resolvedStoreys.intValue(), lang.name() + " (heavy)");
                return;
            }
        }
    }

    // ==================== Heavy resolution ====================

    /**
     * Land size resolution: handles acre/hectare and plot/land keywords
     * with area unit detection and conversion. Only for house-like properties.
     */
    protected void resolveLand(RealEstateListing listing, Building building, String text) {
        boolean isHouseOrNull = listing.getBasic().isSubTypeHouseLike()
                || listing.getBasic().getPropertySubType() == null;
        if (building.getLandSz() != null || !isHouseOrNull) return;

        NumbersAsText cardinals = getCardinals();
        if (cardinals == null) return;

        for (ParserLanguage lang : getLanguages()) {
            String paddedText = text.toLowerCase();
            if (!paddedText.startsWith(" ")) paddedText = " " + paddedText + " ";

            List<String> landAreaKw = lang.landAreaKeywords();
            List<String> plotKw = lang.plotKeywords();
            List<String> landExclusionKw = lang.landExclusionKeywords();

            boolean hasLandArea = containsAny(paddedText, landAreaKw);
            boolean hasPlot = containsAny(paddedText, plotKw);
            if (!hasLandArea && !hasPlot) continue;

            try {
                // Scenario 1: Acre/hectare keywords
                if (hasLandArea) {
                    Double resolvedAcre = findClosestNumberExtended(paddedText, landAreaKw,
                            true, 3, null, landExclusionKw, cardinals);

                    if (resolvedAcre != null) {
                        if (containsAny(paddedText, lang.acreKeywords()))
                            resolvedAcre = UnitConversion.acresToMetersSq(resolvedAcre);
                        else if (containsAny(paddedText, lang.hectareKeywords()))
                            resolvedAcre = UnitConversion.hectaresToMetersSq(resolvedAcre);

                        if (isDescription()
                                && listing.getBasic().getPropertySubType() != null
                                && !listing.getBasic().getPropertySubType().equals(SubTypeEnum.RES_DETACHEDHOUSE_VILLA)) {
                            continue;
                        }

                        building.setLandSz(resolvedAcre.intValue());
                        tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_LAND_ACRE_FROM_DESC);
                        logResolved("landSz", resolvedAcre.intValue(), lang.name() + " (heavy acre)");
                        return;
                    }

                // Scenario 2: Plot/land keywords with area unit detection
                } else if (hasPlot) {
                    for (ParserLanguage.AreaUnit unit : lang.areaUnits()) {
                        String matchedPattern = unit.findInText(paddedText);
                        if (matchedPattern == null) continue;

                        Double plotValue = findClosestNumberExtended(paddedText, plotKw,
                                null, 4, List.of(matchedPattern), landExclusionKw);
                        if (plotValue == null) continue;

                        Double resolvedLand = unit.isImperial()
                                ? UnitConversion.sqFtToMetersSq(plotValue) : plotValue;

                        if (resolvedLand != null) {
                            building.setLandSz(resolvedLand.intValue());
                            tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_LAND_PLOT_FROM_DESC);
                            logResolved("landSz", resolvedLand.intValue(),
                                    lang.name() + " (heavy plot, '" + matchedPattern + "')");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("{} {} Error in heavy land resolution: {}", TAG_TEXT_RESOLVE, getSectionName(), e.getMessage());
            }
        }
    }

    /**
     * Building height resolution: total number of floors in the building.
     * Only for floor-related properties (apartments).
     */
    protected void resolveBuildingHeight(RealEstateListing listing, Building building, String text) {
        if (building.getHeight() != null) return;
        boolean isRetail = listing.getBasic().isCommercialRetail();
        if (isRetail) return;

        boolean isFloorRelated = listing.getBasic().isSubTypeFloorRelated()
                || listing.getBasic().getPropertySubType() == null;
        if (!isFloorRelated) return;

        NumbersAsText cardinals = getCardinals();
        if (cardinals == null) return;

        for (ParserLanguage lang : getLanguages()) {
            List<String> heightKw = lang.buildingHeightKeywords();
            if (!containsAny(text, heightKw)) continue;

            Double resolvedHeight = findClosestNumberIgnoringAreaUnits(
                    text, heightKw, lang, 3, cardinals);

            if (resolvedHeight != null && resolvedHeight >= 1 && resolvedHeight <= 80) {
                building.setHeight(resolvedHeight.intValue());
                tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_BUILDING_HEIGHT_FROM_DESC);
                logResolved("buildingHeight", resolvedHeight.intValue(), lang.name() + " (heavy)");
                return;
            }
        }
    }


    protected void resolveBuildingAge(RealEstateListing listing, Building building, String text) {
        if (building.getBuildingAge() != null) return;
        
        for (ParserLanguage lang : getLanguages()) {
            List<String> ageKw = lang.buildingAgeKeywords();
            if (!containsAny(text, ageKw)) continue;

            Double resolvedAge = findClosestNumber(text, ageKw);
            if (resolvedAge != null) {
                //TODO: Pending to defien the case when 80s, 90s, etc (parcial years)
                building.setBuildingAge(resolvedAge.intValue());
                tagResolvedFromDescription(listing, ScrapingTaggingEnum.GENERIC_RESOLVED_BUILDING_AGE_FROM_DESC);
                logResolved("buildingAge", resolvedAge.intValue(), lang.name() + " (heavy)");
                return;
            }
        }
    }
}
