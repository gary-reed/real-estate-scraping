package com.realestate.dialect.warehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scraper.flavor.cms.db.ListingJsonMapper;
import com.scraper.runtime.sink.spi.SinkRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps a {@code temp_warehouse} row to a record of the {@code listings} dataset (see
 * {@code docs/reference/sink-consumer-api.md}, "Dataset listings"): the columns become flat attributes, the
 * {@code details_json} text becomes the payload, and the {@code content_hash} the hash.
 *
 * <p>Kept free of any consumer type: the consumer decides how to store the attributes. The three values the
 * warehouse does not hold as columns ({@code countryCode}, {@code agencyName}, {@code address}) come from the
 * scraper's country and from the payload.
 */
public final class ListingSinkRecords {

    private static final ObjectMapper JSON = ListingJsonMapper.create();

    /**
     * Columns selected by name. This is not the INSERT order: the insert is the shell, then the six
     * property columns, then {@code details_json}.
     */
    static final String COLUMN_LIST = """
            listing_id, site_alias, status, ann_type, created_at, updated_at,
            operation, price, currency, property_type, property_subtype, city, province,
            scraping_ext_url, scraping_scraped_date,
            scraping_action_status, tags, split_group, content_hash, deleted_date,
            coordinates, "precision", ext_phone, is_precise, details_json""";

    /** The warehouse columns to select; without the payload when only the Diff grid needs them. */
    public static String columns(boolean withPayload) {
        return withPayload
                ? COLUMN_LIST
                : COLUMN_LIST.replace(", details_json", "");
    }

    private ListingSinkRecords() {}

    /** Maps the current row of a result set selected with {@link #columns(boolean)}. */
    public static SinkRecord fromRow(ResultSet rs, String countryCode, boolean withPayload) throws SQLException {
        String status = rs.getString("status");
        String payload = withPayload ? rs.getString("details_json") : null;

        Map<String, Object> a = new LinkedHashMap<>();
        a.put("siteAlias", rs.getString("site_alias"));
        a.put("countryCode", normalizeCountry(countryCode));
        a.put("agencyName", withPayload ? agencyName(payload) : null);
        a.put("status", blank(status) ? "active" : status);
        a.put("annType", rs.getString("ann_type"));
        a.put("operation", rs.getString("operation"));
        a.put("propertyType", rs.getString("property_type"));
        a.put("propertySubtype", rs.getString("property_subtype"));
        a.put("city", rs.getString("city"));
        a.put("province", rs.getString("province"));
        a.put("scrapingExtUrl", rs.getString("scraping_ext_url"));
        a.put("scrapingActionStatus", rs.getString("scraping_action_status"));
        a.put("splitGroup", rs.getString("split_group"));
        a.put("precision", rs.getString("precision"));
        a.put("createdAt", rs.getString("created_at"));
        a.put("updatedAt", rs.getString("updated_at"));
        a.put("scrapingScrapedDate", rs.getString("scraping_scraped_date"));
        a.put("deletedDate", rs.getString("deleted_date"));
        a.put("price", rs.getObject("price"));
        a.put("currency", left(rs.getString("currency"), 3));
        a.put("address", withPayload ? address(payload) : null);
        a.put("tags", splitTags(rs.getString("tags")));
        a.put("coordinates", rs.getString("coordinates"));

        return new SinkRecord(rs.getString("listing_id"), rs.getString("content_hash"),
                "deleted".equalsIgnoreCase(status), a, payload);
    }

    static List<String> splitTags(String csv) {
        return blank(csv) ? null : List.of(csv.split(",\\s*"));
    }

    static String agencyName(String detailsJson) {
        String name = jsonText(detailsJson, "contactExternal", "agencyName");
        return name == null ? "" : name;
    }

    static String address(String detailsJson) {
        return jsonText(detailsJson, "location", "address");
    }

    static String normalizeCountry(String countryCode) {
        if (blank(countryCode)) {
            return null;
        }
        String trimmed = countryCode.trim().toLowerCase();
        return trimmed.length() <= 2 ? trimmed : trimmed.substring(0, 2);
    }

    private static String jsonText(String detailsJson, String... path) {
        if (blank(detailsJson)) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(detailsJson);
            for (String key : path) {
                node = node.path(key);
            }
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            String text = node.asText("");
            return blank(text) ? null : text.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String left(String value, int max) {
        if (blank(value)) {
            return value;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
