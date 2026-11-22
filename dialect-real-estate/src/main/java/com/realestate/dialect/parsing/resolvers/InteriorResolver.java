package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.RealEstateListing;

import com.realestate.dialect.model.Interior;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.number.NumbersAsText;
import com.scraper.flavor.cms.parsing.text.NormalizedText;

import java.util.EnumSet;
import java.util.List;

/**
 * Resolves missing Interior fields from text.
 * Focuses on: shower/bath facilities, double sink detection.
 */
public class InteriorResolver extends TextResolver {

    public InteriorResolver(List<ParserLanguage> languages, NumbersAsText[] numberClasses) {
        super(languages, numberClasses);
    }

    @Override
    public String getSectionName() {
        return "Interior";
    }

    @Override
    public RealEstateListing resolve(RealEstateListing listing, NormalizedText text) {
        if (text == null || text.isBlank()) return listing;

        Interior interior = listing.getInterior();
        if (interior == null) {
            interior = new Interior();
            listing.setInterior(interior);
        }

        String textStr = text.value();

        resolveShowerBath(interior, textStr);
        resolveBathFacilities(interior, textStr);

        return listing;
    }

    protected void resolveShowerBath(Interior interior, String text) {
        EnumSet<Interior.ShowerBathEnum> showerBath = interior.getShowerBath();
        if (showerBath != null && !showerBath.isEmpty()) return;

        for (ParserLanguage lang : getLanguages()) {
            if (containsAny(text, lang.walkInShowerKeywords())) {
                interior.addShowerBath(Interior.ShowerBathEnum.WALK_IN_SHOWER);
                logResolved("showerBath:WALK_IN_SHOWER", true, lang.name());
            }
            if (containsAny(text, lang.massageShowerKeywords())) {
                interior.addShowerBath(Interior.ShowerBathEnum.MASSAGE_SHOWER);
                logResolved("showerBath:MASSAGE_SHOWER", true, lang.name());
            }
            if (containsAny(text, lang.jacuzziKeywords())) {
                interior.addShowerBath(Interior.ShowerBathEnum.JACUZZI);
                logResolved("showerBath:JACUZZI", true, lang.name());
            }
            if (containsAny(text, lang.bathtubKeywords())) {
                interior.addShowerBath(Interior.ShowerBathEnum.BATHTUB);
                logResolved("showerBath:BATHTUB", true, lang.name());
            }
            if ((interior.getShowerBath() == null || interior.getShowerBath().isEmpty())
                    && containsAny(text, lang.showerKeywords())) {
                interior.addShowerBath(Interior.ShowerBathEnum.NORMAL_SHOWER);
                logResolved("showerBath:NORMAL_SHOWER", true, lang.name());
            }
        }
    }

    protected void resolveBathFacilities(Interior interior, String text) {
        EnumSet<Interior.BathFacilitiesEnum> facilities = interior.getBathFacilities();

        for (ParserLanguage lang : getLanguages()) {
            if ((facilities == null || !facilities.contains(Interior.BathFacilitiesEnum.EN_SUITE))
                    && containsAny(text, lang.enSuiteKeywords())) {
                interior.addBathFacility(Interior.BathFacilitiesEnum.EN_SUITE);
                logResolved("bathFacility:EN_SUITE", true, lang.name());
            }
            if ((facilities == null || !facilities.contains(Interior.BathFacilitiesEnum.DOUBLE_SINK))
                    && containsAny(text, lang.doubleSinkKeywords())) {
                interior.addBathFacility(Interior.BathFacilitiesEnum.DOUBLE_SINK);
                logResolved("bathFacility:DOUBLE_SINK", true, lang.name());
            }
        }
    }
}
