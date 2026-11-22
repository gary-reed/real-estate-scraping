package com.realestate.dialect.warehouse;

import com.scraper.runtime.sink.spi.SinkRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A warehouse row becomes a record of the {@code listings} dataset (docs/reference/sink-consumer-api.md). */
class ListingSinkRecordsTest {

    private static final String DETAILS = "{\"contactExternal\":{\"agencyName\":\" Acme Realty \"},"
            + "\"location\":{\"address\":\" 1 Main Street, Capital \"}}";

    private Connection conn;

    @BeforeEach
    void openWarehouse() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement s = conn.createStatement()) {
            s.execute("""
                    CREATE TABLE temp_warehouse (
                        listing_id TEXT, site_alias TEXT, status TEXT, ann_type TEXT, created_at TEXT, updated_at TEXT,
                        operation TEXT, price REAL, currency TEXT, property_type TEXT, property_subtype TEXT,
                        city TEXT, province TEXT, scraping_ext_url TEXT, scraping_scraped_date TEXT,
                        scraping_action_status TEXT, tags TEXT, split_group TEXT, content_hash TEXT,
                        deleted_date TEXT, coordinates TEXT, "precision" TEXT, ext_phone TEXT, is_precise INTEGER,
                        details_json TEXT)""");
        }
    }

    @AfterEach
    void closeWarehouse() throws SQLException {
        conn.close();
    }

    private void insert(String id, String status, String currency, String tags, String coordinates, String details)
            throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("INSERT INTO temp_warehouse (listing_id, site_alias, status, price, currency, city, tags, "
                    + "coordinates, content_hash, details_json) VALUES ('" + id + "', 'example-es', "
                    + sql(status) + ", 250000, " + sql(currency) + ", 'Capital', " + sql(tags) + ", "
                    + sql(coordinates) + ", 'h-" + id + "', " + sql(details) + ")");
        }
    }

    private static String sql(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private SinkRecord read(String id, boolean withPayload, String country) throws SQLException {
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + ListingSinkRecords.columns(withPayload)
                     + " FROM temp_warehouse WHERE listing_id = '" + id + "'")) {
            assertTrue(rs.next());
            return ListingSinkRecords.fromRow(rs, country, withPayload);
        }
    }

    @Test
    void mapsTheRowToTheListingsDataset() throws SQLException {
        insert("abc", "active", "EURO", "pool, garden", "1.0,2.0", DETAILS);

        SinkRecord r = read("abc", true, "ES");

        assertEquals("abc", r.id());
        assertEquals("h-abc", r.hash());
        assertFalse(r.deleted());
        assertEquals(DETAILS, r.payload());
        assertEquals("example-es", r.attributes().get("siteAlias"));
        assertEquals("es", r.attributes().get("countryCode"));
        assertEquals("Acme Realty", r.attributes().get("agencyName"));
        assertEquals("1 Main Street, Capital", r.attributes().get("address"));
        assertEquals("EUR", r.attributes().get("currency"));
        assertEquals(250000.0, r.attributes().get("price"));
        assertEquals("Capital", r.attributes().get("city"));
        assertEquals(List.of("pool", "garden"), r.attributes().get("tags"));
        assertEquals("1.0,2.0", r.attributes().get("coordinates"));
    }

    @Test
    void aBlankStatusIsActiveAndADeletedStatusMarksTheRecordDeleted() throws SQLException {
        insert("a", null, "EUR", null, null, "{}");
        insert("d", "deleted", "EUR", null, null, "{}");

        assertEquals("active", read("a", true, "es").attributes().get("status"));
        assertFalse(read("a", true, "es").deleted());
        assertEquals("deleted", read("d", true, "es").attributes().get("status"));
        assertTrue(read("d", true, "es").deleted());
    }

    @Test
    void withoutThePayloadTheRecordCarriesOnlyTheColumns() throws SQLException {
        insert("abc", "active", "EUR", null, null, DETAILS);

        SinkRecord r = read("abc", false, "es");

        assertNull(r.payload());
        assertNull(r.attributes().get("agencyName"));
        assertNull(r.attributes().get("address"));
        assertEquals("Capital", r.attributes().get("city"));
        assertEquals("h-abc", r.hash());
    }

    @Test
    void anUnparseablePayloadLeavesTheAgencyEmptyAndTheAddressNull() throws SQLException {
        insert("abc", "active", "EUR", null, null, "not json");

        SinkRecord r = read("abc", true, "es");

        assertEquals("", r.attributes().get("agencyName"));
        assertNull(r.attributes().get("address"));
    }

    @Test
    void tagsAndCountryAreNormalized() {
        assertEquals(List.of("a", "b"), ListingSinkRecords.splitTags("a, b"));
        assertNull(ListingSinkRecords.splitTags(""));
        assertNull(ListingSinkRecords.splitTags(null));
        assertEquals("es", ListingSinkRecords.normalizeCountry(" ESP "));
        assertNull(ListingSinkRecords.normalizeCountry(null));
        assertNull(ListingSinkRecords.normalizeCountry(" "));
    }
}
