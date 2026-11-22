package com.realestate.dialect.sink;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A run writes outputs/country/site/file-name. The default file name is listings.csv. */
class CsvListingSinkTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.ofHours(1));

    private static final String PAYLOAD = """
            {"description":"A house, \\"nice\\"","created":"2026-01-02T03:04:05Z","updated":"2026-11-01T00:00:00Z",
            "basic":{"price":230000.5,"originalPrice":250000,"currency":"USD"},
            "property":{"bedrooms":3,"bathrooms":2,"surface":148,"propertyStatus":"good_condition",
            "amenities":["balcony"],"othrAmens":["wine cellar"]},
            "location":{"address":"1412 Riverwind Dr","postcode":"73130","city":"Midwest City"},
            "building":{"buildingAge":1998,"landSz":520,"landSzM":"m2","parkingCount":2,
            "buildingFacilities":["parking","gym"],"othrFacils":["dog park"]},
            "furnish":{"furnishType":"furnished","heatingSystem":"gas",
            "basicAppliances":["oven","dishwasher"],"furnishAmenities":["air_conditioner","internet"]},
            "transaction":{"commCharges":125.5},
            "media":{"imagesArray":[{"url":"https://img/a.jpg"},{"url":"https://img/b.jpg"}],
            "thumbImagesArray":[{"url":"https://img/a-thumb.jpg"},{"url":"https://img/b-thumb.jpg"}]},
            "contactExternal":{"extPhone":"+14055505635","extEmail":"a@b.com","agencyName":"Acme","extRefId":"MLS-9"}}""";

    @TempDir
    Path temp;

    @Test
    void snapshotLandsInTheSiteFolderAndReloads() throws Exception {
        Path csvRoot = temp.resolve("outputs");
        Path stateRoot = temp.resolve("state");
        CsvListingStore store = new CsvListingStore(csvRoot, stateRoot, "compass-us", "us", CLOCK);
        store.write(List.of(incoming("compass-us2237", "h1", false, false)));

        Path csv = store.csvFile();
        assertTrue(csv.toString().replace('\\', '/').endsWith("outputs/us/compass/listings.csv"), csv.toString());
        String text = Files.readString(csv, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("\uFEFFlisting_id,deleted,status,"));
        assertTrue(text.contains("building_age,land_size,land_size_unit,parking_count,furnish_type,heating_system,property_status,"));
        assertTrue(text.contains("basic_appliances,furnish_amenities,building_facilities,other_facilities,property_amenities,other_amenities,comm_charges,"));
        assertTrue(text.contains("ext_ref_id,images_array,thumb_images_array,url,created,updated,scraped_at,description"));
        assertTrue(text.contains("compass-us2237,false,active,buy,residential,house,250000,USD,230000.5,Midwest City,OK,73130,1412 Riverwind Dr,35.45,-97.39,3,2,148,1998,520,m2,2,furnished,gas,good_condition,"));
        assertTrue(text.contains("\"oven, dishwasher\""));
        assertTrue(text.contains("\"air_conditioner, internet\""));
        assertTrue(text.contains("\"parking, gym\""));
        assertTrue(text.contains("dog park,balcony,wine cellar,125.5,"));
        assertTrue(text.contains("\"\t+14055505635\",a@b.com,Acme,MLS-9,"));
        assertTrue(text.contains("https://img/a.jpg | https://img/b.jpg"));
        assertTrue(text.contains("https://img/a-thumb.jpg | https://img/b-thumb.jpg"));
        assertTrue(text.contains("2026-01-02T03:04:05Z,2026-11-01T00:00:00Z,2026-11-16T12:00:00Z,"));
        assertTrue(text.contains("\"A house, \"\"nice\"\"\""));
        assertEquals(2, text.strip().lines().count());

        store.write(List.of(new CsvListingStore.Incoming("compass-us2237", null, true, true, null, null)));
        assertTrue(Files.readString(csv, StandardCharsets.UTF_8).contains("compass-us2237,true,"));

        CsvListingStore again = new CsvListingStore(csvRoot, stateRoot, "compass-us", "us", CLOCK);
        assertEquals(1, again.records().size());
        assertTrue(again.records().get(0).deleted());
        assertEquals("h1", again.records().get(0).hash());
    }

    @Test
    void fingerprintMatchesTheSinkContract() throws Exception {
        CsvListingStore store = new CsvListingStore(temp.resolve("outputs"), temp.resolve("state"), "compass-us", "us", CLOCK);
        store.write(List.of(
                row("a", "h1", false),
                row("～", "h2", false),
                row("😀", "h3", false),
                row("b", null, false),
                row("c", "h4", true)));
        CsvListingStore.Fingerprint fingerprint = store.fingerprint();
        assertEquals(4, fingerprint.count());
        assertEquals("fp2:sha256:dbbc559dcb7155ad90a04a8936aa2c958463a14972c54ced9166dcfa6c96c29c", fingerprint.value());

        CsvListingStore empty = new CsvListingStore(temp.resolve("empty-out"), temp.resolve("empty-state"), "compass-us", "us", CLOCK);
        assertEquals("fp2:sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                empty.fingerprint().value());
        assertEquals(0, empty.fingerprint().count());
    }

    @Test
    void httpConsumerWritesTheSameFile() throws Exception {
        CsvSinkProperties properties = new CsvSinkProperties();
        properties.setPort(0);
        properties.setToken("local-csv");
        properties.setDirectory(temp.resolve("outputs").toString());
        properties.setFileName("okc.csv");
        properties.setStateDirectory(temp.resolve("state").toString());
        try (CsvListingSinkServer server = new CsvListingSinkServer(properties, CLOCK)) {
            server.start();
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.port() + "/v2/sinks/listings/compass-us";

            HttpResponse<String> denied = http.send(request(base + "/records?country=us", "nope", "{\"records\":[]}"),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, denied.statusCode());

            String body = JSON.writeValueAsString(Map.of("records", List.of(Map.of(
                    "id", "compass-us2237", "hash", "h1", "deleted", false,
                    "attributes", attributes(), "payload", JSON.readTree(PAYLOAD)))));
            HttpResponse<String> written = http.send(request(base + "/records?country=us", "local-csv", body),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, written.statusCode(), written.body());
            JsonNode response = JSON.readTree(written.body());
            assertEquals(1, response.get("written").asInt());
            assertTrue(response.get("target").asText().replace('\\', '/').endsWith("us/compass/okc.csv"));

            HttpResponse<String> hashes = http.send(HttpRequest.newBuilder(URI.create(base + "/hashes?country=us"))
                    .header("X-Internal-Token", "local-csv").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, hashes.statusCode());
            assertEquals("1", hashes.headers().firstValue("X-Sink-Count").orElse(""));
            assertTrue(hashes.body().contains("\"id\":\"compass-us2237\""));

            HttpResponse<String> fingerprint = http.send(HttpRequest.newBuilder(URI.create(base + "/fingerprint?country=us"))
                    .header("X-Internal-Token", "local-csv").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, fingerprint.statusCode());
            assertEquals(1, JSON.readTree(fingerprint.body()).get("count").asInt());
        }
    }

    @Test
    void fileNameDefaultsToListingsCsvAndRejectsAPath() {
        assertEquals("listings.csv", new CsvSinkProperties().getFileName());
        assertEquals("listings.csv", CsvSinkProperties.fileNameOrDefault("  "));
        assertEquals("okc.csv", CsvSinkProperties.fileNameOrDefault(" okc.csv "));

        CsvListingStore store = new CsvListingStore(temp.resolve("outputs"), temp.resolve("state"),
                "compass-us", "us", CLOCK, "okc.csv");
        assertTrue(store.csvFile().toString().replace('\\', '/').endsWith("outputs/us/compass/okc.csv"));
        assertThrows(IllegalArgumentException.class,
                () -> new CsvListingStore(temp.resolve("outputs"), temp.resolve("state"), "compass-us", "us", CLOCK, "us/okc.csv"));
        assertThrows(IllegalArgumentException.class, () -> CsvSinkProperties.fileNameOrDefault("../listings.csv"));
    }

    @Test
    void aMissingSpreadsheetMakesTheNextDiffPushEverything() throws Exception {
        Path csvRoot = temp.resolve("outputs");
        Path stateRoot = temp.resolve("state");
        CsvListingStore store = new CsvListingStore(csvRoot, stateRoot, "compass-us", "us", CLOCK);
        store.write(List.of(incoming("compass-us2237", "h1", false, false)));
        Files.delete(store.csvFile());

        assertEquals(0, store.fingerprint().count());
        assertTrue(store.records().isEmpty());

        Clock nextDay = Clock.fixed(Instant.parse("2026-11-17T12:00:00Z"), ZoneOffset.ofHours(1));
        CsvListingStore restarted = new CsvListingStore(csvRoot, stateRoot, "compass-us", "us", nextDay);
        assertEquals(store.csvFile(), restarted.csvFile());
        assertEquals(0, restarted.fingerprint().count());
        assertEquals("fp2:sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                restarted.fingerprint().value());
    }

    private static CsvListingStore.Incoming incoming(String id, String hash, boolean deleted, boolean marker) {
        return new CsvListingStore.Incoming(id, hash, deleted, marker, attributes(), PAYLOAD);
    }

    private static CsvListingStore.Incoming row(String id, String hash, boolean deleted) {
        return new CsvListingStore.Incoming(id, hash, deleted, false, Map.of("status", "active"), "{}");
    }

    private static Map<String, Object> attributes() {
        return Map.ofEntries(
                Map.entry("status", "active"),
                Map.entry("operation", "buy"),
                Map.entry("propertyType", "residential"),
                Map.entry("propertySubtype", "house"),
                Map.entry("price", 250000),
                Map.entry("currency", "USD"),
                Map.entry("city", "Midwest City"),
                Map.entry("province", "OK"),
                Map.entry("address", "1412 Riverwind Dr"),
                Map.entry("coordinates", "35.45,-97.39"),
                Map.entry("agencyName", "Acme"),
                Map.entry("scrapingExtUrl", "https://www.compass.com/homedetails/x"),
                Map.entry("scrapingScrapedDate", "2026-11-16T12:00:00Z"));
    }

    private static HttpRequest request(String url, String token, String body) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("X-Internal-Token", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
