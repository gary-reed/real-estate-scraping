package com.realestate.dialect.parsing.resolvers;

import com.realestate.dialect.model.Basic.SubTypeEnum;
import com.realestate.dialect.model.Basic.TypeEnum;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.scraper.flavor.cms.parsing.text.NormalizedText;
import com.scraper.flavor.cms.parsing.text.TextSearchUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Shared free-text → {@link SubTypeEnum} guess using {@link ParserLanguage} keyword lists.
 *
 * <p>Residential priority (first hit wins): duplex → loft → apartment → building →
 * attached house → detached / generic house. Site-specific exact label tables stay in scrapers;
 * this only covers free-text (title, features, description).</p>
 *
 * <p>Commercial text guess is not covered here yet — scrapers keep their own maps.</p>
 */
public final class PropertySubtypeTextResolver {

    private PropertySubtypeTextResolver() {}

    /**
     * Guess a residential subtype from free text for the given languages (tried in order).
     *
     * @return matched subtype, or {@code null} if nothing matched
     */
    public static SubTypeEnum resolveResidential(String rawText, ParserLanguage... languages) {
        List<ParserLanguage> langs = (languages == null || languages.length == 0)
                ? ParserLanguage.all()
                : Arrays.asList(languages);
        return resolveResidential(rawText, langs);
    }

    public static SubTypeEnum resolveResidential(String rawText, List<ParserLanguage> languages) {
        if (rawText == null || rawText.isBlank() || languages == null || languages.isEmpty()) {
            return null;
        }
        String text = NormalizedText.normalize(rawText).value();
        if (text == null || text.isBlank()) {
            return null;
        }
        for (ParserLanguage lang : languages) {
            SubTypeEnum hit = resolveResidentialNormalized(text, lang);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * Dispatches by {@link TypeEnum}: residential uses shared keywords; commercial returns {@code null}
     * (site tables remain scraper-owned).
     */
    public static SubTypeEnum resolve(String rawText, TypeEnum type, ParserLanguage... languages) {
        if (type == TypeEnum.COMMERCIAL) {
            return null;
        }
        return resolveResidential(rawText, languages);
    }

    private static SubTypeEnum resolveResidentialNormalized(String text, ParserLanguage lang) {
        if (containsAny(text, lang.duplexSubtypeKeywords())) {
            return SubTypeEnum.RES_DUPLEX;
        }
        if (containsAny(text, lang.loftSubtypeKeywords())) {
            return SubTypeEnum.RES_LOFT;
        }
        if (containsAny(text, lang.apartmentSubtypeKeywords())) {
            return SubTypeEnum.RES_APARTMENT;
        }
        if (containsAny(text, lang.buildingSubtypeKeywords())) {
            return SubTypeEnum.RES_BUILDINGCASTLE;
        }
        if (containsAny(text, lang.attachedHouseSubtypeKeywords())) {
            return SubTypeEnum.RES_ATTACH_SEMIHOUSE;
        }
        if (containsAny(text, lang.detachedHouseSubtypeKeywords())) {
            return SubTypeEnum.RES_DETACHEDHOUSE_VILLA;
        }
        return null;
    }

    /**
     * Same multi-word / {@code |} semantics as {@link TextResolver#containsAny}.
     */
    private static boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null) return false;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) continue;
            if (keyword.contains(" ")) {
                String[] parts = keyword.split("\\s+");
                if (parts.length >= 2
                        && TextSearchUtils.wordsTogetherInText(text, parts[0],
                        Arrays.copyOfRange(parts, 1, parts.length))) {
                    return true;
                }
            } else if (TextSearchUtils.matchesKeyword(text, keyword)) {
                return true;
            }
        }
        return false;
    }
}
