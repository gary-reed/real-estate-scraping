package com.realestate.dialect.sink;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Listings for one source and country. The spreadsheet is rewritten after every non-empty batch.
 * Its folder is {@code <directory>/<country>/<site>/<file-name>}, fixed from the scraper settings for
 * the life of this process. The state file is what the next DIFF compares, unless that spreadsheet
 * is missing: then the consumer is empty and the diff pushes every listing again.
 */
final class CsvListingStore {

    private static final Logger log = LoggerFactory.getLogger(CsvListingStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final byte UNIT = 0x1F;
    private static final byte RECORD = 0x1E;

    private final Path csvFile;
    private final Path stateFile;
    private final Map<String, StoredListing> records = new TreeMap<>(CsvListingStore::utf8Order);
    private boolean loaded;

    CsvListingStore(Path csvRoot, Path stateRoot, String source, String country, Clock clock) {
        this(csvRoot, stateRoot, source, country, clock, CsvSinkProperties.DEFAULT_FILE_NAME);
    }

    /**
     * {@code clock} is unused for the path. The folder does not follow the calendar day: a diff the
     * next day still reads the spreadsheet chosen from {@code csvRoot} when this store was created.
     */
    CsvListingStore(Path csvRoot, Path stateRoot, String source, String country, Clock clock, String csvFileName) {
        String countryFolder = segment(country);
        String site = siteFolder(source, countryFolder);
        this.csvFile = csvRoot.resolve(countryFolder).resolve(site).resolve(CsvSinkProperties.fileNameOrDefault(csvFileName));
        this.stateFile = stateRoot.resolve(countryFolder).resolve(segment(source) + ".json");
        if (clock == null) {
            throw new IllegalArgumentException("clock is required");
        }
    }

    /** {@code compass-us} + {@code us} → {@code compass}. */
    static String siteFolder(String source, String country) {
        String name = segment(source);
        String cc = segment(country);
        String suffix = "-" + cc;
        if (!"_".equals(cc) && name.endsWith(suffix) && name.length() > suffix.length()) {
            return name.substring(0, name.length() - suffix.length());
        }
        return name;
    }

    static String segment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "_";
        }
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (cleaned.isEmpty() || cleaned.contains("..")) {
            return "_";
        }
        return cleaned;
    }

    synchronized int write(List<Incoming> batch) throws IOException {
        load();
        dropSavedRowsWhenTheSpreadsheetIsMissing();
        if (batch.isEmpty()) {
            return 0;
        }
        for (Incoming incoming : batch) {
            if (incoming.id() == null || incoming.id().isBlank()) {
                throw new IOException("a record has no id");
            }
            StoredListing previous = records.get(incoming.id());
            if (incoming.deletionMarker() && previous != null) {
                records.put(incoming.id(), new StoredListing(incoming.id(), previous.hash(), true,
                        previous.attributes(), previous.payload()));
            } else {
                String hash = incoming.hash() != null ? incoming.hash() : previous == null ? null : previous.hash();
                Map<String, Object> attributes = incoming.attributes() != null
                        ? incoming.attributes()
                        : previous == null ? null : previous.attributes();
                String payload = incoming.payload() != null
                        ? incoming.payload()
                        : previous == null ? null : previous.payload();
                records.put(incoming.id(), new StoredListing(incoming.id(), hash, incoming.deleted(), attributes, payload));
            }
        }
        persist();
        return batch.size();
    }

    synchronized List<StoredListing> records() throws IOException {
        load();
        dropSavedRowsWhenTheSpreadsheetIsMissing();
        return List.copyOf(records.values());
    }

    synchronized String payload(String id) throws IOException {
        load();
        dropSavedRowsWhenTheSpreadsheetIsMissing();
        StoredListing row = records.get(id);
        return row == null ? null : row.payload();
    }

    /**
     * Call 4 of the sink API: live rows only, ordered by the UTF-8 bytes of the id.
     * The same definition the framework uses, so a matching value skips the hash download.
     */
    synchronized Fingerprint fingerprint() throws IOException {
        load();
        dropSavedRowsWhenTheSpreadsheetIsMissing();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            for (StoredListing row : records.values()) {
                if (row.deleted()) {
                    continue;
                }
                digest.update(row.id().getBytes(StandardCharsets.UTF_8));
                digest.update(UNIT);
                if (row.hash() != null) {
                    digest.update(row.hash().getBytes(StandardCharsets.UTF_8));
                }
                digest.update(RECORD);
                count++;
            }
            return new Fingerprint(count, "fp2:sha256:" + HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** The spreadsheet for this process: {@code <directory>/<country>/<site>/<file-name>}. */
    Path csvFile() {
        return csvFile;
    }

    /**
     * The hash memory is only valid while the spreadsheet it describes is still there.
     * A deleted file, or a folder that no longer contains it, is an empty consumer.
     */
    private void dropSavedRowsWhenTheSpreadsheetIsMissing() {
        if (Files.isRegularFile(csvFile) || records.isEmpty()) {
            return;
        }
        log.warn("Spreadsheet missing at {} — diff will push every listing again", csvFile.toAbsolutePath().normalize());
        records.clear();
    }

    private void load() throws IOException {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!Files.isRegularFile(csvFile)) {
            if (Files.isRegularFile(stateFile)) {
                log.warn("Spreadsheet missing at {} — ignoring saved hashes in {}",
                        csvFile.toAbsolutePath().normalize(), stateFile.toAbsolutePath().normalize());
            }
            return;
        }
        if (!Files.isRegularFile(stateFile)) {
            return;
        }
        JsonNode root = JSON.readTree(stateFile.toFile());
        if (!root.isArray()) {
            return;
        }
        for (JsonNode node : root) {
            String id = text(node, "id");
            if (id == null) {
                continue;
            }
            Map<String, Object> attributes = node.get("attributes") == null || node.get("attributes").isNull()
                    ? null
                    : JSON.convertValue(node.get("attributes"), MAP);
            String payload = node.get("payload") == null || node.get("payload").isNull()
                    ? null
                    : node.get("payload").isTextual() ? node.get("payload").asText() : JSON.writeValueAsString(node.get("payload"));
            records.put(id, new StoredListing(id, text(node, "hash"), node.path("deleted").asBoolean(false), attributes, payload));
        }
    }

    private void persist() throws IOException {
        Files.createDirectories(stateFile.getParent());
        Path csv = csvFile();
        Files.createDirectories(csv.getParent());
        ArrayNode array = JSON.createArrayNode();
        for (StoredListing row : records.values()) {
            ObjectNode node = array.addObject();
            node.put("id", row.id());
            if (row.hash() == null) {
                node.putNull("hash");
            } else {
                node.put("hash", row.hash());
            }
            node.put("deleted", row.deleted());
            if (row.attributes() == null) {
                node.putNull("attributes");
            } else {
                node.set("attributes", JSON.valueToTree(row.attributes()));
            }
            if (row.payload() == null) {
                node.putNull("payload");
            } else {
                node.put("payload", row.payload());
            }
        }
        writeAtomically(stateFile, JSON.writeValueAsBytes(array));
        writeAtomically(csv, ListingCsv.render(new ArrayList<>(records.values())).getBytes(StandardCharsets.UTF_8));
        log.info("Wrote {} listings to {}", records.size(), csv.toAbsolutePath().normalize());
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        Files.write(tmp, bytes);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** UTF-8 byte order, unsigned, which is what the fingerprint requires. Not {@code String.compareTo}. */
    static int utf8Order(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    record Incoming(String id, String hash, boolean deleted, boolean deletionMarker,
                    Map<String, Object> attributes, String payload) {}

    record Fingerprint(long count, String value) {}
}
