package com.realestate.dialect.model;

import com.scraper.commons.core.model.SerializableEnum;

import java.util.Map;



/**
 * Enum for tagging specific fixes or resolutions applied to a listing during scraping.
 *
 * <p><b>Naming convention:</b></p>
 * <ul>
 *   <li><b>GENERIC_</b> — Applied by common/shared code (AnomalyDetector, resolvers, TextResolutionUtils).
 *       Every scraper produces these tags. Searching the DB for one of these tags shows listings
 *       where the same fix was applied across all scrapers.</li>
 *   <li><b>SCRAPER_</b> — Applied by scraper-specific code. Meaning depends on which scraper used the tag.
 *       Reserved for use in each scraper's parser class.</li>
 *   <li><b>RESET</b> — An existing field value was cleared to null because it was detected as wrong.</li>
 *   <li><b>FIXED</b> — An existing field value was corrected to a different (non-null) value.</li>
 *   <li><b>RESOLVED</b> — A missing field was filled from description or other text source.</li>
 *   <li><b>CHANGED</b> — The operation type (BUY/RENT) was changed because of a detected mismatch.</li>
 * </ul>
 *
 * <p>Integer values are stable identifiers stored in the database. Never change them.</p>
 */
public enum ScrapingTaggingEnum implements SerializableEnum {

	// ==================== GENERIC: Operation changes (detected by AnomalyDetector) ====================

	/** BUY listing actually looks like RENT (low ratio + rental keywords in description). Operation changed to RENT. */
	GENERIC_CHANGED_OPERATION_TO_RENT("GENERIC_CHANGED_OPERATION_TO_RENT", 1),

	/** RENT listing ratio is too high — likely BUY. Operation changed to BUY. */
	GENERIC_CHANGED_OPERATION_TO_BUY("GENERIC_CHANGED_OPERATION_TO_BUY", 2),

	/** Scraper-specific operation fix (reserved for scraper use). */
	SCRAPER_FIXED_OPERATION_3("SCRAPER_FIXED_OPERATION_3", 3),

	/** Scraper-specific operation fix (reserved for scraper use). */
	SCRAPER_FIXED_OPERATION_4("SCRAPER_FIXED_OPERATION_4", 4),

	// ==================== SCRAPER: Type changes ====================

	/** Scraper-specific type fix. */
	SCRAPER_FIXED_TYPE_1("SCRAPER_FIXED_TYPE_1", 12),

	SCRAPER_FIXED_TYPE_2("SCRAPER_FIXED_TYPE_2", 22),

	SCRAPER_FIXED_TYPE_3("SCRAPER_FIXED_TYPE_3", 23),

	SCRAPER_FIXED_TYPE_4("SCRAPER_FIXED_TYPE_4", 24),

	// ==================== GENERIC: Price anomaly fixes (detected by AnomalyDetector) ====================

	/** BUY price too small, missing thousands zeros. Price corrected (multiplied by 1000). */
	GENERIC_FIXED_PRICE_MISSING_ZEROS("GENERIC_FIXED_PRICE_MISSING_ZEROS", 301),

	/** BUY price too small but couldn't be corrected. Price reset to null. */
	GENERIC_RESET_PRICE_BUY_TOO_SMALL("GENERIC_RESET_PRICE_BUY_TOO_SMALL", 302),

	/** Surface equals price by mistake on RENT listing with low price. Price reset to null. */
	GENERIC_RESET_PRICE_EQUALS_SURFACE_RENT("GENERIC_RESET_PRICE_EQUALS_SURFACE_RENT", 303),

	/** BUY price ratio too high — price is disproportionately big vs surface. Price reset to null. */
	GENERIC_RESET_PRICE_BUY_TOO_BIG("GENERIC_RESET_PRICE_BUY_TOO_BIG", 304),

	/** RENT price with yearly amount — divided by 12 to get monthly. Price fixed. */
	GENERIC_FIXED_PRICE_YEARLY_TO_MONTHLY("GENERIC_FIXED_PRICE_YEARLY_TO_MONTHLY", 307),

	/** BUY price tiny with very low ratio. Price reset to null. */
	GENERIC_RESET_PRICE_TINY_LOW_RATIO("GENERIC_RESET_PRICE_TINY_LOW_RATIO", 308),

	/** Scraper-specific price fix (reserved for scraper use). */
	SCRAPER_FIXED_PRICE_5("SCRAPER_FIXED_PRICE_5", 305),

	SCRAPER_FIXED_PRICE_6("SCRAPER_FIXED_PRICE_6", 306),

	SCRAPER_FIXED_PRICE_9("SCRAPER_FIXED_PRICE_9", 309),

	SCRAPER_FIXED_PRICE_10("SCRAPER_FIXED_PRICE_10", 310),

	SCRAPER_FIXED_PRICE_11("SCRAPER_FIXED_PRICE_11", 311),

	SCRAPER_FIXED_PRICE_12("SCRAPER_FIXED_PRICE_12", 312),

	// ==================== GENERIC: Surface anomaly fixes (detected by AnomalyDetector) ====================

	/** Surface > 1000 and equals price — clearly a parsing mistake. Surface reset to null. */
	GENERIC_RESET_SURFACE_SAME_AS_PRICE("GENERIC_RESET_SURFACE_SAME_AS_PRICE", 401),

	/** Surface had extra zeros (e.g. 150000 instead of 150). Corrected by dividing by 1000. */
	GENERIC_FIXED_SURFACE_EXTRA_ZEROS("GENERIC_FIXED_SURFACE_EXTRA_ZEROS", 402),

	/** Surface > 1000 with suspicious ratio, not confirmed in description, no land. Surface reset to null. */
	GENERIC_RESET_SURFACE_SUSPICIOUS_RATIO("GENERIC_RESET_SURFACE_SUSPICIOUS_RATIO", 403),

	/** Surface < 6 m² for non-commercial. Impossibly small. Surface reset to null. */
	GENERIC_RESET_SURFACE_TOO_SMALL("GENERIC_RESET_SURFACE_TOO_SMALL", 404),

	/** Apartment with surface > 2000 m² and very low ratio. Clearly a mistake. Surface reset to null. */
	GENERIC_RESET_SURFACE_APARTMENT_UNREALISTIC("GENERIC_RESET_SURFACE_APARTMENT_UNREALISTIC", 405),

	/** Scraper-specific surface fix (reserved for scraper use). */
	SCRAPER_FIXED_SURFACE_6("SCRAPER_FIXED_SURFACE_6", 406),

	/** Surface equals price on RENT listing. Surface too big. Surface reset to null. */
	GENERIC_RESET_SURFACE_EQUALS_PRICE_RENT("GENERIC_RESET_SURFACE_EQUALS_PRICE_RENT", 407),

	/** Small surface (< 15 m²) with very high ratio (> 40K/sqm). Surface likely wrong. Surface reset to null. */
	GENERIC_RESET_SURFACE_SMALL_HIGH_RATIO("GENERIC_RESET_SURFACE_SMALL_HIGH_RATIO", 408),

	/** Surface too small for the number of bedrooms (e.g. 28 m² with 2 beds). Surface reset to null. */
	GENERIC_RESET_SURFACE_TOO_SMALL_FOR_BEDROOMS("GENERIC_RESET_SURFACE_TOO_SMALL_FOR_BEDROOMS", 409),

	/** Surface resolved from description but ratio is too high. Surface reset to null. */
	GENERIC_RESET_SURFACE_DESC_BAD_RATIO("GENERIC_RESET_SURFACE_DESC_BAD_RATIO", 410),

	// ==================== SCRAPER: Other fixes (reserved for scraper use) ====================

	SCRAPER_FIXED_OTHER_1("SCRAPER_FIXED_OTHER_1", 501),

	SCRAPER_FIXED_OTHER_2("SCRAPER_FIXED_OTHER_2", 502),

	SCRAPER_FIXED_OTHER_3("SCRAPER_FIXED_OTHER_3", 503),

	SCRAPER_FIXED_OTHER_4("SCRAPER_FIXED_OTHER_4", 504),

	SCRAPER_FIXED_OTHER_5("SCRAPER_FIXED_OTHER_5", 505),

	SCRAPER_FIXED_OTHER_6("SCRAPER_FIXED_OTHER_6", 506),

	SCRAPER_FIXED_OTHER_7("SCRAPER_FIXED_OTHER_7", 507),

	SCRAPER_FIXED_OTHER_8("SCRAPER_FIXED_OTHER_8", 508),

	SCRAPER_FIXED_OTHER_9("SCRAPER_FIXED_OTHER_9", 509),

	// ==================== GENERIC: Resolved from description (set by TextResolutionUtils) ====================

	/** Surface (m²) resolved from description text using area-unit keywords. */
	GENERIC_RESOLVED_SURFACE_FROM_DESC("GENERIC_RESOLVED_SURFACE_FROM_DESC", 601),

	/** Land size resolved from description using acre/hectare keywords. */
	GENERIC_RESOLVED_LAND_ACRE_FROM_DESC("GENERIC_RESOLVED_LAND_ACRE_FROM_DESC", 602),

	/** Land size resolved from description using plot/land area keywords. */
	GENERIC_RESOLVED_LAND_PLOT_FROM_DESC("GENERIC_RESOLVED_LAND_PLOT_FROM_DESC", 603),

	/** Bathrooms count resolved from description text. */
	GENERIC_RESOLVED_BATHROOMS_FROM_DESC("GENERIC_RESOLVED_BATHROOMS_FROM_DESC", 604),

	/** Penthouse amenity resolved from description (floor > 2 + penthouse keyword). */
	GENERIC_RESOLVED_PENTHOUSE_FROM_DESC("GENERIC_RESOLVED_PENTHOUSE_FROM_DESC", 605),

	/** Floor number resolved from description text using ordinal/floor keywords. */
	GENERIC_RESOLVED_FLOOR_FROM_DESC("GENERIC_RESOLVED_FLOOR_FROM_DESC", 606),

	/** Storeys (house-like) resolved from description text. */
	GENERIC_RESOLVED_STOREYS_FROM_DESC("GENERIC_RESOLVED_STOREYS_FROM_DESC", 607),

	/** Generic description resolution (reserved for future use). */
	GENERIC_RESOLVED_OTHER_FROM_DESC("GENERIC_RESOLVED_OTHER_FROM_DESC", 608),

	/** Building height (number of floors) resolved from description text. */
	GENERIC_RESOLVED_BUILDING_HEIGHT_FROM_DESC("GENERIC_RESOLVED_BUILDING_HEIGHT_FROM_DESC", 609),

	/** Building age resolved from description text. */
	GENERIC_RESOLVED_BUILDING_AGE_FROM_DESC("GENERIC_RESOLVED_BUILDING_AGE_FROM_DESC", 610),

	/** Generic description resolution (reserved for future use). */
	GENERIC_RESOLVED_OTHER_FROM_DESC_2("GENERIC_RESOLVED_OTHER_FROM_DESC_2", 610),

	// ==================== GENERIC: Free-use marks for scraper review ====================
	// Can be used by any scraper to mark edge cases for DB review.

	GENERIC_MARK_1("GENERIC_MARK_1", 801),

	GENERIC_MARK_2("GENERIC_MARK_2", 802),

	GENERIC_MARK_3("GENERIC_MARK_3", 803),

	GENERIC_MARK_4("GENERIC_MARK_4", 804),

	GENERIC_MARK_5("GENERIC_MARK_5", 805),

	GENERIC_MARK_6("GENERIC_MARK_6", 806),

	GENERIC_MARK_7("GENERIC_MARK_7", 807),

	GENERIC_MARK_8("GENERIC_MARK_8", 808),

	GENERIC_MARK_9("GENERIC_MARK_9", 809),

	// ==================== Location cluster tags ====================

	/** Listing is in a recognized location cluster — coordinates may be approximate.
	 *  Set by LocationClustersLogic at end of run; isPrecise set to false. */
	GENERIC_LOCATION_CLUSTER("GENERIC_LOCATION_CLUSTER", 901),

	/** Listing originally had isPrecise=true before cluster logic changed it to false.
	 *  Kept in case the original precision needs to be recovered. */
	GENERIC_LOCATION_WAS_PRECISE("GENERIC_LOCATION_WAS_PRECISE", 902),

	;


	private static final Map<Integer, ScrapingTaggingEnum> BY_VALUE = SerializableEnum.buildValueMap(values());

	private final String name;
	private final int value;

	ScrapingTaggingEnum(String name, int value) {
		this.name = name;
		this.value = value;
	}

	@Override
	public String getName() { return name; }

	@Override
	public int getValue() { return value; }

	public static ScrapingTaggingEnum valueOf(int value) { return BY_VALUE.get(value); }


}
