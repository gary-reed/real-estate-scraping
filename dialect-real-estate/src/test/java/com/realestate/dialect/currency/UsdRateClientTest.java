package com.realestate.dialect.currency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsdRateClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsTheUsdRateFrankfurterPublishes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/latest", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"amount\":1.0,\"base\":\"EUR\",\"rates\":{\"USD\":1.0968}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/latest";
            UsdRateClient client = new UsdRateClient(url);
            assertEquals(1.0968, client.usdPerUnit("eur"), 0.000001);
            assertEquals(1.0968, client.usdPerUnit("EUR"), 0.000001);
            assertEquals(1, calls.get());
            assertEquals(1.0, client.usdPerUnit("USD"));
            assertEquals(1, calls.get());

            CurrencyRateController controller = new CurrencyRateController(client);
            ResponseEntity<Map<String, Double>> response = controller.rate("EUR");
            assertEquals(200, response.getStatusCode().value());
            assertEquals(1.0968, response.getBody().get("rate"), 0.000001);

            MockMvc http = MockMvcBuilders.standaloneSetup(controller).build();
            http.perform(get("/api/v1/currencyRate/EUR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rate").value(1.0968));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAResponseWithoutAUsdRate() {
        assertThrows(Exception.class, () -> UsdRateClient.rate("{\"rates\":{}}", "EUR"));
        assertThrows(IllegalArgumentException.class, () -> new UsdRateClient(UsdRateClient.FRANKFURTER).usdPerUnit("EU"));
    }

    @Test
    void liveFrankfurterReturnsAUsableEurToUsdRate() throws Exception {
        double rate = new UsdRateClient().usdPerUnit("EUR");
        assertTrue(rate > 0.8 && rate < 1.5, "EUR to USD rate was " + rate);
        assertTrue(JSON.readTree("{\"rate\":" + rate + "}").get("rate").isNumber());
    }
}
