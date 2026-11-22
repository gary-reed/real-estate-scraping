package com.realestate.dialect;

import com.realestate.dialect.parsing.validation.AnomalyBounds;
import org.springframework.beans.factory.annotation.Value;

/**
 * Real-estate settings read from the scraper's {@code application.yaml}.
 *
 * <p>Today it holds the anomaly-detection bounds, under {@code scraper.anomaly-bounds.*}.
 * A parser uses them by overriding {@link RealEstateParser#getAnomalyBounds()}:</p>
 * <pre>
 *   public AnomalyBounds getAnomalyBounds() { return realEstateSettings.getAnomalyBounds(); }
 * </pre>
 */
public class RealEstateSettings {


    @Value("${scraper.anomaly-bounds.max-price-buy:500000000}")
    private volatile double abMaxPriceBuy;
    @Value("${scraper.anomaly-bounds.max-price-rent:500000}")
    private volatile double abMaxPriceRent;
    @Value("${scraper.anomaly-bounds.max-bedrooms:30}")
    private volatile int abMaxBedrooms;
    @Value("${scraper.anomaly-bounds.max-bathrooms:30}")
    private volatile int abMaxBathrooms;
    @Value("${scraper.anomaly-bounds.max-surface:100000}")
    private volatile int abMaxSurface;
    @Value("${scraper.anomaly-bounds.min-surface:3}")
    private volatile int abMinSurface;
    @Value("${scraper.anomaly-bounds.max-ratio-buy:200000}")
    private volatile double abMaxRatioBuy;
    @Value("${scraper.anomaly-bounds.max-ratio-rent:5000}")
    private volatile double abMaxRatioRent;
    @Value("${scraper.anomaly-bounds.min-ratio-buy:5}")
    private volatile double abMinRatioBuy;
    @Value("${scraper.anomaly-bounds.min-ratio-rent:1.5}")
    private volatile double abMinRatioRent;
    @Value("${scraper.anomaly-bounds.max-floor:200}")
    private volatile int abMaxFloor;
    @Value("${scraper.anomaly-bounds.max-rooms:100}")
    private volatile int abMaxRooms;
    @Value("${scraper.anomaly-bounds.max-levels:20}")
    private volatile int abMaxLevels;
    @Value("${scraper.anomaly-bounds.max-ratio-buy-for-price-fix:50000}")
    private volatile double abMaxRatioBuyForPriceFix;
    @Value("${scraper.anomaly-bounds.ratio-threshold-rent-to-buy:500}")
    private volatile double abRatioThresholdRentToBuy;

    /**
     * Builds an {@link AnomalyBounds} from the current configuration.
     * Call {@link AnomalyBounds#copy()} on the result to override values for one city.
     */
    public AnomalyBounds getAnomalyBounds() {
        AnomalyBounds b = new AnomalyBounds();
        b.setMaxPriceBuy(abMaxPriceBuy);
        b.setMaxPriceRent(abMaxPriceRent);
        b.setMaxBedrooms(abMaxBedrooms);
        b.setMaxBathrooms(abMaxBathrooms);
        b.setMaxSurface(abMaxSurface);
        b.setMinSurface(abMinSurface);
        b.setMaxRatioBuy(abMaxRatioBuy);
        b.setMaxRatioRent(abMaxRatioRent);
        b.setMinRatioBuy(abMinRatioBuy);
        b.setMinRatioRent(abMinRatioRent);
        b.setMaxFloor(abMaxFloor);
        b.setMaxRooms(abMaxRooms);
        b.setMaxLevels(abMaxLevels);
        b.setMaxRatioBuyForPriceFix(abMaxRatioBuyForPriceFix);
        b.setRatioThresholdRentToBuy(abRatioThresholdRentToBuy);
        return b;
    }
}
