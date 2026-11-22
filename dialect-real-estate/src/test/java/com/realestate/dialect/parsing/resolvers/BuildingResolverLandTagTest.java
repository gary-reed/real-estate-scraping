package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.RealEstateParser;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Basic.SubTypeEnum;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.ScrapingTaggingEnum;
import com.scraper.flavor.cms.parsing.ScraperParser;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingResolverLandTagTest {

    private final BuildingResolver resolver = new BuildingResolver(
            List.of(ParserLanguage.ENGLISH),
            RealEstateParser.getNumberClasses(ParserLanguage.ENGLISH));

    private RealEstateListing resolve(String raw, String whatText) {
        RealEstateListing listing = new RealEstateListing();
        Basic basic = new Basic();
        basic.setPropertySubType(SubTypeEnum.RES_DETACHEDHOUSE_VILLA);
        listing.setBasic(basic);
        listing.setBuilding(new Building());
        NormalizedText text = NormalizedText.normalize(raw);
        resolver.prepare(text, whatText, listing);
        resolver.resolve(listing, text);
        return listing;
    }

    @Test
    void acreFromFeaturesSetsLandWithoutTag() {
        RealEstateListing listing = resolve("Approximately 0.5 acres", "features");
        assertNotNull(listing.getBuilding().getLandSz());
        assertTrue(listing.getBuilding().getLandSz() > 0);
        assertTrue(listing.getScrapingDetails() == null
                || !listing.getScrapingDetails().hasTag(
                        ScrapingTaggingEnum.GENERIC_RESOLVED_LAND_ACRE_FROM_DESC));
    }

    @Test
    void acreFromDescriptionSetsLandAndTag() {
        RealEstateListing listing = resolve("The plot extends to approximately 2 acres", "description");
        assertNotNull(listing.getBuilding().getLandSz());
        assertTrue(listing.getScrapingDetails().hasTag(
                ScrapingTaggingEnum.GENERIC_RESOLVED_LAND_ACRE_FROM_DESC));
    }
}
