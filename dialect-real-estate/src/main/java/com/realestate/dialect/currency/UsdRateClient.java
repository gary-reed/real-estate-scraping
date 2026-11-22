package com.realestate.dialect.currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How many USD one unit of a currency is worth, from the ECB rate published by
 * <a href="https://www.frankfurter.app">Frankfurter</a>. No API key.
 * {@code GET <url>?from=EUR&amp;to=USD} returns {@code rates.USD}.
 */
public final class UsdRateClient {

    /** Current Frankfurter host. {@code api.frankfurter.app} redirects here. */
    public static final String FRANKFURTER = "https://api.frankfurter.dev/v1/latest";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration TTL = Duration.ofHours(6);

    private final String url;
    private final HttpClient http;
    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public UsdRateClient() {
        this(FRANKFURTER);
    }

    public UsdRateClient(String url) {
        this(url, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    UsdRateClient(String url, HttpClient http) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("realestate.usd-rate.url is blank");
        }
        this.url = url.trim();
        this.http = http;
    }

    /**
     * USD per 1 unit of {@code currency}. USD itself is {@code 1.0} and does not call the network.
     * A successful rate is reused for six hours.
     */
    public double usdPerUnit(String currency) throws IOException, InterruptedException {
        String code = code(currency);
        if ("USD".equals(code)) {
            return 1.0;
        }
        long now = System.currentTimeMillis();
        Cached cached = cache.get(code);
        if (cached != null && cached.expiresAt > now) {
            return cached.rate;
        }
        double rate = fetch(code);
        cache.put(code, new Cached(rate, now + TTL.toMillis()));
        return rate;
    }

    private double fetch(String code) throws IOException, InterruptedException {
        String separator = url.contains("?") ? "&" : "?";
        URI uri = URI.create(url + separator + "from=" + code + "&to=USD");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "real-estate-scraping")
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("Frankfurter request failed for " + code + ": " + e.getMessage(), e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("Frankfurter returned " + response.statusCode() + " for " + code + ": " + response.body());
        }
        return rate(response.body(), code);
    }

    static double rate(String body, String code) throws IOException {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException e) {
            throw new IOException("Frankfurter response is not JSON for " + code + ": " + body, e);
        }
        JsonNode usd = root.path("rates").path("USD");
        if (!usd.isNumber()) {
            throw new IOException("Frankfurter response has no rates.USD for " + code + ": " + body);
        }
        double rate = usd.asDouble();
        if (rate <= 0 || Double.isNaN(rate) || Double.isInfinite(rate)) {
            throw new IOException("Frankfurter rate for " + code + " is not usable: " + rate);
        }
        return rate;
    }

    static String code(String currency) {
        if (currency == null || !currency.trim().matches("(?i)[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code: " + currency);
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private record Cached(double rate, long expiresAt) {}
}
