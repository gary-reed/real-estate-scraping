package com.realestate.dialect;

import com.scraper.flavor.cms.model._ListingDetails;
import com.scraper.flavor.cms.dialect.Dialect;
import com.realestate.dialect.model.RealEstateListing;
import com.realestate.dialect.warehouse.RealEstateWarehouse;
import com.scraper.flavor.cms.dialect.ListingWarehouse;

/**
 * The property dialect: a listing is a {@link RealEstateListing}, its site price lives on {@code basic}, and the
 * warehouse adds the operation and property type columns the consumer filters on.
 */
public final class RealEstateDialect implements Dialect {

    public static final String ID = "real-estate";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Double sitePrice(_ListingDetails listing) {
        if (!(listing instanceof RealEstateListing property) || property.getBasic() == null) {
            return null;
        }
        return property.getBasic().getPriceInSiteCurrency();
    }

    @Override
    public ListingWarehouse warehouse() {
        return new RealEstateWarehouse(this);
    }
}
