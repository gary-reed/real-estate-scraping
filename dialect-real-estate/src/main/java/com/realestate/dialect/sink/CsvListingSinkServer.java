package com.realestate.dialect.sink;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sink Consumer API v2 on localhost. The framework already pushes listings to whatever
 * {@code scraper.sink-consumers.central-db.base-url} names; this is that consumer, and it
 * writes {@code outputs/<country>/<site>/<file-name>}. The folder is fixed when this process
 * starts. The file name is
 * {@code realestate.csv-sink.file-name}, and {@code listings.csv} when that property is omitted.
 */
public final class CsvListingSinkServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CsvListingSinkServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private static final int MAX_BODY = 32 * 1024 * 1024;

    private final CsvSinkProperties properties;
    private final PathPair paths;
    private final String csvFileName;
    private final Clock clock;
    private final Map<String, CsvListingStore> stores = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "csv-listing-sink");
        thread.setDaemon(true);
        return thread;
    });

    private HttpServer server;

    public CsvListingSinkServer(CsvSinkProperties properties) {
        this(properties, Clock.systemDefaultZone());
    }

    CsvListingSinkServer(CsvSinkProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.csvFileName = CsvSinkProperties.fileNameOrDefault(properties.getFileName());
        this.paths = new PathPair(
                java.nio.file.Path.of(properties.getDirectory()).toAbsolutePath().normalize(),
                java.nio.file.Path.of(properties.getStateDirectory()).toAbsolutePath().normalize());
    }

    public void start() throws IOException {
        if (server != null) {
            return;
        }
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", properties.getPort());
        server = HttpServer.create(address, 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
        log.info("CSV listing sink listening on http://127.0.0.1:{}/v2/sinks ; spreadsheets under {}",
                server.getAddress().getPort(), paths.csvRoot());
    }

    public int port() {
        return server == null ? properties.getPort() : server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) {
                json(exchange, 401, Map.of("error", "unauthorized"));
                return;
            }
            String[] parts = path(exchange);
            if (parts == null) {
                json(exchange, 404, Map.of("error", "unknown path"));
                return;
            }
            String dataset = parts[0];
            String source = parts[1];
            String call = parts[2];
            String id = parts.length > 3 ? parts[3] : null;
            String country = query(exchange, "country");
            CsvListingStore store = stores.computeIfAbsent(dataset + "\n" + source + "\n" + country,
                    key -> new CsvListingStore(paths.csvRoot(), paths.stateRoot(), source, country, clock, csvFileName));
            switch (call) {
                case "records" -> records(exchange, store, id);
                case "hashes" -> hashes(exchange, store);
                case "fingerprint" -> fingerprint(exchange, store);
                default -> json(exchange, 404, Map.of("error", "unknown path"));
            }
        } catch (Exception e) {
            log.warn("CSV listing sink failed: {}", e.getMessage());
            try {
                json(exchange, 500, Map.of("error", e.getMessage() == null ? "failed" : e.getMessage()));
            } catch (Exception ignored) {
                // The response was already sent.
            }
        }
    }

    private void records(HttpExchange exchange, CsvListingStore store, String id) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method) && id != null) {
            String payload = store.payload(id);
            if (payload == null) {
                json(exchange, 404, Map.of("error", "unknown"));
                return;
            }
            json(exchange, 200, Map.of("id", id, "payload", JSON.readTree(payload)));
            return;
        }
        if (!"POST".equals(method) || id != null) {
            json(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length > MAX_BODY) {
            json(exchange, 400, Map.of("error", "batch is too large"));
            return;
        }
        JsonNode root = JSON.readTree(body);
        JsonNode array = root.get("records");
        if (array == null || !array.isArray()) {
            json(exchange, 400, Map.of("error", "records must be an array"));
            return;
        }
        List<CsvListingStore.Incoming> batch = new ArrayList<>();
        for (JsonNode node : array) {
            batch.add(incoming(node));
        }
        int written = store.write(batch);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("written", written);
        response.put("target", store.csvFile().toString());
        json(exchange, 200, response);
    }

    private void hashes(HttpExchange exchange, CsvListingStore store) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            json(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        StringBuilder lines = new StringBuilder();
        List<StoredListing> rows = store.records();
        for (StoredListing row : rows) {
            ObjectNode line = JSON.createObjectNode();
            line.put("id", row.id());
            if (row.hash() == null) {
                line.putNull("hash");
            } else {
                line.put("hash", row.hash());
            }
            line.put("deleted", row.deleted());
            lines.append(JSON.writeValueAsString(line)).append('\n');
        }
        byte[] bytes = lines.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson; charset=utf-8");
        exchange.getResponseHeaders().set("X-Sink-Count", Integer.toString(rows.size()));
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private void fingerprint(HttpExchange exchange, CsvListingStore store) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            json(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        CsvListingStore.Fingerprint fingerprint = store.fingerprint();
        json(exchange, 200, Map.of("count", fingerprint.count(), "fingerprint", fingerprint.value()));
    }

    private static CsvListingStore.Incoming incoming(JsonNode node) throws IOException {
        String id = text(node, "id");
        String hash = text(node, "hash");
        boolean deleted = node.path("deleted").asBoolean(false);
        JsonNode attributesNode = node.get("attributes");
        JsonNode payloadNode = node.get("payload");
        boolean marker = deleted && hash == null && (attributesNode == null || attributesNode.isNull())
                && (payloadNode == null || payloadNode.isNull());
        Map<String, Object> attributes = attributesNode == null || attributesNode.isNull()
                ? null
                : JSON.convertValue(attributesNode, MAP);
        String payload = payloadNode == null || payloadNode.isNull() ? null : JSON.writeValueAsString(payloadNode);
        return new CsvListingStore.Incoming(id, hash, deleted, marker, attributes, payload);
    }

    private boolean authorized(HttpExchange exchange) {
        String expected = properties.getToken();
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String header = properties.getTokenHeader() == null || properties.getTokenHeader().isBlank()
                ? "X-Internal-Token"
                : properties.getTokenHeader();
        return expected.equals(exchange.getRequestHeaders().getFirst(header));
    }

    /** {@code /v2/sinks/{dataset}/{source}/{call}[/{id}]}, or null. */
    private static String[] path(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getPath();
        if (raw == null) {
            return null;
        }
        String[] bits = raw.split("/");
        // "", "v2", "sinks", dataset, source, call, optional id
        if (bits.length < 6 || !"v2".equals(bits[1]) || !"sinks".equals(bits[2])) {
            return null;
        }
        String dataset = decode(bits[3]);
        String source = decode(bits[4]);
        String call = decode(bits[5]);
        if (bits.length == 6) {
            return new String[] {dataset, source, call};
        }
        if (bits.length == 7) {
            return new String[] {dataset, source, call, decode(bits[6])};
        }
        return null;
    }

    private static String query(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            if (name.equals(key)) {
                return eq < 0 ? "" : decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private record PathPair(java.nio.file.Path csvRoot, java.nio.file.Path stateRoot) {}
}
