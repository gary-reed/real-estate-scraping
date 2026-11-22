package com.realestate.dialect.parsing;

import com.realestate.dialect.RealEstateParser;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.Property;
import com.scraper.flavor.cms.model.ScrapingDetails;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.realestate.dialect.parsing.resolvers.BuildingResolver;
import com.realestate.dialect.parsing.resolvers.PropertyResolver;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import com.scraper.flavor.cms.parsing.text.TextSearchUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingHeightFloorResolveTest {

    @Test
    void areaFigureNextToSquareMetersIsBesideUnitAndDeIsNot() {
        List<String> units = ParserLanguage.SPANISH.areaUnitPatterns();
        assertTrue(TextSearchUtils.isNumberBesideAny("55 m² en planta baja", 55, units));
        assertFalse(TextSearchUtils.isNumberBesideAny("edificio de 8 plantas", 8, units));
    }

    @Test
    void roomAreaWithPlantaBajaAndPrimeraIsFloorNotHeight() {
        String raw = "(55 m² en planta baja y 31 m² en planta primera)";
        RealEstateListing listing = resolveApartment(raw);

        assertNull(listing.getBuilding().getHeight(), "55 m² must not become buildingHeight");
        assertEquals(1, listing.getProperty().getFloorNr(), "planta primera → floor 1");
    }

    @Test
    void viviendaDeDosPlantasIsBuildingHeight() {
        String raw = "esta vivienda de dos plantas";
        RealEstateListing listing = resolveApartment(raw);

        assertEquals(2, listing.getBuilding().getHeight());
        assertNull(listing.getProperty().getFloorNr(), "plural plantas is height, not floorNr");
    }

    private static RealEstateListing resolveApartment(String rawDescription) {
        NormalizedText text = NormalizedText.normalize(rawDescription);
        var numberClasses = RealEstateParser.getNumberClasses(ParserLanguage.SPANISH);

        Basic basic = new Basic();
        basic.setPropertySubType(Basic.SubTypeEnum.RES_APARTMENT);

        RealEstateListing listing = new RealEstateListing();
        listing.setBasic(basic);
        listing.setProperty(new Property());
        listing.setBuilding(new Building());
        listing.setScrapingDetails(new ScrapingDetails());

        PropertyResolver propertyResolver = new PropertyResolver(
                List.of(ParserLanguage.SPANISH), numberClasses);
        propertyResolver.prepare(text, "description");
        propertyResolver.resolve(listing, text);

        BuildingResolver buildingResolver = new BuildingResolver(
                List.of(ParserLanguage.SPANISH), numberClasses);
        buildingResolver.prepare(text, "description");
        buildingResolver.resolve(listing, text);
        return listing;
    }
}
