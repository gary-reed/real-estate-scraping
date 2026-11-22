package com.realestate.dialect.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The shape {@code CurrencyRateService} already requests: {@code GET /api/v1/currencyRate/EUR}
 * answers {@code {"rate": <USD per 1 EUR>}}. The number comes from {@link UsdRateClient}.
 * Point {@code scraper.currency-rate-endpoint} at {@code http://127.0.0.1:<server.port>/api/v1/currencyRate}.
 * The framework calls this path for every site currency except EUR. USD answers {@code 1.0}.
 * Price conversion does not read the rate the framework stores; {@code RealEstateParser} calls {@link UsdRateClient}.
 */
@RestController
@RequestMapping("/api/v1/currencyRate")
public class CurrencyRateController {

    private static final Logger log = LoggerFactory.getLogger(CurrencyRateController.class);

    private final UsdRateClient rates;

    public CurrencyRateController(UsdRateClient rates) {
        this.rates = rates;
    }

    @GetMapping("/{currency}")
    public ResponseEntity<Map<String, Double>> rate(@PathVariable String currency) {
        try {
            double rate = rates.usdPerUnit(currency);
            log.info("USD rate for {} = {}", currency, rate);
            return ResponseEntity.ok(Map.of("rate", rate));
        } catch (Exception e) {
            log.warn("USD rate for {} failed: {}", currency, e.toString());
            return ResponseEntity.status(502).build();
        }
    }
}
