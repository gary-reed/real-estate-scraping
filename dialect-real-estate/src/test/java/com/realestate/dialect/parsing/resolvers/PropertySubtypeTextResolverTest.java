package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.Basic.SubTypeEnum;
import com.realestate.dialect.model.Basic.TypeEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertySubtypeTextResolverTest {

    @Test
    void englishHouseFromDescription() {
        assertEquals(SubTypeEnum.RES_DETACHEDHOUSE_VILLA,
                PropertySubtypeTextResolver.resolveResidential(
                        "Generously proportioned 3 bedroom house with photovoltaic panels",
                        ParserLanguage.ENGLISH));
    }

    @Test
    void englishTerracedBeatsBareHouse() {
        assertEquals(SubTypeEnum.RES_ATTACH_SEMIHOUSE,
                PropertySubtypeTextResolver.resolveResidential(
                        "A charming terraced house near the park", ParserLanguage.ENGLISH));
    }

    @Test
    void englishFlatAndDuplex() {
        assertEquals(SubTypeEnum.RES_APARTMENT,
                PropertySubtypeTextResolver.resolveResidential("Bright 2 bed flat", ParserLanguage.ENGLISH));
        assertEquals(SubTypeEnum.RES_DUPLEX,
                PropertySubtypeTextResolver.resolveResidential("Spacious maisonette with garden",
                        ParserLanguage.ENGLISH));
    }

    @Test
    void spanishTitleCases() {
        assertEquals(SubTypeEnum.RES_APARTMENT,
                PropertySubtypeTextResolver.resolveResidential("Piso en venta en Centro", ParserLanguage.SPANISH));
        assertEquals(SubTypeEnum.RES_APARTMENT,
                PropertySubtypeTextResolver.resolveResidential("Ático con terraza", ParserLanguage.SPANISH));
        assertEquals(SubTypeEnum.RES_DUPLEX,
                PropertySubtypeTextResolver.resolveResidential("Dúplex en Eixample", ParserLanguage.SPANISH));
        assertEquals(SubTypeEnum.RES_DETACHEDHOUSE_VILLA,
                PropertySubtypeTextResolver.resolveResidential("Chalet independiente", ParserLanguage.SPANISH));
        assertEquals(SubTypeEnum.RES_ATTACH_SEMIHOUSE,
                PropertySubtypeTextResolver.resolveResidential("Adosado con jardín", ParserLanguage.SPANISH));
        assertEquals(SubTypeEnum.RES_LOFT,
                PropertySubtypeTextResolver.resolveResidential("Loft reformado", ParserLanguage.SPANISH));
    }

    @Test
    void unknownTextReturnsNull() {
        assertNull(PropertySubtypeTextResolver.resolveResidential("Something quirky with no cues",
                ParserLanguage.ENGLISH));
    }

    @Test
    void commercialTypeNotResolvedHere() {
        assertNull(PropertySubtypeTextResolver.resolve(
                "Office for sale", TypeEnum.COMMERCIAL, ParserLanguage.ENGLISH));
    }
}
