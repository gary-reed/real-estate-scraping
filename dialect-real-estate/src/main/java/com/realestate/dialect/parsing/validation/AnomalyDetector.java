package com.realestate.dialect.parsing.validation;

import com.realestate.dialect.model.RealEstateListing;

import com.scraper.commons.core.model.GeoPoint;
import com.scraper.flavor.cms.model.Location;
import com.scraper.flavor.cms.model.ScrapingDetails;
import com.realestate.dialect.model.ScrapingTaggingEnum;
import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Basic.OperationEnum;
import com.realestate.dialect.model.Basic.SubTypeEnum;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.Property;
import com.realestate.dialect.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects and fixes anomalies in parsed listing data.
 *
 * <p>This class is called at two points in the post-parsing pipeline
 * (see {@link com.realestate.dialect.RealEstateParser#postProcess}):</p>
 * <ol>
 *   <li><b>Before resolution</b> — {@link #detect(RealEstateListing)} resets obviously
 *       invalid field values (e.g. negative price, 500 bedrooms) using configured
 *       {@link AnomalyBounds}.</li>
 *   <li><b>After resolution</b> — {@link #detectWithDescription(RealEstateListing, String)}
 *       runs the same basic checks <em>plus</em> description-aware fixes that detect
 *       subtler errors like surface-equals-price mistakes, missing zeros in price,
 *       or BUY/RENT misclassification from ratio analysis.</li>
 * </ol>
 *
 * <p>Bounds are configurable per-scraper via {@code application.yaml}
 * (see {@link AnomalyBounds}). For city-level overrides, the parser can copy the
 * bounds and adjust before creating the detector:</p>
 * <pre>
 *   AnomalyBounds londonBounds = realEstateSettings.getAnomalyBounds().copy();
 *   londonBounds.setMaxRatioBuy(300_000);
 *   AnomalyDetector detector = new AnomalyDetector(londonBounds);
 * </pre>
 *
 * <p>All check methods are {@code protected} so scrapers can extend this class
 * and override individual checks for site-specific rules.</p>
 */
public class AnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);

    private static final String TAG = "[ANOMALY]";

    /** Configurable thresholds. Never null. */
    private final AnomalyBounds bounds;

    // ==================== Constructors ====================

    /** Creates a detector with default bounds. */
    public AnomalyDetector() {
        this(new AnomalyBounds());
    }

    /**
     * Creates a detector with custom bounds (e.g. from scraper config or city override).
     *
     * @param bounds the thresholds to use for anomaly detection
     */
    public AnomalyDetector(AnomalyBounds bounds) {
        this.bounds = bounds != null ? bounds : new AnomalyBounds();
    }

    /** Returns the current bounds (useful for logging or UI display). */
    public AnomalyBounds getBounds() {
        return bounds;
    }


    // ==================== Pipeline entry points ====================

    /**
     * Runs basic anomaly checks on the listing and resets obviously invalid fields.
     * Called BEFORE description resolution.
     *
     * <p>This only checks absolute bounds (price > 0, surface within range, etc.)
     * and cross-field ratio anomalies. Does NOT do description-aware fixes.</p>
     *
     * @param listing the listing to check
     * @return the same listing with anomalies fixed
     */
    public RealEstateListing detect(RealEstateListing listing) {
        if (listing == null) return null;

        checkBasicAnomalies(listing);
        checkPropertyAnomalies(listing);
        checkTransactionAnomalies(listing);
        checkLocationAnomalies(listing);
        checkBuildingAnomalies(listing);
        checkRatioAnomalies(listing);

        return listing;
    }

    /**
     * Runs ALL anomaly checks including description-aware fixes.
     * Called AFTER description resolution so we can cross-validate resolved values
     * against the description text and detect subtler errors.
     *
     * <p>This includes everything from {@link #detect(RealEstateListing)} plus:</p>
     * <ul>
     *   <li>Surface = price mistake detection and correction</li>
     *   <li>Surface extra-zeros correction (e.g. 150000 → 150)</li>
     *   <li>Surface too small for bedrooms</li>
     *   <li>Price too small for BUY (missing thousands)</li>
     *   <li>BUY/RENT operation misclassification from ratio analysis</li>
     * </ul>
     *
     * @param listing     the listing to check
     * @param description normalized description text (lowercased), or null if not available
     * @return the same listing with anomalies fixed
     */
    public RealEstateListing detectWithDescription(RealEstateListing listing, String description) {
        if (listing == null) return null;

        // First run the basic checks (same as detect())
        checkBasicAnomalies(listing);
        checkPropertyAnomalies(listing);
        checkTransactionAnomalies(listing);
        checkLocationAnomalies(listing);
        checkBuildingAnomalies(listing);
        checkRatioAnomalies(listing);

        // Then run description-aware fixes on surface and price.
        // These detect subtler errors that need description context (like surface = price mistake,
        // or a buy price that is obviously a rent price based on description keywords).
        if (description == null) description = "";
        checkSurfaceAnomaliesWithDescription(listing, description);
        checkPriceAnomaliesWithDescription(listing, description);

        return listing;
    }


    // ==================== Basic anomalies ====================

    /**
     * Checks price for absolute bounds: must be positive and within operation-specific max.
     * Also validates price ratio field independently.
     */
    protected void checkBasicAnomalies(RealEstateListing listing) {
        Basic basic = listing.getBasic();
        if (basic == null) return;

        // Price must be positive
        if (basic.getPrice() != null && basic.getPrice() <= 0) {
            log.warn("{} Price is <= 0: {} - resetting", TAG, basic.getPrice());
            basic.setPrice(null);
        }

        // Price upper bounds depend on operation (BUY vs RENT)
        if (basic.getPrice() != null) {
            OperationEnum op = basic.getOperation();
            double maxPrice = (op == OperationEnum.BUY) ? bounds.getMaxPriceBuy() : bounds.getMaxPriceRent();
            if (basic.getPrice() > maxPrice) {
                log.warn("{} Price {} exceeds max {} for operation {} - resetting",
                        TAG, basic.getPrice(), maxPrice, op);
                basic.setPrice(null);
            }
        }

        // Price ratio sanity check (independent of surface — ratio may be set from external source)
        if (basic.getPriceRatio() != null) {
            OperationEnum op = basic.getOperation();
            double maxRatio = (op == OperationEnum.BUY) ? bounds.getMaxRatioBuy() : bounds.getMaxRatioRent();
            if (basic.getPriceRatio() <= 0 || basic.getPriceRatio() > maxRatio) {
                log.warn("{} PriceRatio {} is invalid - resetting", TAG, basic.getPriceRatio());
                basic.setPriceRatio(null);
            }
        }
    }

    // ==================== Property anomalies ====================

    /**
     * Checks property-level fields (bedrooms, bathrooms, surface, floor, rooms, levels)
     * against configured bounds. Resets any field that is out of range.
     */
    protected void checkPropertyAnomalies(RealEstateListing listing) {
        Property prop = listing.getProperty();
        if (prop == null) return;

        if (prop.getBedrooms() != null && (prop.getBedrooms() < 0 || prop.getBedrooms() > bounds.getMaxBedrooms())) {
            log.warn("{} Bedrooms {} is out of range - resetting", TAG, prop.getBedrooms());
            prop.setBedrooms(null);
        }

        if (prop.getBathrooms() != null && (prop.getBathrooms() < 0 || prop.getBathrooms() > bounds.getMaxBathrooms())) {
            log.warn("{} Bathrooms {} is out of range - resetting", TAG, prop.getBathrooms());
            prop.setBathrooms(null);
        }

        if (prop.getSurface() != null && (prop.getSurface() < bounds.getMinSurface() || prop.getSurface() > bounds.getMaxSurface())) {
            log.warn("{} Surface {} is out of range [{}, {}] - resetting",
                    TAG, prop.getSurface(), bounds.getMinSurface(), bounds.getMaxSurface());
            prop.setSurface(null);
        }

        if (prop.getFloorNr() != null && (prop.getFloorNr() < -2 || prop.getFloorNr() > bounds.getMaxFloor())) {
            log.warn("{} Floor number {} is out of range - resetting", TAG, prop.getFloorNr());
            prop.setFloorNr(null);
        }

        if (prop.getRoomsTotal() != null && (prop.getRoomsTotal() < 0 || prop.getRoomsTotal() > bounds.getMaxRooms())) {
            log.warn("{} Total rooms {} is out of range - resetting", TAG, prop.getRoomsTotal());
            prop.setRoomsTotal(null);
        }

        if (prop.getPropertyLevels() != null && (prop.getPropertyLevels() < 0 || prop.getPropertyLevels() > bounds.getMaxLevels())) {
            log.warn("{} Property levels {} is out of range - resetting", TAG, prop.getPropertyLevels());
            prop.setPropertyLevels(null);
        }
    }

    // ==================== Transaction anomalies ====================

    /** Checks term prices (long, mid, short) are positive when set. */
    protected void checkTransactionAnomalies(RealEstateListing listing) {
        Transaction txn = listing.getTransaction();
        if (txn == null) return;

        if (txn.getlTerm() != null && txn.getlTerm() <= 0) {
            log.warn("{} Long term price {} is <= 0 - resetting", TAG, txn.getlTerm());
            txn.setlTerm(null);
        }
        if (txn.getmTerm() != null && txn.getmTerm() <= 0) {
            log.warn("{} Mid term price {} is <= 0 - resetting", TAG, txn.getmTerm());
            txn.setmTerm(null);
        }
        if (txn.getsTerm() != null && txn.getsTerm() <= 0) {
            log.warn("{} Short term price {} is <= 0 - resetting", TAG, txn.getsTerm());
            txn.setsTerm(null);
        }
    }

    // ==================== Location anomalies ====================

    /** Validates latitude [-90, 90] and longitude [-180, 180]. */
    protected void checkLocationAnomalies(RealEstateListing listing) {
        Location loc = listing.getLocation();
        if (loc == null || loc.getGeopoint() == null) return;

        GeoPoint geo = loc.getGeopoint();

        if (geo.getLatitude() != null) {
            if (geo.getLatitude() < -90 || geo.getLatitude() > 90) {
                log.warn("{} Latitude {} is out of range - resetting", TAG, geo.getLatitude());
                geo.setLatitude(null);
            }
        }
        if (geo.getLongitude() != null) {
            if (geo.getLongitude() < -180 || geo.getLongitude() > 180) {
                log.warn("{} Longitude {} is out of range - resetting", TAG, geo.getLongitude());
                geo.setLongitude(null);
            }
        }
    }

    // ==================== Building anomalies ====================

    /** Checks building-level fields (height, year built, land size) against bounds. */
    protected void checkBuildingAnomalies(RealEstateListing listing) {
        Building bld = listing.getBuilding();
        if (bld == null) return;

        if (bld.getHeight() != null && (bld.getHeight() < 0 || bld.getHeight() > bounds.getMaxBuildingHeight())) {
            log.warn("{} Building floors {} is out of range - resetting", TAG, bld.getHeight());
            bld.setHeight(null);
        }

        if (bld.getBuildingAge() != null && (bld.getBuildingAge() < bounds.getMinYearBuilt() || bld.getBuildingAge() > bounds.getMaxYearBuilt())) {
            log.warn("{} Year built {} is out of range - resetting", TAG, bld.getBuildingAge());
            bld.setBuildingAge(null);
        }

        if (bld.getLandSz() != null && (bld.getLandSz() < 0 || bld.getLandSz() > bounds.getMaxLandSize())) {
            log.warn("{} Land size {} is out of range - resetting", TAG, bld.getLandSz());
            bld.setLandSz(null);
        }
    }

    // ==================== Ratio cross-validation ====================

    /**
     * Cross-validates price vs surface using ratio checks.
     * If both price and surface are present, calculates the price/sqm ratio
     * and resets surface if the ratio is impossibly high.
     * Logs a warning (without resetting) if the ratio is suspiciously low.
     */
    protected void checkRatioAnomalies(RealEstateListing listing) {
        Basic basic = listing.getBasic();
        Property prop = listing.getProperty();
        if (basic == null || prop == null) return;
        if (basic.getPrice() == null || prop.getSurface() == null || prop.getSurface() <= 0) return;

        // Calculate ratio (price per sqm)
        listing.calculateAndSetPriceRatio();
        Double ratio = basic.getPriceRatio();
        if (ratio == null) return;

        boolean isBuy = basic.isBuy();
        double maxRatio = isBuy ? bounds.getMaxRatioBuy() : bounds.getMaxRatioRent();
        double minRatio = isBuy ? bounds.getMinRatioBuy() : bounds.getMinRatioRent();

        if (ratio > maxRatio) {
            // Ratio impossibly high → surface is probably wrong (too small or garbage value)
            log.warn("{} Price/surface ratio {} exceeds max {} for {} - surface {} likely wrong, resetting",
                    TAG, String.format("%.1f", ratio), maxRatio, isBuy ? "BUY" : "RENT", prop.getSurface());
            prop.setSurface(null);
            basic.setPriceRatio(null);
        } else if (ratio < minRatio) {
            // Ratio suspiciously low — could be genuine cheap property or wrong data.
            // Don't reset, just log for review.
            log.warn("{} Price/surface ratio {} below min {} for {} - price {} surface {} may be suspicious",
                    TAG, String.format("%.2f", ratio), minRatio, isBuy ? "BUY" : "RENT",
                    basic.getPrice(), prop.getSurface());
        }
    }


    // ==================== Description-aware surface fixes ====================

    /**
     * Description-aware anomaly checks for surface. Detects and corrects common mistakes
     * that require looking at the description text or cross-referencing multiple fields:
     * <ul>
     *   <li>Surface equals price (common parsing mistake on some sites)</li>
     *   <li>Surface has extra zeros (e.g. 150000 instead of 150) — corrected if confirmed in description</li>
     *   <li>Small surface with very high ratio (likely wrong number was captured)</li>
     *   <li>Surface too small for number of bedrooms</li>
     *   <li>Apartment with unrealistically big surface and low ratio</li>
     *   <li>Surface below general minimum (< 6 for non-commercial)</li>
     * </ul>
     *
     * @param listing     the listing to check
     * @param description normalized description text (lowercased)
     */
    protected void checkSurfaceAnomaliesWithDescription(RealEstateListing listing, String description) {
        if (listing.getProperty().getSurface() == null) return;

        Integer surface = listing.getProperty().getSurface().intValue();
        Double price = listing.getBasic().getPrice();
        listing.calculateAndSetPriceRatio();
        Double ratio = listing.getBasic().getPriceRatio();
        Integer amntBdrms = listing.getProperty().getBedrooms();

        boolean isBuy = listing.getBasic().isBuy();
        boolean isCommercial = listing.getBasic().isCommercialType();
        boolean isShare = listing.getBasic().getOperation() != null
                && listing.getBasic().getOperation().equals(OperationEnum.SHARE);
        boolean hasLand = listing.getBuilding() != null && listing.getBuilding().getLandSz() != null;
        boolean hasLowRatio = isSuspiciousSmallRatioBuy(listing, bounds.getMinRatioBuy())
                || isSuspiciousSmallRatioRent(listing, bounds.getMinRatioRent());

        // --- Case 1: Surface equals price (common mistake on some sites where price field
        //     gets the surface value or vice versa). We decide which one to reset based on
        //     the ratio: if price is very low (< 150) we reset price; if surface is very
        //     large (> 400) we reset surface. ---
        if (hasLowRatio && !hasLand && !isCommercial
                && price != null && price.intValue() == surface) {
            if (price < 150) {
                listing.getBasic().setPrice((Double) null);
                listing.calculateAndSetPriceRatio();
                log.debug("{} Surface equals price ({}) — price too low for rent, reset price", TAG, price);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_PRICE_EQUALS_SURFACE_RENT);
            } else if (surface > 400) {
                listing.getProperty().setSurface((Integer) null);
                listing.calculateAndSetPriceRatio();
                log.debug("{} Surface equals price ({}) — surface too big, reset surface", TAG, surface);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_EQUALS_PRICE_RENT);
            }
        }

        // Re-read surface in case it was just cleared
        surface = listing.getProperty().getSurface() != null ? listing.getProperty().getSurface().intValue() : null;
        if (surface == null) return;

        // --- Case 2: Surface > 1000 with suspicious low ratio. Could be:
        //     (a) Surface equals price — reset surface.
        //     (b) Surface has extra zeros (150000 → 150) — fix if confirmed in description.
        //     (c) Neither — reset surface if no land associated. ---
        if (hasLowRatio && surface > 1000) {
            if (price != null && price.intValue() == surface) {
                listing.getProperty().setSurface((Integer) null);
                listing.calculateAndSetPriceRatio();
                log.debug("{} Surface > 1000 and equals price ({}) — reset surface", TAG, surface);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_SAME_AS_PRICE);
                return;
            }

            // Check if the surface value itself appears in description (confirming it's correct)
            if (description.contains(surface + " ") || description.contains(surface + "m")
                    || description.contains(Integer.toString(surface))) {
                return; // Surface is confirmed by description text, keep it
            }

            // Try dividing by 1000 and check if that reduced value appears in description
            int surfaceFixed = Double.valueOf(surface / 1000d).intValue();
            String surfaceReduced = String.valueOf(surfaceFixed);
            if (surfaceFixed > 25
                    && (description.contains(surfaceReduced + " ")
                        || description.contains(surfaceReduced + "m"))) {
                listing.getProperty().setSurface(surfaceFixed);
                listing.calculateAndSetPriceRatio();
                log.debug("{} Surface had extra zeros ({} → {})", TAG, surface, surfaceFixed);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_FIXED_SURFACE_EXTRA_ZEROS);
                return;
            } else if (!hasLand) {
                listing.getProperty().setSurface((Integer) null);
                listing.calculateAndSetPriceRatio();
                log.debug("{} Surface {} with suspicious ratio, not in description — reset", TAG, surface);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_SUSPICIOUS_RATIO);
                return;
            }
        }

        // Re-read after potential changes
        surface = listing.getProperty().getSurface() != null ? listing.getProperty().getSurface().intValue() : null;
        ratio = listing.getBasic().getPriceRatio();
        if (surface == null) return;

        // --- Case 3: Small surface with disproportionately high ratio.
        //     E.g. surface=10, price=500000 → ratio=50000. The 10 is likely a bedroom count
        //     or some other misparse, not actual m². ---
        if (surface < bounds.getSurfaceSmallHighRatioThreshold()
                && ratio != null && ratio > bounds.getRatioThresholdForSmallSurface()) {
            listing.getProperty().setSurface((Integer) null);
            listing.calculateAndSetPriceRatio();
            log.debug("{} Small surface {} with very high ratio {} — reset surface", TAG, surface, ratio);
            listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_SMALL_HIGH_RATIO);
            return;
        }

        // --- Case 4: Surface too small for the number of bedrooms.
        //     Minimum m² per bedroom: 1-bed ≥ 28, 2-bed ≥ 47, 3-bed ≥ 56 ---
        if (amntBdrms != null) {
            boolean tooSmallForBeds = (surface < 28 && amntBdrms > 1)
                    || (surface < 47 && amntBdrms > 2)
                    || (surface < 56 && amntBdrms > 3);
            if (tooSmallForBeds) {
                listing.getProperty().setSurface((Integer) null);
                listing.calculateAndSetPriceRatio();
                log.debug("{} Surface {} too small for {} bedrooms — reset surface", TAG, surface, amntBdrms);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_TOO_SMALL_FOR_BEDROOMS);
                return;
            }
        }

        // --- Case 5: Surface too small (below general minimum), non-commercial ---
        if (surface < bounds.getGeneralMinSurface() && !isCommercial) {
            listing.getProperty().setSurface((Integer) null);
            listing.calculateAndSetPriceRatio();
            log.debug("{} Surface {} below min {} for non-commercial — reset", TAG, surface, bounds.getGeneralMinSurface());
            listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_TOO_SMALL);
            return;
        }

        // --- Case 6: Apartment with unrealistically big surface and low ratio.
        //     An apartment with 2000+ m² at < 5/sqm is almost certainly a data error. ---
        SubTypeEnum subType = listing.getBasic().getPropertySubType();
        if (!isShare && subType != null
                && subType.equals(SubTypeEnum.RES_APARTMENT)
                && surface > 2000 && ratio != null && ratio < 5) {
            listing.getProperty().setSurface((Integer) null);
            listing.calculateAndSetPriceRatio();
            log.debug("{} Apartment surface {} with ratio {} — unrealistic, reset", TAG, surface, ratio);
            listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_APARTMENT_UNREALISTIC);
        }

        // --- Case 7: Surface resolved from description with bad ratio.
        //     Check if GENERIC_RESOLVED_SURFACE_FROM_DESC tag is present; if so, apply stricter
        //     ratio bounds since description-resolved surfaces are less reliable. ---
        if (listing.getScrapingDetails().hasTag(ScrapingTaggingEnum.GENERIC_RESOLVED_SURFACE_FROM_DESC)) {
            ratio = listing.getBasic().getPriceRatio();
            if (ratio != null) {
                double maxBuyRatio = bounds.getMaxRatioBuyForPriceFix();
                double maxRentRatio = bounds.getRatioThresholdRentToBuy();
                boolean badRatio = (isBuy && ratio > maxBuyRatio) || (!isBuy && ratio > maxRentRatio);
                if (badRatio) {
                    listing.getProperty().setSurface((Integer) null);
                    listing.calculateAndSetPriceRatio();
                    log.debug("{} Desc-resolved surface with bad ratio {} — reset", TAG, ratio);
                    listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_SURFACE_DESC_BAD_RATIO);
                }
            }
        }
    }


    // ==================== Description-aware price fixes ====================

    /**
     * Description-aware anomaly checks for price. Detects and corrects:
     * <ul>
     *   <li>BUY price too small (< minPriceBuy) — may be missing thousands (000)</li>
     *   <li>BUY with very low ratio is actually RENT</li>
     *   <li>BUY price disproportionately big for the surface</li>
     *   <li>RENT with very high ratio — yearly rent or actually BUY</li>
     * </ul>
     *
     * @param listing     the listing to check
     * @param description normalized description text (lowercased)
     */
    protected void checkPriceAnomaliesWithDescription(RealEstateListing listing, String description) {
        boolean isBuy = listing.getBasic().isBuy();
        Double price = listing.getBasic().getPrice();
        listing.calculateAndSetPriceRatio();
        Double ratio = listing.getBasic().getPriceRatio();
        Integer surface = listing.getProperty().getSurface();

        // --- Case 1: BUY price below minimum (< 1000 by default).
        //     Check if the description mentions "000" near the price value, which suggests
        //     the scraper missed the trailing zeros. If found, multiply by 1000; else reset. ---
        if (isBuy && price != null && price < bounds.getMinPriceBuy()) {
            if (description.contains("000")) {
                int indexPrice = description.indexOf(String.valueOf(price));
                int indexZeros = description.indexOf("000");
                // If the price and "000" are close together in the description, likely missing zeros
                if (indexPrice >= 0 && Math.abs(indexPrice - indexZeros) < 15) {
                    price = price * 1000;
                    listing.getBasic().setPrice(price);
                    listing.calculateAndSetPriceRatio();
                    log.debug("{} BUY price missing thousands zeros — corrected to {}", TAG, price);
                    listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_FIXED_PRICE_MISSING_ZEROS);
                    return;
                }
            }

            // Couldn't fix — price is just too small for BUY. Reset.
            listing.getBasic().setPrice((Double) null);
            listing.calculateAndSetPriceRatio();
            log.debug("{} BUY price {} too small — reset", TAG, price);
            listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_PRICE_BUY_TOO_SMALL);
            return;
        }

        // --- Case 2: BUY with very low ratio → probably actually a RENT listing.
        //     If the description contains rent-related keywords ("rent", "month") and the
        //     price is in a typical rental range (100 - 100000), change operation to RENT. ---
        if (isBuy && ratio != null && ratio < bounds.getRatioThresholdBuyToRent()) {
            if (description.contains(" rent") || description.contains("rent ")
                    || description.contains(" month")) {
                if (price != null && price > 100 && price < 100000) {
                    listing.getBasic().setOperation(OperationEnum.RENT);
                    listing.getTransaction().setOwnership(null);
                    log.debug("{} BUY with low ratio {} and rental keywords — changed to RENT", TAG, ratio);
                    listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_CHANGED_OPERATION_TO_RENT);
                    return;
                }
            }
        }

        // --- Case 3: BUY with tiny ratio AND tiny price — both suggest bad data. Reset price. ---
        if (isBuy && ratio != null && ratio < 7 && price != null && price < bounds.getMinPriceBuy()) {
            listing.getBasic().setPrice((Double) null);
            listing.calculateAndSetPriceRatio();
            log.debug("{} BUY price {} with tiny ratio {} — reset price", TAG, price, ratio);
            listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_PRICE_TINY_LOW_RATIO);
            return;
        }

        // --- Case 4: BUY price disproportionately big for the surface.
        //     Ratio above maxRatioBuyForPriceFix with surface > 15 means price is inflated/wrong. ---
        if (isBuy && ratio != null && ratio > bounds.getMaxRatioBuyForPriceFix()
                && surface != null && surface > 15) {
            listing.getBasic().setPrice((Double) null);
            listing.calculateAndSetPriceRatio();
            log.debug("{} BUY price too big (ratio {}) — reset price", TAG, ratio);
            listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_RESET_PRICE_BUY_TOO_BIG);
            return;
        }

        // --- Case 5: RENT with very high ratio → could be yearly rent or misclassified BUY.
        //     If commercial + "yearl" in description, divide price by 12 for monthly.
        //     Otherwise, change operation to BUY. ---
        if (!isBuy && ratio != null && ratio > bounds.getRatioThresholdRentToBuy()
                && surface != null && surface > 15) {
            if (listing.getBasic().isCommercialType() && description.contains("yearl")) {
                listing.getBasic().setPrice(price / 12);
                listing.calculateAndSetPriceRatio();
                log.debug("{} RENT commercial yearly price — divided by 12", TAG);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_FIXED_PRICE_YEARLY_TO_MONTHLY);
            } else {
                listing.getBasic().setOperation(OperationEnum.BUY);
                log.debug("{} RENT with very high ratio {} — changed to BUY", TAG, ratio);
                listing.getScrapingDetails().addTag(ScrapingTaggingEnum.GENERIC_CHANGED_OPERATION_TO_BUY);
            }
        }
    }


    // ==================== Static ratio-based suspicion checks ====================
    // Used by TextResolutionUtils during resolution to decide whether to keep
    // a value just resolved from description text. These don't reset anything;
    // they just return true/false so the caller can decide.

    /** Returns true if the BUY price/sqm ratio is above the given max — meaning surface is probably too small. */
    public static boolean isSuspiciousBigRatioBuy(RealEstateListing listing, double maxRatioVal) {
        listing.calculateAndSetPriceRatio();
        Double ratio = listing.getBasic().getPriceRatio();
        return isSuspiciousBigRatioBuy(listing, ratio, maxRatioVal);
    }

    public static boolean isSuspiciousBigRatioBuy(RealEstateListing listing, Double ratio, double maxRatioVal) {
        return listing.getBasic().isBuy() && ratio != null && ratio > maxRatioVal;
    }

    /** Returns true if the BUY price/sqm ratio is below the given min — meaning surface or price may be wrong. */
    public static boolean isSuspiciousSmallRatioBuy(RealEstateListing listing, double minRatioVal) {
        listing.calculateAndSetPriceRatio();
        Double ratio = listing.getBasic().getPriceRatio();
        return isSuspiciousSmallRatioBuy(listing, ratio, minRatioVal);
    }

    public static boolean isSuspiciousSmallRatioBuy(RealEstateListing listing, Double ratio, double minRatioVal) {
        return listing.getBasic().isBuy() && ratio != null && ratio < minRatioVal;
    }

    /** Returns true if the RENT price/sqm ratio is above the given max. */
    public static boolean isSuspiciousBigRatioRent(RealEstateListing listing, double maxRatioVal) {
        listing.calculateAndSetPriceRatio();
        Double ratio = listing.getBasic().getPriceRatio();
        return isSuspiciousBigRatioRent(listing, ratio, maxRatioVal);
    }

    public static boolean isSuspiciousBigRatioRent(RealEstateListing listing, Double ratio, double maxRatioVal) {
        return listing.getBasic().isRent() && ratio != null && ratio > maxRatioVal;
    }

    /** Returns true if the RENT price/sqm ratio is below the given min. */
    public static boolean isSuspiciousSmallRatioRent(RealEstateListing listing, double minRatioVal) {
        listing.calculateAndSetPriceRatio();
        Double ratio = listing.getBasic().getPriceRatio();
        return isSuspiciousSmallRatioRent(listing, ratio, minRatioVal);
    }

    public static boolean isSuspiciousSmallRatioRent(RealEstateListing listing, Double ratio, double minRatioVal) {
        return listing.getBasic().isRent() && ratio != null && ratio < minRatioVal;
    }
}
