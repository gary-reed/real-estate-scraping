package com.realestate.dialect.warehouse;

import com.realestate.dialect.model.RealEstateListing;
import com.scraper.flavor.cms.dialect.DefaultListingWarehouse;
import com.scraper.flavor.cms.dialect.DefaultListingWarehouse.ExtraColumn;
import com.scraper.flavor.cms.dialect.Dialect;
import com.scraper.flavor.cms.model._ListingDetails;
import com.scraper.runtime.sink.spi.SinkRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Property columns after the shell. The shell insert (id, status, dates, the price printed on the
 * site, place, url, tags, hash, coordinates, precision, {@code details_json}) stays in
 * {@link DefaultListingWarehouse}.
 */
public final class RealEstateWarehouse extends DefaultListingWarehouse {

    public RealEstateWarehouse(Dialect dialect) {
        super(dialect);
    }

    @Override
    protected List<ExtraColumn> extraColumns() {
        return List.of(
                new ExtraColumn("ann_type", "TEXT"),
                new ExtraColumn("operation", "TEXT"),
                new ExtraColumn("currency", "TEXT"),
                new ExtraColumn("property_type", "TEXT"),
                new ExtraColumn("property_subtype", "TEXT"),
                new ExtraColumn("ext_phone", "TEXT"));
    }

    @Override
    protected int bindExtra(PreparedStatement stmt, int index, _ListingDetails listing) throws SQLException {
        if (!(listing instanceof RealEstateListing property)) {
            throw new IllegalArgumentException(
                    "real-estate warehouse expects RealEstateListing, got " + listing.getClass().getName());
        }
        stmt.setString(index++, property.getAnnType() != null ? property.getAnnType().getName() : null);
        var basic = property.getBasic();
        if (basic != null) {
            stmt.setString(index++, basic.getOperation() != null ? basic.getOperation().getName() : null);
            stmt.setString(index++, basic.getCurrency());
            stmt.setString(index++, basic.getPropertyType() != null ? basic.getPropertyType().getName() : null);
            stmt.setString(index++, basic.getPropertySubType() != null
                    ? basic.getPropertySubType().getName() : null);
        } else {
            for (int i = 0; i < 4; i++) {
                stmt.setNull(index++, Types.NULL);
            }
        }
        var contact = property.getContactExternal();
        stmt.setString(index++, contact != null ? contact.getExtPhone() : null);
        return index;
    }

    /**
     * The shell {@code price} column is the amount printed on the site. {@code basic.price} in the JSON is USD.
     * A non-USD site also keeps that printed amount in {@code basic.originalPrice}.
     * COALESCE is the amount Phase 2 compares with the card.
     */
    @Override
    public String sitePriceSql() {
        return "COALESCE(json_extract(w.details_json, '$.basic.originalPrice'), w.price)";
    }

    @Override
    public String columns(boolean withPayload) {
        return ListingSinkRecords.columns(withPayload);
    }

    @Override
    public SinkRecord toSinkRecord(ResultSet rs, String countryCode, boolean withPayload) throws SQLException {
        return ListingSinkRecords.fromRow(rs, countryCode, withPayload);
    }
}
