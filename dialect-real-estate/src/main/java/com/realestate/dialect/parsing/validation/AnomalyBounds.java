package com.realestate.dialect.parsing.validation;

/**
 * Configurable thresholds for anomaly detection in listing data.
 *
 * <p>Each scraper can customize these bounds via its {@code application.yaml}
 * under the {@code scraper.anomaly-bounds.*} prefix. Scrapers in expensive
 * markets can raise ratio limits, while rural scrapers can
 * tighten them.</p>
 *
 * <p>The parser can also override bounds programmatically for a specific
 * city or listing by calling the setters before passing this object to
 * {@link AnomalyDetector}:</p>
 * <pre>
 *   AnomalyBounds cityBounds = realEstateSettings.getAnomalyBounds().copy();
 *   cityBounds.setMaxRatioBuy(300_000); // an expensive city
 *   AnomalyDetector detector = new AnomalyDetector(cityBounds);
 * </pre>
 *
 * <p>Default values are intentionally generous to avoid false positives.
 * Scrapers should tune them based on their market characteristics.</p>
 */
public class AnomalyBounds {

    // ==================== Price bounds ====================

    /** Maximum buy price (absolute). Default: 500,000,000 (500M). */
    private double maxPriceBuy = 500_000_000;

    /** Maximum rent price per month (absolute). Default: 500,000 (500K). */
    private double maxPriceRent = 500_000;

    // ==================== Property bounds ====================

    /** Maximum number of bedrooms. Default: 30. */
    private int maxBedrooms = 30;

    /** Maximum number of bathrooms. Default: 30. */
    private int maxBathrooms = 30;

    /** Maximum surface in m². Default: 100,000 (100K sqm). */
    private int maxSurface = 100_000;

    /** Minimum surface in m². Default: 6. */
    private int minSurface = 6;

    /** Maximum floor number. Default: 200. */
    private int maxFloor = 200;

    /** Maximum total rooms. Default: 100. */
    private int maxRooms = 100;

    /** Maximum property levels/storeys. Default: 20. */
    private int maxLevels = 20;

    // ==================== Ratio bounds (price per sqm) ====================

    /**
     * Maximum price/sqm ratio for BUY.
     * Above this, surface is likely wrong. Default: 200,000 (200K per sqm).
     */
    private double maxRatioBuy = 200_000;

    /**
     * Maximum price/sqm ratio for RENT.
     * Above this, surface is likely wrong. Default: 5,000 (5K per sqm/month).
     */
    private double maxRatioRent = 5_000;

    /**
     * Minimum price/sqm ratio for BUY.
     * Below this, price or surface may be suspicious. Default: 5 per sqm.
     */
    private double minRatioBuy = 5;

    /**
     * Minimum price/sqm ratio for RENT.
     * Below this, price or surface may be suspicious. Default: 1.5 per sqm/month.
     */
    private double minRatioRent = 1.5;

    // ==================== Building bounds ====================

    /** Maximum building height in floors. Default: 200. */
    private int maxBuildingHeight = 200;

    /** Minimum year built. Default: 1000. */
    private int minYearBuilt = 1000;

    /** Maximum year built. Default: 2100. */
    private int maxYearBuilt = 2100;

    /** Maximum land size in m². Default: 10,000,000 (10M sqm). */
    private int maxLandSize = 10_000_000;

    // ==================== Surface fix bounds ====================
    // Used by the enhanced anomaly detector for description-aware surface validation.

    /**
     * Maximum surface resolved from description (general, all types).
     * Default: 45,000. Values above this from description are unreliable.
     */
    private int maxSurfaceFromDescription = 45_000;

    /**
     * Maximum surface resolved from description for floor-related properties (apartments, etc).
     * Default: 20,000. Floor-related properties rarely exceed this.
     */
    private int maxSurfaceFromDescriptionApartment = 20_000;

    /**
     * Maximum land size resolved from description. Default: 50,000.
     */
    private int maxLandFromDescription = 50_000;

    /**
     * Surface threshold below which it's considered too small if combined with high ratio.
     * Default: 15.
     */
    private int surfaceSmallHighRatioThreshold = 15;

    /**
     * Ratio threshold above which a small surface is considered wrong.
     * Default: 40,000.
     */
    private double ratioThresholdForSmallSurface = 40_000;

    /**
     * General minimum surface (used in enhanced fix). Default: 6.
     * Below this for non-commercial is always wrong.
     */
    private int generalMinSurface = 6;

    // ==================== Price fix bounds ====================
    // Used by the enhanced anomaly detector for description-aware price validation.

    /**
     * Minimum price for buy operation. Below this, price is cleared. Default: 1,000.
     */
    private double minPriceBuy = 1000;

    /**
     * Ratio below which a BUY listing may actually be a RENT. Default: 40.
     */
    private double ratioThresholdBuyToRent = 40;

    /**
     * Max ratio for buy operation to consider price too big (normal areas).
     * Default: 50,000. Scrapers in expensive areas should raise this.
     */
    private double maxRatioBuyForPriceFix = 50_000;

    /**
     * Ratio above which rent listing may actually be buy. Default: 500.
     */
    private double ratioThresholdRentToBuy = 500;

    // ==================== Resolution validation bounds ====================
    // Used when validating values resolved from description text.

    /**
     * Minimum ratio for accepting a surface value resolved from description (buy).
     * Below this ratio after resolving surface, the surface is rejected. Default: 150.
     */
    private double minRatioForDescSurfaceBuy = 150;

    /**
     * Minimum ratio for accepting a surface value resolved from description (rent).
     * Below this ratio after resolving surface, the surface is rejected. Default: 5.
     */
    private double minRatioForDescSurfaceRent = 5;

    /**
     * Minimum ratio for accepting a land size resolved from description (buy).
     * Default: 150.
     */
    private double minRatioForDescLandBuy = 150;


    // ==================== Constructors ====================

    /** Creates bounds with all default values. */
    public AnomalyBounds() {}

    /**
     * Creates a deep copy of these bounds, useful for per-city overrides.
     *
     * @return new AnomalyBounds instance with same values
     */
    public AnomalyBounds copy() {
        AnomalyBounds c = new AnomalyBounds();
        c.maxPriceBuy = this.maxPriceBuy;
        c.maxPriceRent = this.maxPriceRent;
        c.maxBedrooms = this.maxBedrooms;
        c.maxBathrooms = this.maxBathrooms;
        c.maxSurface = this.maxSurface;
        c.minSurface = this.minSurface;
        c.maxFloor = this.maxFloor;
        c.maxRooms = this.maxRooms;
        c.maxLevels = this.maxLevels;
        c.maxRatioBuy = this.maxRatioBuy;
        c.maxRatioRent = this.maxRatioRent;
        c.minRatioBuy = this.minRatioBuy;
        c.minRatioRent = this.minRatioRent;
        c.maxBuildingHeight = this.maxBuildingHeight;
        c.minYearBuilt = this.minYearBuilt;
        c.maxYearBuilt = this.maxYearBuilt;
        c.maxLandSize = this.maxLandSize;
        c.maxSurfaceFromDescription = this.maxSurfaceFromDescription;
        c.maxSurfaceFromDescriptionApartment = this.maxSurfaceFromDescriptionApartment;
        c.maxLandFromDescription = this.maxLandFromDescription;
        c.surfaceSmallHighRatioThreshold = this.surfaceSmallHighRatioThreshold;
        c.ratioThresholdForSmallSurface = this.ratioThresholdForSmallSurface;
        c.generalMinSurface = this.generalMinSurface;
        c.minPriceBuy = this.minPriceBuy;
        c.ratioThresholdBuyToRent = this.ratioThresholdBuyToRent;
        c.maxRatioBuyForPriceFix = this.maxRatioBuyForPriceFix;
        c.ratioThresholdRentToBuy = this.ratioThresholdRentToBuy;
        c.minRatioForDescSurfaceBuy = this.minRatioForDescSurfaceBuy;
        c.minRatioForDescSurfaceRent = this.minRatioForDescSurfaceRent;
        c.minRatioForDescLandBuy = this.minRatioForDescLandBuy;
        return c;
    }

    // ==================== Getters & Setters ====================

    public double getMaxPriceBuy() { return maxPriceBuy; }
    public void setMaxPriceBuy(double maxPriceBuy) { this.maxPriceBuy = maxPriceBuy; }

    public double getMaxPriceRent() { return maxPriceRent; }
    public void setMaxPriceRent(double maxPriceRent) { this.maxPriceRent = maxPriceRent; }

    public int getMaxBedrooms() { return maxBedrooms; }
    public void setMaxBedrooms(int maxBedrooms) { this.maxBedrooms = maxBedrooms; }

    public int getMaxBathrooms() { return maxBathrooms; }
    public void setMaxBathrooms(int maxBathrooms) { this.maxBathrooms = maxBathrooms; }

    public int getMaxSurface() { return maxSurface; }
    public void setMaxSurface(int maxSurface) { this.maxSurface = maxSurface; }

    public int getMinSurface() { return minSurface; }
    public void setMinSurface(int minSurface) { this.minSurface = minSurface; }

    public int getMaxFloor() { return maxFloor; }
    public void setMaxFloor(int maxFloor) { this.maxFloor = maxFloor; }

    public int getMaxRooms() { return maxRooms; }
    public void setMaxRooms(int maxRooms) { this.maxRooms = maxRooms; }

    public int getMaxLevels() { return maxLevels; }
    public void setMaxLevels(int maxLevels) { this.maxLevels = maxLevels; }

    public double getMaxRatioBuy() { return maxRatioBuy; }
    public void setMaxRatioBuy(double maxRatioBuy) { this.maxRatioBuy = maxRatioBuy; }

    public double getMaxRatioRent() { return maxRatioRent; }
    public void setMaxRatioRent(double maxRatioRent) { this.maxRatioRent = maxRatioRent; }

    public double getMinRatioBuy() { return minRatioBuy; }
    public void setMinRatioBuy(double minRatioBuy) { this.minRatioBuy = minRatioBuy; }

    public double getMinRatioRent() { return minRatioRent; }
    public void setMinRatioRent(double minRatioRent) { this.minRatioRent = minRatioRent; }

    public int getMaxBuildingHeight() { return maxBuildingHeight; }
    public void setMaxBuildingHeight(int maxBuildingHeight) { this.maxBuildingHeight = maxBuildingHeight; }

    public int getMinYearBuilt() { return minYearBuilt; }
    public void setMinYearBuilt(int minYearBuilt) { this.minYearBuilt = minYearBuilt; }

    public int getMaxYearBuilt() { return maxYearBuilt; }
    public void setMaxYearBuilt(int maxYearBuilt) { this.maxYearBuilt = maxYearBuilt; }

    public int getMaxLandSize() { return maxLandSize; }
    public void setMaxLandSize(int maxLandSize) { this.maxLandSize = maxLandSize; }

    public int getMaxSurfaceFromDescription() { return maxSurfaceFromDescription; }
    public void setMaxSurfaceFromDescription(int v) { this.maxSurfaceFromDescription = v; }

    public int getMaxSurfaceFromDescriptionApartment() { return maxSurfaceFromDescriptionApartment; }
    public void setMaxSurfaceFromDescriptionApartment(int v) { this.maxSurfaceFromDescriptionApartment = v; }

    public int getMaxLandFromDescription() { return maxLandFromDescription; }
    public void setMaxLandFromDescription(int v) { this.maxLandFromDescription = v; }

    public int getSurfaceSmallHighRatioThreshold() { return surfaceSmallHighRatioThreshold; }
    public void setSurfaceSmallHighRatioThreshold(int v) { this.surfaceSmallHighRatioThreshold = v; }

    public double getRatioThresholdForSmallSurface() { return ratioThresholdForSmallSurface; }
    public void setRatioThresholdForSmallSurface(double v) { this.ratioThresholdForSmallSurface = v; }

    public int getGeneralMinSurface() { return generalMinSurface; }
    public void setGeneralMinSurface(int v) { this.generalMinSurface = v; }

    public double getMinPriceBuy() { return minPriceBuy; }
    public void setMinPriceBuy(double v) { this.minPriceBuy = v; }

    public double getRatioThresholdBuyToRent() { return ratioThresholdBuyToRent; }
    public void setRatioThresholdBuyToRent(double v) { this.ratioThresholdBuyToRent = v; }

    public double getMaxRatioBuyForPriceFix() { return maxRatioBuyForPriceFix; }
    public void setMaxRatioBuyForPriceFix(double v) { this.maxRatioBuyForPriceFix = v; }

    public double getRatioThresholdRentToBuy() { return ratioThresholdRentToBuy; }
    public void setRatioThresholdRentToBuy(double v) { this.ratioThresholdRentToBuy = v; }

    public double getMinRatioForDescSurfaceBuy() { return minRatioForDescSurfaceBuy; }
    public void setMinRatioForDescSurfaceBuy(double v) { this.minRatioForDescSurfaceBuy = v; }

    public double getMinRatioForDescSurfaceRent() { return minRatioForDescSurfaceRent; }
    public void setMinRatioForDescSurfaceRent(double v) { this.minRatioForDescSurfaceRent = v; }

    public double getMinRatioForDescLandBuy() { return minRatioForDescLandBuy; }
    public void setMinRatioForDescLandBuy(double v) { this.minRatioForDescLandBuy = v; }
}
