package com.realestate.dialect.parsing.language;

import com.scraper.commons.core.ScraperLanguage;

import java.util.Arrays;
import java.util.List;

/**
 * Supported languages for description-based resolution.
 *
 * <p>Each language defines:</p>
 * <ul>
 *   <li>Unit system for the language's primary measurement convention</li>
 *   <li>Area unit definitions ({@link AreaUnit}) for surface/land resolution</li>
 *   <li>All keyword lists used by resolvers to extract information from free text</li>
 * </ul>
 *
 * <p>Keywords use <b>stems/partial tokens</b> for versatile matching:
 * "privat" matches "private/privato/privata", "fume" matches "fumeur/fumer/fumatori".
 * Multi-word keywords omit prepositions — {@code containsAny} uses proximity matching
 * via {@code wordsTogetherInText} which tolerates filler words between tokens.</p>
 *
 * <p>Prefix/suffix {@code |} pins a token to a word boundary (space in normalized text).
 * Bare stems are substrings; pipes stop accidental hits inside longer words:</p>
 * <ul>
 *   <li>{@code "|bed|"} — the whole word {@code bed} only (not {@code bedroom})</li>
 *   <li>{@code "|pent"} — word must <em>start</em> with {@code pent} ({@code penthouse}, not {@code independent})</li>
 *   <li>{@code "pool|"} — word must <em>end</em> with {@code pool} ({@code pool}, {@code whirlpool})</li>
 *   <li>{@code "built |in|"} — {@code in} must be its own word (not the {@code in} inside {@code kitchen})</li>
 * </ul>
 *
 * <p>Dashes are normalized to spaces by {@link com.scraper.flavor.cms.parsing.text.NormalizedText},
 * so keywords never contain dashes (e.g. "air conditioning" matches both
 * "air-conditioning" and "air conditioning").</p>
 *
 * <p>To add a new language: add the enum constant and add a case in every keyword method.</p>
 */
public enum ParserLanguage {

    ENGLISH("en", "English", UnitSystem.IMPERIAL, ScraperLanguage.ENGLISH),
    SPANISH("es", "Spanish", UnitSystem.METRIC, ScraperLanguage.SPANISH),
    GERMAN("de", "German", UnitSystem.METRIC, ScraperLanguage.GERMAN),
    FRENCH("fr", "French", UnitSystem.METRIC, ScraperLanguage.FRENCH),
    ITALIAN("it", "Italian", UnitSystem.METRIC, ScraperLanguage.ITALIAN);

    private final String code;
    private final String displayName;
    private final UnitSystem unitSystem;
    private final ScraperLanguage scraperLanguage;

    ParserLanguage(String code, String displayName, UnitSystem unitSystem, ScraperLanguage scraperLanguage) {
        this.code = code;
        this.displayName = displayName;
        this.unitSystem = unitSystem;
        this.scraperLanguage = scraperLanguage;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public UnitSystem getUnitSystem() { return unitSystem; }
    public ScraperLanguage getScraperLanguage() { return scraperLanguage; }

    /**
     * Find language by ISO code (case-insensitive).
     */
    public static ParserLanguage fromCode(String code) {
        if (code == null) return null;
        for (ParserLanguage lang : values()) {
            if (lang.code.equalsIgnoreCase(code)) return lang;
        }
        return null;
    }

    /**
     * Returns all languages as a list for iteration.
     */
    public static List<ParserLanguage> all() {
        return Arrays.asList(values());
    }

    /** Measurement system used in a country/language */
    public enum UnitSystem {
        IMPERIAL,  // feet, sq ft (UK, US)
        METRIC     // meters, m² (most of Europe)
    }

    // ====================================================================
    //  AREA UNIT DEFINITIONS (used by surface and land resolvers)
    // ====================================================================

    /**
     * Defines an area measurement unit with all known text variations for matching.
     *
     * <p>Patterns are ordered from most specific (longest) to least specific.
     * The resolver uses {@link #findInText(String)} to detect which unit and which
     * exact text pattern matched, enabling accurate logging and conversion.</p>
     *
     * @param name       canonical name for logging ("sqft", "m²")
     * @param isImperial true for imperial units (need sqft→m² conversion)
     * @param patterns   all text patterns that identify this unit (post-normalization)
     */
    public record AreaUnit(String name, boolean isImperial, List<String> patterns) {
        /**
         * Returns the first matching pattern found in text, or null if none match.
         * Patterns are checked in order (longest/most-specific first).
         */
        public String findInText(String text) {
            for (String p : patterns) {
                if (text.contains(p)) return p;
            }
            return null;
        }
    }

    /**
     * Returns the area unit definitions for this language, with all known text patterns.
     *
     * <p>Patterns reflect how unit text appears AFTER normalization (lowercased,
     * accents stripped, dots cleaned from "sq."/"ft.", m2→m², ft2→ft²).
     * The resolver matches these patterns against normalized text to detect units
     * and log exactly what matched.</p>
     *
     * <p>Each language lists its primary unit system first (e.g. sqft for ENGLISH,
     * m² for SPANISH) so the most likely unit is checked first.</p>
     */
    public List<AreaUnit> areaUnits() {
        return switch (this) {
            case ENGLISH -> List.of(
                new AreaUnit("sqft", true, List.of("square feet", "square foot", "sq ft", "sqft", "ft²")),
                new AreaUnit("m²", false, List.of("square met", "sq m", "sqm", "m²"))
            );
            case SPANISH -> List.of(
                new AreaUnit("m²", false, List.of("metros cuadrados", "mts²", "sq m", "sqm", "m²")),
                new AreaUnit("sqft", true, List.of("square feet", "sq ft", "sqft", "ft²"))
            );
            case GERMAN -> List.of(
                new AreaUnit("m²", false, List.of("quadratmeter", "qm", "sq m", "sqm", "m²")),
                new AreaUnit("sqft", true, List.of("square feet", "sq ft", "sqft", "ft²"))
            );
            case FRENCH -> List.of(
                new AreaUnit("m²", false, List.of("metres carres", "sq m", "sqm", "m²")),
                new AreaUnit("sqft", true, List.of("square feet", "sq ft", "sqft", "ft²"))
            );
            case ITALIAN -> List.of(
                new AreaUnit("m²", false, List.of("metri quadr", "mq", "sq m", "sqm", "m²")),
                new AreaUnit("sqft", true, List.of("square feet", "sq ft", "sqft", "ft²"))
            );
        };
    }

    /** Flattened area-unit patterns (plus first token of multi-word units) for number-beside-unit checks. */
    public List<String> areaUnitPatterns() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (AreaUnit unit : areaUnits()) {
            for (String pattern : unit.patterns()) {
                if (pattern == null || pattern.isBlank()) continue;
                out.add(pattern);
                String first = pattern.split("\\s+")[0];
                if (first.length() >= 3) {
                    out.add(first);
                }
            }
        }
        return List.copyOf(out);
    }

    // ====================================================================
    //  PROPERTY KEYWORDS
    // ====================================================================

    public List<String> bedroomKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("bedroom", "|bed|", "|beds|", "double bedroom", "single bedroom");
            case SPANISH  -> List.of("dormitorio", "habitacion", "alcoba", "recamara");
            case GERMAN   -> List.of("schlafzimmer", "zimmer", "schlafraum");
            case FRENCH   -> List.of("chambre");
            case ITALIAN  -> List.of("camera", "camere", "stanza letto");
        };
    }

    public List<String> bathroomKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("bathroom", "|bath|", "wc", "shower room", "ensuite");
            case SPANISH  -> List.of("bano", "aseo", "cuarto bano");
            case GERMAN   -> List.of("bad", "badezimmer", "duschbad", "wc");
            case FRENCH   -> List.of("salle bain", "salle d'eau", "wc");
            case ITALIAN  -> List.of("bagno", "bagni", "servizi", "toilette");
        };
    }

    public List<String> floorKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|floor|", "|storey|", "|story|", "level", "ground floor", "first floor", "basement");
            case SPANISH  -> List.of("|planta|", "|piso|", "planta baja", "sotano", "primer piso");
            case GERMAN   -> List.of("|etage|", "|stockwerk|", "erdgeschoss", "obergeschoss", "untergeschoss", "|stock|");
            case FRENCH   -> List.of("|etage|", "rez chaussee", "sous sol", "niveau");
            case ITALIAN  -> List.of("|piano|", "pianterreno", "piano terra", "seminterrato", "livello");
        };
    }

    // ====================================================================
    //  TRANSACTION KEYWORDS
    // ====================================================================

    public List<String> priceKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("price", "rent", "pcm", "per month", "per week", "pw", "asking price", "guide price");
            case SPANISH  -> List.of("precio", "alquiler", "renta", "mensual", "por mes");
            case GERMAN   -> List.of("preis", "miete", "kaltmiete", "warmmiete", "monatlich", "kaufpreis");
            case FRENCH   -> List.of("prix", "loyer", "mensuel", "par mois");
            case ITALIAN  -> List.of("prezzo", "affitto", "canone", "mensile", "al mese");
        };
    }

    public List<String> depositKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("deposit", "security deposit", "bond");
            case SPANISH  -> List.of("deposito", "fianza", "garantia");
            case GERMAN   -> List.of("kaution", "sicherheitsleistung", "mietkaution");
            case FRENCH   -> List.of("depot garantie", "caution");
            case ITALIAN  -> List.of("deposito cauzional", "cauzione", "caparra");
        };
    }

    public List<String> availableNowKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("availab now", "availab immediat", "move in now");
            case SPANISH  -> List.of("disponib ahora", "disponib immediat", "disponib ya");
            case GERMAN   -> List.of("sofort verfugbar", "ab sofort", "sofort frei", "sofort beziehbar");
            case FRENCH   -> List.of("disponib immediate", "disponib suite", "libr suite");
            case ITALIAN  -> List.of("disponib subito", "disponib immediat", "liber subito");
        };
    }

    // ====================================================================
    //  FURNISH KEYWORDS
    // ====================================================================

    // Unfurnished first in the resolver when only one side matches. Furnished stems that would
    // otherwise match inside unfurnished compounds use |word-boundary (EN "|furnished", DE "|mobliert").
    public List<String> unfurnishedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("unfurnished", "furnish not");
            case SPANISH  -> List.of("amuebl sin", "muebl sin", "amuebl no", "desamuebl",
                    "amobl sin", "amobl no", "desamobl", "desamuebl"
            );
            case GERMAN   -> List.of("unmobliert", "ohne mobel");
            case FRENCH   -> List.of("meubl non", "vide");
            case ITALIAN  -> List.of("arredat non", "vuoto");
        };
    }

    public List<String> furnishedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|furnished", "fully furnished");
            case SPANISH  -> List.of("amueblado", "completamente amueblado", "amoblado");
            case GERMAN   -> List.of("|mobliert", "voll mobliert", "vollmobliert");
            case FRENCH   -> List.of("meubl", "entierem meubl");
            case ITALIAN  -> List.of("arredat", "completamente arredat");
        };
    }

    public List<String> semiFurnishedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("part furnished", "partly furnished", "semi furnished");
            case SPANISH  -> List.of("parcialmente amueblado", "semi amueblado");
            case GERMAN   -> List.of("teilmobliert");
            case FRENCH   -> List.of("partiellement meuble", "semi meuble");
            case ITALIAN  -> List.of("parzialmente arredato", "semi arredato");
        };
    }

    // ====================================================================
    //  PROPERTY SUBTYPE KEYWORDS (free-text guess, ordered by caller)
    // ====================================================================

    public List<String> duplexSubtypeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("maisonette", "duplex", "triplex");
            case SPANISH  -> List.of("duplex", "triplex");
            case GERMAN   -> List.of("maisonette", "duplex", "triplex");
            case FRENCH   -> List.of("duplex", "triplex");
            case ITALIAN  -> List.of("duplex", "triplex");
        };
    }

    public List<String> loftSubtypeKeywords() {
        return switch (this) {
            case ENGLISH, SPANISH, GERMAN, FRENCH, ITALIAN -> List.of("|loft|");
        };
    }

    public List<String> apartmentSubtypeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("flat", "apartment", "studio", "penthouse", "bedsit");
            case SPANISH  -> List.of("piso", "partamento", "studio", "atico", "penthouse");
            case GERMAN   -> List.of("wohnung", "apartment", "studio", "penthouse");
            case FRENCH   -> List.of("appartement", "studio", "penthouse");
            case ITALIAN  -> List.of("appartamento", "monolocale", "studio", "attico", "penthouse");
        };
    }

    public List<String> buildingSubtypeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("block of", "multiple occupation", "castle", "|hmo|");
            case SPANISH  -> List.of("edificio", "castillo");
            case GERMAN   -> List.of("mehrfamilienhaus", "schloss");
            case FRENCH   -> List.of("immeuble", "chateau");
            case ITALIAN  -> List.of("palazzo", "castello");
        };
    }

    /** Semi / terrace / townhouse — check before generic house. */
    public List<String> attachedHouseSubtypeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("town house", "link detached", "end row", "end chain", "semi", "terrac", "townhouse", "mews");
            case SPANISH  -> List.of("adosado", "pareado");
            case GERMAN   -> List.of("reihenhaus", "doppelhaus");
            case FRENCH   -> List.of("mitoyenne", "jumelé", "jumele");
            case ITALIAN  -> List.of("a schiera", "bifamiliare");
        };
    }

    /**
     * Detached / villa / cottage / generic house. Put {@code |house|} last among callers'
     * priority so finer shapes win. Use word-boundary on house to avoid {@code warehouse}.
     */
    public List<String> detachedHouseSubtypeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("farm house", "country house", "detach", "villa", "farmhouse", "manor",
                    "lodge", "barn", "chalet", "finca", "cortijo", "riad", "cabin", "bungalow", "cottage", "|house|");
            case SPANISH  -> List.of("casa rural", "chalet", "casa", "villa", "finca", "masia");
            case GERMAN   -> List.of("einfamilienhaus", "villa", "haus", "bungalow", "ferienhaus");
            case FRENCH   -> List.of("maison", "villa", "chalet", "pavillon");
            case ITALIAN  -> List.of("casa", "villa", "chalet", "rustico");
        };
    }

    // ====================================================================
    //  BUILDING KEYWORDS
    // ====================================================================

    public List<String> parkingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("parking", "garage", "car port", "car park", "off street parking", "driveway");
            case SPANISH  -> List.of("estacionamiento", "garaje", "aparcamiento", "parking", "cochera", "plaza garaje");
            case GERMAN   -> List.of("stellplatz", "garage", "parkplatz", "tiefgarage", "carport");
            case FRENCH   -> List.of("parking", "garage", "stationnement", "place parking");
            case ITALIAN  -> List.of("parcheggio", "garage", "box auto", "posto auto");
        };
    }

    public List<String> elevatorKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("elevator", "|lift|");
            case SPANISH  -> List.of("ascensor", "elevador");
            case GERMAN   -> List.of("aufzug", "fahrstuhl", "|lift|");
            case FRENCH   -> List.of("ascenseur");
            case ITALIAN  -> List.of("ascensore");
        };
    }

    public List<String> poolKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("swim |pool|", "|pool|");
            case SPANISH  -> List.of("piscina", "alberca");
            case GERMAN   -> List.of("schwimmbad", "|pool|", "swimmingpool");
            case FRENCH   -> List.of("piscine");
            case ITALIAN  -> List.of("piscina");
        };
    }

    public List<String> gardenKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("garden", "rear garden");
            case SPANISH  -> List.of("jardin");
            case GERMAN   -> List.of("garten");
            case FRENCH   -> List.of("jardin");
            case ITALIAN  -> List.of("giardino");
        };
    }

    public List<String> privateKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("privat", "own", "exclusiv", "dedicat", "personal");
            case SPANISH  -> List.of("privad", "propi", "exclusiv");
            case GERMAN   -> List.of("privat", "eigen", "exklusiv");
            case FRENCH   -> List.of("priv", "propre", "exclusif");
            case ITALIAN  -> List.of("privat", "propri", "esclusiv");
        };
    }

    public List<String> sharedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("shared", "communal", "common", "resident", "community");
            case SPANISH  -> List.of("compartid", "comunitari", "comunal", "residente");
            case GERMAN   -> List.of("gemeinschaftlich", "gemeinschaft", "gemeinsam", "bewohner");
            case FRENCH   -> List.of("commun", "partag", "resident", "copropriete");
            case ITALIAN  -> List.of("condominial", "condivis", "comune", "resident");
        };
    }

    public List<String> buildingAgeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("year built", "built |in|");
            case SPANISH  -> List.of("construido |en|", "año de constr", "año constr");
            case GERMAN   -> List.of("baujahr", "erbaut", "jahr");
            case FRENCH   -> List.of("année de constr", "construit |en|", "année constr");
            case ITALIAN  -> List.of("anno costr", "costruito ne", "anno di costr");
        };
    }

    // ====================================================================
    //  INTERIOR KEYWORDS
    // ====================================================================

    public List<String> airConditioningKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("air con", "a/c", "aircon");
            case SPANISH  -> List.of("aire acond", "climatiz");
            case GERMAN   -> List.of("klimaanlage", "klimatisier");
            case FRENCH   -> List.of("climatisation", "clim", "air conditionne");
            case ITALIAN  -> List.of("aria condizionata", "climatizzazione", "condizionamento");
        };
    }

    public List<String> centralHeatingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("central heating");
            case SPANISH  -> List.of("calef central");
            case GERMAN   -> List.of("zentralheizung");
            case FRENCH   -> List.of("chauffage central");
            case ITALIAN  -> List.of("riscaldamento centralizz");
        };
    }

    public List<String> underfloorHeatingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("heat floor", "radiant floor");
            case SPANISH  -> List.of("suelo radiante", "calef suelo");
            case GERMAN   -> List.of("fussbodenheizung", "bodenheizung");
            case FRENCH   -> List.of("plancher chauffant", "chauffage sol");
            case ITALIAN  -> List.of("riscaldamento pavimento", "pavimento riscaldato");
        };
    }

    // ====================================================================
    //  LOCATION KEYWORDS
    // ====================================================================

    public List<String> nearTransportKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("near station", "close station", "transport link", "underground", "tube station", "bus stop", "metro");
            case SPANISH  -> List.of("cerca metro", "cerca transporte", "estacion metro", "parada autobus");
            case GERMAN   -> List.of("nahe bahn", "nahe haltest", "gute anbindung", "verkehrsanbindung");
            case FRENCH   -> List.of("proche metro", "proche transport", "station metro", "arret bus");
            case ITALIAN  -> List.of("vicino metro", "vicino stazione", "fermata metro", "fermata autobus");
        };
    }

    // ====================================================================
    //  BASIC / POLICY KEYWORDS
    // ====================================================================

    public List<String> petsAllowedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("pets allowed", "pet friendly", "pets considered", "pets welcome", "pets permitted");
            case SPANISH  -> List.of("mascotas permitid", "admiten mascotas", "apto mascotas");
            case GERMAN   -> List.of("haustiere erlaubt", "haustierfreundlich", "haustiere willkommen");
            case FRENCH   -> List.of("animaux accepte", "animaux autorise", "animaux bienvenu");
            case ITALIAN  -> List.of("animali ammess", "animali consent", "accettano animali");
        };
    }

    public List<String> noSmokingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("no smok", "non smok", "smok free", "smok no permit");
            case SPANISH  -> List.of("no fuma", "prohibido fuma", "sin humo");
            case GERMAN   -> List.of("nicht rauchen", "nichtraucher", "rauchfrei", "rauchen verboten");
            case FRENCH   -> List.of("non fume", "interdit fume", "sans tabac");
            case ITALIAN  -> List.of("vietato fumare", "non fumator");
        };
    }

    public List<String> smokingAllowedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("smoking allowed", "smoking permitted");
            case SPANISH  -> List.of("permite fumar", "fumar permitido");
            case GERMAN   -> List.of("rauchen erlaubt");
            case FRENCH   -> List.of("fumeur autorise");
            case ITALIAN  -> List.of("fumare consentito");
        };
    }

    public List<String> studentsAllowedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("student welcome", "suitable student", "student friendly", "student let");
            case SPANISH  -> List.of("apto estudiantes", "estudiantes bienveni");
            case GERMAN   -> List.of("studenten willkommen", "studenten geeignet");
            case FRENCH   -> List.of("etudiants bienvenu", "convient etudiants");
            case ITALIAN  -> List.of("studenti benvenut", "adatto studenti");
        };
    }

    public List<String> balconyKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("balcony", "terrace", "roof terrace", "patio");
            case SPANISH  -> List.of("balcon", "terraza");
            case GERMAN   -> List.of("balkon", "terrasse", "dachterrasse");
            case FRENCH   -> List.of("balcon", "terrasse");
            case ITALIAN  -> List.of("balcone", "terrazza", "terrazzino");
        };
    }

    public List<String> fireplaceKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("fireplace", "log burner", "wood burner", "open fire");
            case SPANISH  -> List.of("chimenea", "hogar");
            case GERMAN   -> List.of("kamin", "kaminofen", "offener kamin");
            case FRENCH   -> List.of("cheminee", "poele");
            case ITALIAN  -> List.of("camino", "caminetto");
        };
    }

    // ====================================================================
    //  TEXT RESOLUTION KEYWORDS (used by number-near-word resolution)
    // ====================================================================

    /** Keywords for resolving bathroom count from text (stems for number-near-word search) */
    public List<String> bathroomResolutionKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("bath", "wc");
            case SPANISH  -> List.of("bano", "aseo", "wc");
            case GERMAN   -> List.of("bad", "wc");
            case FRENCH   -> List.of("bain", "wc");
            case ITALIAN  -> List.of("bagn", "wc");
        };
    }

    /** Plural bathroom keywords — needed to validate resolved count > 1 */
    public List<String> bathroomPluralKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("bathrooms", "wc");
            case SPANISH  -> List.of("banos", "aseos", "wc");
            case GERMAN   -> List.of("bader", "badezimmer", "wc");
            case FRENCH   -> List.of("bains", "wc");
            case ITALIAN  -> List.of("bagni", "wc");
        };
    }

    /** Keywords indicating "floor" for floor number resolution (whole word, not plural height) */
    public List<String> floorResolutionKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|floor|");
            case SPANISH  -> List.of("|planta|", "|piso|");
            case GERMAN   -> List.of("|etage|", "|stock|");
            case FRENCH   -> List.of("|etage|");
            case ITALIAN  -> List.of("|piano|");
        };
    }

    /** Keywords for "ground floor" detection */
    public List<String> groundFloorKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("ground");
            case SPANISH  -> List.of("baja", "bajo");
            case GERMAN   -> List.of("erdgeschoss", "eg");
            case FRENCH   -> List.of("rez chaussee", "rdc");
            case ITALIAN  -> List.of("terra", "pianterreno");
        };
    }

    /** Keywords for "top floor" / "last floor" detection */
    public List<String> topFloorKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("top", "last");
            case SPANISH  -> List.of("ultimo", "ultima");
            case GERMAN   -> List.of("oberst", "dachgeschoss");
            case FRENCH   -> List.of("dernier");
            case ITALIAN  -> List.of("ultimo", "attico");
        };
    }

    /** Keywords for "high floor" detection */
    public List<String> highFloorKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("high", "upper");
            case SPANISH  -> List.of("alto", "alta");
            case GERMAN   -> List.of("hoch", "obere");
            case FRENCH   -> List.of("haut");
            case ITALIAN  -> List.of("alto", "alta");
        };
    }

    /** Keywords for "low floor" detection */
    public List<String> lowFloorKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("low");
            case SPANISH  -> List.of("bajo", "baja");
            case GERMAN   -> List.of("niedrig", "untere");
            case FRENCH   -> List.of("bas");
            case ITALIAN  -> List.of("basso", "bassa");
        };
    }

    /** Keywords for "penthouse" detection */
    public List<String> penthouseKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|pent");
            case SPANISH  -> List.of("atico", "penthouse");
            case GERMAN   -> List.of("penthouse", "dachgeschoss");
            case FRENCH   -> List.of("penthouse", "attique");
            case ITALIAN  -> List.of("attico", "penthouse");
        };
    }

    /** Keywords indicating "underfloor heating" — used to skip floor resolution */
    public List<String> floorHeatingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("heat");
            case SPANISH  -> List.of("calefac", "radiant");
            case GERMAN   -> List.of("heiz", "fussbodenheiz");
            case FRENCH   -> List.of("chauff");
            case ITALIAN  -> List.of("riscald");
        };
    }

    /** Plural whole-word height keywords ({@code |plantas|}, not singular {@code planta}). */
    public List<String> buildingHeightKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|floors|");
            case SPANISH  -> List.of("|plantas|", "|pisos|");
            case GERMAN   -> List.of("|etagen|", "|stockwerke|");
            case FRENCH   -> List.of("|etages|");
            case ITALIAN  -> List.of("|piani|");
        };
    }

    /** Keywords for house storeys resolution */
    public List<String> storeysKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("storey", "story");
            case SPANISH  -> List.of("nivel", "planta");
            case GERMAN   -> List.of("geschoss", "stockwerk");
            case FRENCH   -> List.of("niveau");
            case ITALIAN  -> List.of("livello", "piano");
        };
    }

    /** Plural forms of storey keywords — needed to validate resolved count > 1 */
    public List<String> storeysPluralKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("storeys", "stories", "floors");
            case SPANISH  -> List.of("niveles", "plantas", "pisos");
            case GERMAN   -> List.of("geschosse", "stockwerke", "etagen");
            case FRENCH   -> List.of("niveaux", "etages");
            case ITALIAN  -> List.of("livelli", "piani");
        };
    }

    /** Keywords for acre detection in land resolution */
    public List<String> acreKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("acre");
            case SPANISH  -> List.of();
            case GERMAN   -> List.of("morgen");
            case FRENCH   -> List.of();
            case ITALIAN  -> List.of();
        };
    }

    /** Keywords for hectare detection in land resolution */
    public List<String> hectareKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("hectar");
            case SPANISH  -> List.of("hectar");
            case GERMAN   -> List.of("hektar");
            case FRENCH   -> List.of("hectar");
            case ITALIAN  -> List.of("ettar");
        };
    }

    /** Keywords for acre/hectare land measurement (combined) */
    public List<String> landAreaKeywords() {
        List<String> all = new java.util.ArrayList<>();
        all.addAll(acreKeywords());
        all.addAll(hectareKeywords());
        return all;
    }

    /** Keywords for plot/land references */
    public List<String> plotKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|plot|", "|land|");
            case SPANISH  -> List.of("parcela", "terreno", "solar");
            case GERMAN   -> List.of("grundstuck", "|land|");
            case FRENCH   -> List.of("terrain", "parcelle");
            case ITALIAN  -> List.of("terreno", "lotto");
        };
    }

    /** Exclusion keywords for land resolution (community land, parks, etc.) */
    public List<String> landExclusionKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("park", "comm", "green", "wood", "esta");
            case SPANISH  -> List.of("parque", "comun", "verde", "bosque");
            case GERMAN   -> List.of("park", "gemein", "wald");
            case FRENCH   -> List.of("parc", "commun", "bois");
            case ITALIAN  -> List.of("parco", "comune", "bosco");
        };
    }

    // ====================================================================
    //  FEATURE DETECTION KEYWORDS (amenities and facilities)
    // ====================================================================

    public List<String> bathtubKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("bathtub", "bath tub", "roll top bath");
            case SPANISH  -> List.of("banera");
            case GERMAN   -> List.of("badewanne");
            case FRENCH   -> List.of("baignoire");
            case ITALIAN  -> List.of("vasca bagno", "vasca");
        };
    }

    public List<String> showerKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("shower");
            case SPANISH  -> List.of("ducha");
            case GERMAN   -> List.of("dusche");
            case FRENCH   -> List.of("douche");
            case ITALIAN  -> List.of("doccia");
        };
    }

    public List<String> walkInShowerKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("walk in shower", "open shower");
            case SPANISH  -> List.of("ducha ras suelo", "ducha obra");
            case GERMAN   -> List.of("begehbare dusche", "bodengleiche dusche");
            case FRENCH   -> List.of("douche italienne");
            case ITALIAN  -> List.of("doccia filo pavimento", "doccia walk in");
        };
    }

    public List<String> massageShowerKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("massage shower", "power shower");
            case SPANISH  -> List.of("ducha hidromasaje");
            case GERMAN   -> List.of("massagedusche");
            case FRENCH   -> List.of("douche hydromassant");
            case ITALIAN  -> List.of("doccia idromassaggio");
        };
    }

    public List<String> jacuzziKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("jacuzzi", "hot tub", "whirlpool");
            case SPANISH  -> List.of("jacuzzi", "banera hidromasaje");
            case GERMAN   -> List.of("jacuzzi", "whirlpool");
            case FRENCH   -> List.of("jacuzzi", "bain remous");
            case ITALIAN  -> List.of("jacuzzi", "vasca idromassaggio");
        };
    }

    public List<String> enSuiteKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("en suite", "ensuite");
            case SPANISH  -> List.of("bano suite", "bano privado");
            case GERMAN   -> List.of("en suite", "eigenes bad");
            case FRENCH   -> List.of("salle bain privativ", "en suite");
            case ITALIAN  -> List.of("bagno privato", "en suite");
        };
    }

    public List<String> doubleSinkKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("double sink", "twin sink", "his and hers");
            case SPANISH  -> List.of("doble lavabo");
            case GERMAN   -> List.of("doppelwaschbecken");
            case FRENCH   -> List.of("double vasque");
            case ITALIAN  -> List.of("doppio lavabo");
        };
    }

    public List<String> cellarKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("cellar", "storage room", "|cave|");
            case SPANISH  -> List.of("sotano", "trastero", "bodega");
            case GERMAN   -> List.of("keller", "kellerraum", "abstellraum");
            case FRENCH   -> List.of("|cave|", "cellier", "local rangement");
            case ITALIAN  -> List.of("cantina", "ripostiglio", "magazzino");
        };
    }

    public List<String> conciergeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("concierge", "porter", "doorman");
            case SPANISH  -> List.of("conserje", "portero");
            case GERMAN   -> List.of("concierge", "pfortner", "hausmeister");
            case FRENCH   -> List.of("concierge", "gardien");
            case ITALIAN  -> List.of("portiere", "portineria", "concierge");
        };
    }

    public List<String> gymKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|gym|", "fitness");
            case SPANISH  -> List.of("gimnasio");
            case GERMAN   -> List.of("fitnessstudio", "fitnessraum", "|gym|");
            case FRENCH   -> List.of("salle sport", "|gym|");
            case ITALIAN  -> List.of("palestra", "|gym|");
        };
    }

    public List<String> eventRoomKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("meeting room", "lounge", "event room");
            case SPANISH  -> List.of("sala reuniones", "salon eventos", "salon comunal");
            case GERMAN   -> List.of("gemeinschaftsraum", "veranstaltungsraum");
            case FRENCH   -> List.of("salle reunion", "salle commune");
            case ITALIAN  -> List.of("sala riunioni", "sala eventi", "sala comune");
        };
    }

    public List<String> doubleGlazingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("double glaz");
            case SPANISH  -> List.of("doble acristalamiento", "doble cristal");
            case GERMAN   -> List.of("doppelverglasung", "doppelt verglast");
            case FRENCH   -> List.of("double vitrage");
            case ITALIAN  -> List.of("doppio vetro", "doppi vetri");
        };
    }

    public List<String> interphoneKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("intercom", "entry phone", "video phone", "door phone");
            case SPANISH  -> List.of("interfono", "portero automatico", "videoportero");
            case GERMAN   -> List.of("gegensprechanlage", "klingel", "tuersprechanlage");
            case FRENCH   -> List.of("interphone", "visiophone", "parlophone");
            case ITALIAN  -> List.of("citofono", "videocitofono");
        };
    }

    public List<String> alarmKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("alarm", "security system");
            case SPANISH  -> List.of("alarma", "sistema seguridad");
            case GERMAN   -> List.of("alarmanlage", "sicherheitssystem");
            case FRENCH   -> List.of("alarme", "systeme securite");
            case ITALIAN  -> List.of("allarme", "sistema sicurezza");
        };
    }

    // ====================================================================
    //  FURNISH AMENITIES / APPLIANCES
    // ====================================================================

    public List<String> internetKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("internet", "wifi", "wi fi", "broadband", "fibre");
            case SPANISH  -> List.of("internet", "wifi", "fibra optica");
            case GERMAN   -> List.of("internet", "wlan", "wifi", "breitband");
            case FRENCH   -> List.of("internet", "wifi", "fibre optique");
            case ITALIAN  -> List.of("internet", "wifi", "fibra ottica");
        };
    }

    public List<String> dishwasherKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("dishwasher");
            case SPANISH  -> List.of("lavavajillas");
            case GERMAN   -> List.of("geschirrspuler", "spulmaschine");
            case FRENCH   -> List.of("lave vaisselle");
            case ITALIAN  -> List.of("lavastoviglie");
        };
    }

    public List<String> fridgeKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("fridge", "refrigerator");
            case SPANISH  -> List.of("nevera", "frigorifico", "refrigerador");
            case GERMAN   -> List.of("kuhlschrank");
            case FRENCH   -> List.of("refrigerateur", "frigo");
            case ITALIAN  -> List.of("frigorifero", "frigo");
        };
    }

    public List<String> freezerKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("freezer");
            case SPANISH  -> List.of("congelador");
            case GERMAN   -> List.of("gefrierschrank", "tiefkuhlschrank");
            case FRENCH   -> List.of("congelateur");
            case ITALIAN  -> List.of("congelatore");
        };
    }

    public List<String> ovenKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("|oven|");
            case SPANISH  -> List.of("horno");
            case GERMAN   -> List.of("backofen", "|ofen|");
            case FRENCH   -> List.of("|four|");
            case ITALIAN  -> List.of("forno");
        };
    }

    public List<String> washingMachineKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("washing machine", "washer");
            case SPANISH  -> List.of("lavadora");
            case GERMAN   -> List.of("waschmaschine");
            case FRENCH   -> List.of("lave linge", "machine laver");
            case ITALIAN  -> List.of("lavatrice");
        };
    }

    // ====================================================================
    //  HEATING SYSTEMS (specific types beyond central)
    // ====================================================================

    public List<String> gasHeatingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("gas heating", "gas central heating", "gas boiler");
            case SPANISH  -> List.of("calefaccion gas", "caldera gas");
            case GERMAN   -> List.of("gasheizung", "gastherme");
            case FRENCH   -> List.of("chauffage gaz", "chaudiere gaz");
            case ITALIAN  -> List.of("riscaldamento gas", "caldaia gas");
        };
    }

    public List<String> electricHeatingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("electric heating", "electric radiator", "storage heater");
            case SPANISH  -> List.of("calefaccion electric", "radiador electric");
            case GERMAN   -> List.of("elektroheizung", "nachtspeicherheizung");
            case FRENCH   -> List.of("chauffage electrique", "radiateur electrique");
            case ITALIAN  -> List.of("riscaldamento elettrico");
        };
    }

    public List<String> collectiveHeatingKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("communal heating", "collective heating", "district heating");
            case SPANISH  -> List.of("calefaccion comunitaria", "calefaccion colectiva");
            case GERMAN   -> List.of("fernwarme", "zentralheizung gemeinschaft");
            case FRENCH   -> List.of("chauffage collectif");
            case ITALIAN  -> List.of("riscaldamento condominiale", "riscaldamento collettivo");
        };
    }

    // ====================================================================
    //  PROPERTY STATUS / CONDITION
    // ====================================================================

    public List<String> newBuildKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("new build", "new development", "newly built");
            case SPANISH  -> List.of("obra nueva", "nueva construccion");
            case GERMAN   -> List.of("neubau", "neubauprojekt");
            case FRENCH   -> List.of("neuf", "construction neuve", "programme neuf");
            case ITALIAN  -> List.of("nuova costruzione");
        };
    }

    public List<String> goodConditionKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("immaculate", "excellent condition", "pristine");
            case SPANISH  -> List.of("impecable", "excelente estado", "perfecto estado");
            case GERMAN   -> List.of("einwandfrei", "ausgezeichneter zustand", "top zustand");
            case FRENCH   -> List.of("impeccable", "excellent etat", "parfait etat");
            case ITALIAN  -> List.of("impeccabile", "ottimo stato", "perfette condizioni");
        };
    }

    public List<String> renovatedKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("refurbish", "renovated", "recently renovated");
            case SPANISH  -> List.of("reformado", "renovado", "recien reformado");
            case GERMAN   -> List.of("renoviert", "saniert", "modernisiert");
            case FRENCH   -> List.of("renove", "refait neuf", "remis neuf");
            case ITALIAN  -> List.of("ristrutturato", "rinnovato", "appena ristrutturato");
        };
    }

    // ====================================================================
    //  VIEWS
    // ====================================================================

    public List<String> seaViewKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("sea view", "ocean view", "coastal view");
            case SPANISH  -> List.of("vista mar");
            case GERMAN   -> List.of("meerblick", "seeblick");
            case FRENCH   -> List.of("vue mer");
            case ITALIAN  -> List.of("vista mare");
        };
    }

    public List<String> monumentViewKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("palace view", "monument view", "landmark view", "castle view");
            case SPANISH  -> List.of("vista monumento", "vista palacio");
            case GERMAN   -> List.of("denkmalblick", "schlossblick");
            case FRENCH   -> List.of("vue monument", "vue chateau");
            case ITALIAN  -> List.of("vista monumento", "vista palazzo");
        };
    }

    public List<String> mountainViewKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("mountain view", "hill view");
            case SPANISH  -> List.of("vista montana", "vista sierra");
            case GERMAN   -> List.of("bergblick", "gebirgsblick");
            case FRENCH   -> List.of("vue montagne");
            case ITALIAN  -> List.of("vista montagna");
        };
    }

    // ====================================================================
    //  TRANSACTION / OWNERSHIP
    // ====================================================================

    public List<String> leaseholdKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("leasehold", "lease hold");
            case SPANISH  -> List.of("arrendamiento", "concesion");
            case GERMAN   -> List.of("pacht", "erbpacht", "erbbaurecht");
            case FRENCH   -> List.of("bail", "bail emphyteotique");
            case ITALIAN  -> List.of("locazione", "diritto superficie");
        };
    }

    public List<String> freeholdKeywords() {
        return switch (this) {
            case ENGLISH  -> List.of("freehold", "free hold");
            case SPANISH  -> List.of("plena propiedad", "propiedad absoluta");
            case GERMAN   -> List.of("eigentum", "volleigentum");
            case FRENCH   -> List.of("pleine propriete");
            case ITALIAN  -> List.of("piena proprieta");
        };
    }
}
