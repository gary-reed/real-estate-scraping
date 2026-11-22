package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Furnish;
import com.realestate.dialect.model.Furnish.FurnishingTypeEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnishResolverFurnishingTypeTest {

    private final FurnishResolver resolver =
            new FurnishResolver(List.of(ParserLanguage.ENGLISH), null);

    private Furnish resolve(String raw) {
        RealEstateListing listing = new RealEstateListing();
        listing.setFurnish(new Furnish());
        NormalizedText text = NormalizedText.normalize(raw);
        resolver.prepare(text, "description", listing);
        resolver.resolve(listing, text);
        return listing.getFurnish();
    }

    @Test
    // If both are present, will default to FURNISHED
    void flexibleFurnishedOrUnfurnishedLeavesUnset() {
        Furnish f = resolve("Furnished or unfurnished, landlord is flexible");
        assertTrue(f.getFurnishType() == FurnishingTypeEnum.FURNISHED);
    }

    @Test
    void unfurnishedAloneResolves() {
        assertEquals(FurnishingTypeEnum.UNFURNISHED, resolve("Bright unfurnished flat in town").getFurnishType());
    }

    @Test
    void furnishedAloneResolves() {
        assertEquals(FurnishingTypeEnum.FURNISHED, resolve("Beautifully furnished flat near the park").getFurnishType());
    }

    @Test
    void semiFurnishedResolves() {
        assertEquals(FurnishingTypeEnum.SEMI_FURNISHED, resolve("Part furnished house share").getFurnishType());
    }
}
