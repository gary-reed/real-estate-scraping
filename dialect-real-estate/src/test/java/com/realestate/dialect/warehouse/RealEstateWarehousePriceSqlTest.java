package com.realestate.dialect.warehouse;

import com.realestate.dialect.RealEstateDialect;
import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Property;
import com.realestate.dialect.model.RealEstateListing;
import com.scraper.flavor.cms.domain.ListingIdUrl;
import com.scraper.flavor.cms.db.SQLiteHandler;
import com.scraper.flavor.cms.model.Location;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The card price is the amount on the site, not the USD conversion stored in the shell price column. */
class RealEstateWarehousePriceSqlTest {

    @Test
    void cardIsComparedWithTheAmountOnTheSiteNotTheEuroConversion() throws Exception {
        Path file = Files.createTempFile("real-estate-warehouse", ".db");
        SQLiteHandler handler = new SQLiteHandler();
        try {
            Field path = SQLiteHandler.class.getDeclaredField("localScrapingTempDatabaseName");
            path.setAccessible(true);
            path.set(handler, file.toString());
            handler.setListingWarehouse(new RealEstateWarehouse(new RealEstateDialect()));
            handler.initialize();

            insert(file, "gbp", 290000.0, 250000.0);
            insert(file, "gbp-changed", 290000.0, 250000.0);
            handler.saveTempListingIds("test", List.of(
                    new ListingIdUrl("gbp", "https://example.com/gbp", "test", "250000", null),
                    new ListingIdUrl("gbp-changed", "https://example.com/gbp-changed", "test", "260000", null)));

            handler.calculateDelta("test");

            assertEquals(List.of("gbp-changed"),
                    handler.getTempListingIdsForUpdate("test").stream().map(ListingIdUrl::getListingId).toList());
        } finally {
            handler.cleanup();
            for (String suffix : List.of("", "-wal", "-shm")) {
                Files.deleteIfExists(Path.of(file + suffix));
            }
        }
    }

    @Test
    void aSavedListingFillsThePropertyColumnsAndKeepsThePayloadBlocks() throws Exception {
        Path file = Files.createTempFile("real-estate-warehouse", ".db");
        SQLiteHandler handler = new SQLiteHandler();
        try {
            Field path = SQLiteHandler.class.getDeclaredField("localScrapingTempDatabaseName");
            path.setAccessible(true);
            path.set(handler, file.toString());
            handler.setListingWarehouse(new RealEstateWarehouse(new RealEstateDialect()));
            handler.initialize();

            RealEstateListing listing = new RealEstateListing("p1");
            listing.setAnnType(RealEstateListing.AnnounceTypeEnum.EXTERNAL);
            Basic basic = new Basic();
            basic.setOperation(Basic.OperationEnum.RENT);
            basic.setPropertyType(Basic.TypeEnum.RESIDENTIAL);
            basic.setPropertySubType(Basic.SubTypeEnum.RES_APARTMENT);
            basic.setCurrency("EUR");
            basic.setPrice(25.0);
            listing.setBasic(basic);
            listing.setProperty(new Property());
            Location location = new Location();
            location.setCity("Oakridge");
            listing.setLocation(location);

            handler.saveTempWarehouseListings("test", List.of(listing));

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                 Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT price, operation, property_type, property_subtype, details_json FROM temp_warehouse")) {
                assertTrue(rs.next());
                assertEquals(25.0, rs.getDouble("price"));
                assertEquals("rent", rs.getString("operation"));
                assertEquals("residential", rs.getString("property_type"));
                assertEquals("apartment", rs.getString("property_subtype"));
                String json = rs.getString("details_json");
                assertTrue(json.contains("\"basic\""));
                assertTrue(json.contains("\"property\""));
                assertTrue(json.contains("\"location\""));
                assertTrue(json.contains("Oakridge"));
            }
        } finally {
            handler.cleanup();
            for (String suffix : List.of("", "-wal", "-shm")) {
                Files.deleteIfExists(Path.of(file + suffix));
            }
        }
    }

    private static void insert(Path file, String id, double price, double originalPrice) throws Exception {
        String json = "{\"basic\":{\"price\":" + price + ",\"originalPrice\":" + originalPrice + "}}";
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO temp_warehouse (listing_id, site_alias, price, details_json) VALUES ('"
                    + id + "', 'test', " + price + ", '" + json + "')");
        }
    }
}
