package com.realestate.dialect.sink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One row per listing. Attributes are the flat sink columns; rooms, contact, media and the USD price
 * come from the listing JSON because the warehouse does not store them as columns.
 * Every scraper writes the same columns. A list stays inside its cell, on that one row:
 * amenities are comma-separated, and each photo URL is separated inside its own column.
 */
final class ListingCsv {

    static final List<String> COLUMNS = List.of(
            "listing_id", "deleted", "status", "operation", "property_type", "property_subtype",
            "price", "currency", "price_usd", "city", "province", "postcode", "address",
            "latitude", "longitude", "bedrooms", "bathrooms", "surface_m2",
            "building_age", "land_size", "land_size_unit", "parking_count",
            "furnish_type", "heating_system", "property_status",
            "basic_appliances", "furnish_amenities", "building_facilities", "other_facilities",
            "property_amenities", "other_amenities",
            "comm_charges",
            "phone", "email", "agency", "ext_ref_id",
            "images_array", "thumb_images_array",
            "url", "created", "updated", "scraped_at", "description");

    private static final ObjectMapper JSON = new ObjectMapper();

    private ListingCsv() {}

    static String render(List<StoredListing> rows) {
        StringBuilder out = new StringBuilder();
        out.append('\uFEFF');
        out.append(String.join(",", COLUMNS)).append('\n');
        for (StoredListing row : rows) {
            List<String> cells = new ArrayList<>(COLUMNS.size());
            Map<String, Object> a = row.attributes() == null ? Map.of() : row.attributes();
            JsonNode payload = payload(row.payload());
            String[] latLon = splitCoordinates(text(a.get("coordinates")));
            for (String column : COLUMNS) {
                cells.add(csv(value(column, row, a, payload, latLon)));
            }
            out.append(String.join(",", cells)).append('\n');
        }
        return out.toString();
    }

    private static String value(String column, StoredListing row, Map<String, Object> a, JsonNode payload, String[] latLon) {
        return switch (column) {
            case "listing_id" -> row.id();
            case "deleted" -> Boolean.toString(row.deleted());
            case "status" -> first(text(a.get("status")), path(payload, "status"));
            case "operation" -> text(a.get("operation"));
            case "property_type" -> text(a.get("propertyType"));
            case "property_subtype" -> text(a.get("propertySubtype"));
            case "price" -> first(text(a.get("price")), path(payload, "basic", "originalPrice"), path(payload, "basic", "price"));
            case "currency" -> first(text(a.get("currency")), path(payload, "basic", "currency"));
            case "price_usd" -> path(payload, "basic", "price");
            case "city" -> first(text(a.get("city")), path(payload, "location", "city"));
            case "province" -> first(text(a.get("province")), path(payload, "location", "province"));
            case "postcode" -> path(payload, "location", "postcode");
            case "address" -> first(text(a.get("address")), path(payload, "location", "address"));
            case "latitude" -> latLon[0];
            case "longitude" -> latLon[1];
            case "bedrooms" -> path(payload, "property", "bedrooms");
            case "bathrooms" -> path(payload, "property", "bathrooms");
            case "surface_m2" -> path(payload, "property", "surface");
            case "building_age" -> path(payload, "building", "buildingAge");
            case "land_size" -> path(payload, "building", "landSz");
            case "land_size_unit" -> path(payload, "building", "landSzM");
            case "parking_count" -> path(payload, "building", "parkingCount");
            case "furnish_type" -> path(payload, "furnish", "furnishType");
            case "heating_system" -> path(payload, "furnish", "heatingSystem");
            case "property_status" -> path(payload, "property", "propertyStatus");
            case "basic_appliances" -> inlineList(payload, "furnish", "basicAppliances");
            case "furnish_amenities" -> inlineList(payload, "furnish", "furnishAmenities");
            case "building_facilities" -> inlineList(payload, "building", "buildingFacilities");
            case "other_facilities" -> inlineList(payload, "building", "othrFacils");
            case "property_amenities" -> inlineList(payload, "property", "amenities");
            case "other_amenities" -> inlineList(payload, "property", "othrAmens");
            case "comm_charges" -> path(payload, "transaction", "commCharges");
            case "phone" -> phone(path(payload, "contactExternal", "extPhone"));
            case "email" -> path(payload, "contactExternal", "extEmail");
            case "agency" -> first(text(a.get("agencyName")), path(payload, "contactExternal", "agencyName"));
            case "ext_ref_id" -> path(payload, "contactExternal", "extRefId");
            case "images_array" -> mediaLines(payload, "imagesArray");
            case "thumb_images_array" -> mediaLines(payload, "thumbImagesArray");
            case "url" -> text(a.get("scrapingExtUrl"));
            case "created" -> first(path(payload, "created"), text(a.get("createdAt")));
            case "updated" -> first(path(payload, "updated"), text(a.get("updatedAt")));
            case "scraped_at" -> text(a.get("scrapingScrapedDate"));
            case "description" -> path(payload, "description");
            default -> "";
        };
    }

    /**
     * A leading {@code +} is a number to Excel, which drops it. A tab in front keeps the cell as text
     * so the plus stays visible, and the value is still the same phone.
     */
    private static String phone(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("+")) {
            return "\t" + trimmed;
        }
        return trimmed;
    }

    /** One cell, one visual line: {@code oven, dishwasher}. */
    private static String inlineList(JsonNode payload, String... keys) {
        return join(at(payload, keys), ", ");
    }

    /**
     * One cell, one listing row. URLs are separated so each photo is visible, without a line break
     * that a spreadsheet would read as the next listing.
     */
    private static String mediaLines(JsonNode payload, String arrayName) {
        return join(at(payload, "media", arrayName), " | ");
    }

    private static String join(JsonNode node, String separator) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (!node.isArray()) {
            return itemText(node);
        }
        StringBuilder joined = new StringBuilder();
        for (JsonNode item : node) {
            String text = itemText(item);
            if (text.isEmpty()) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append(separator);
            }
            joined.append(text);
        }
        return joined.toString();
    }

    private static String itemText(JsonNode item) {
        if (item == null || item.isNull() || item.isMissingNode()) {
            return "";
        }
        if (item.isObject()) {
            JsonNode url = item.get("url");
            if (url != null && url.isTextual() && !url.asText().isBlank()) {
                return url.asText().trim();
            }
            return "";
        }
        if (item.isNumber()) {
            return number(item.numberValue());
        }
        if (item.isTextual()) {
            return item.asText().trim();
        }
        if (item.isBoolean()) {
            return Boolean.toString(item.booleanValue());
        }
        return "";
    }

    private static JsonNode at(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        JsonNode current = node;
        for (String key : keys) {
            current = current.get(key);
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
        }
        return current;
    }

    private static JsonNode payload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static String path(JsonNode node, String... keys) {
        if (node == null) {
            return "";
        }
        JsonNode current = node;
        for (String key : keys) {
            current = current.get(key);
            if (current == null || current.isNull() || current.isMissingNode()) {
                return "";
            }
        }
        if (current.isNumber()) {
            return number(current.numberValue());
        }
        if (current.isTextual()) {
            return current.asText();
        }
        if (current.isBoolean()) {
            return Boolean.toString(current.booleanValue());
        }
        return "";
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number number) {
            return number(number);
        }
        if (value instanceof List<?> list) {
            StringBuilder joined = new StringBuilder();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                if (!joined.isEmpty()) {
                    joined.append("; ");
                }
                joined.append(item);
            }
            return joined.toString();
        }
        return String.valueOf(value);
    }

    private static String number(Number number) {
        double value = number.doubleValue();
        if (!Double.isInfinite(value) && value == Math.rint(value) && Math.abs(value) < Long.MAX_VALUE) {
            return Long.toString(number.longValue());
        }
        return Double.toString(value);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    /** {@code "lat,lon"} as stored on the sink record. A missing or odd value leaves both cells empty. */
    private static String[] splitCoordinates(String coordinates) {
        if (coordinates == null || coordinates.isBlank()) {
            return new String[] {"", ""};
        }
        int comma = coordinates.indexOf(',');
        if (comma < 0) {
            return new String[] {"", ""};
        }
        return new String[] {coordinates.substring(0, comma).trim(), coordinates.substring(comma + 1).trim()};
    }

    static String csv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean quote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('\t') >= 0 || value.charAt(0) == '+' || value.charAt(0) == '=';
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }
}
