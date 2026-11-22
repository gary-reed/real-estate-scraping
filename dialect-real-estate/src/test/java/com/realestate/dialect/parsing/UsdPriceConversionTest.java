package com.realestate.dialect.parsing;

import com.realestate.dialect.RealEstateParser;
import com.realestate.dialect.currency.UsdRateClient;

import com.realestate.dialect.model.RealEstateListing;

import com.scraper.commons.core.domain.WorkResult;
import com.realestate.dialect.model.Basic;
import com.realestate.dialect.model.Basic.OperationEnum;
import com.realestate.dialect.model.Building;
import com.realestate.dialect.model.Furnish;
import com.realestate.dialect.model.Interior;
import com.realestate.dialect.model.Property;
import com.scraper.flavor.cms.model.ScrapingDetails;
import com.realestate.dialect.model.Transaction;
import com.realestate.dialect.parsing.language.ParserLanguage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class UsdPriceConversionTest {

    private static final double GBP_TO_USD = 1.27;

    /** Minimal parser: conversion only; parseListingDetails unused. */
    private static final class TestParser extends RealEstateParser {
        private final double rate;

        TestParser(double rate) {
            this.rate = rate;
        }

        @Override
        public ParserLanguage getLanguage() {
            return ParserLanguage.ENGLISH;
        }

        @Override
        protected double getExchangeRate() {
            return rate;
        }

        void bind(UsdRateClient client) {
            useUsdRateClient(client);
        }

        @Override
        public RealEstateListing parseListingDetails(WorkResult result) {
            throw new UnsupportedOperationException();
        }
    }

    private static RealEstateListing listing(String currency, Double price, Double originalPrice) {
        RealEstateListing d = new RealEstateListing("test");
        Basic basic = new Basic();
        basic.setCurrency(currency);
        basic.setOperation(OperationEnum.BUY);
        basic.setPrice(price);
        basic.setOriginalPrice(originalPrice);
        d.setBasic(basic);
        d.setProperty(new Property());
        d.setBuilding(new Building());
        d.setInterior(new Interior());
        d.setFurnish(new Furnish());
        d.setTransaction(new Transaction());
        d.setScrapingDetails(new ScrapingDetails());
        return d;
    }

    @Test
    void usdCurrencyIsLeftUnchanged() {
        RealEstateListing d = listing("USD", 250000.0, null);
        assertSame(d, new TestParser(GBP_TO_USD).postProcess(d));
        assertEquals(250000.0, d.getBasic().getPrice());
        assertNull(d.getBasic().getOriginalPrice());
    }

    @Test
    void eurUsesTheDialectClientWhenTheFrameworkRateStaysAtOne() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/latest", exchange -> {
            byte[] body = "{\"rates\":{\"USD\":1.0968}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/latest";
            TestParser parser = new TestParser(1.0);
            parser.bind(new UsdRateClient(url));
            RealEstateListing d = listing("EUR", 100_000.0, null);
            assertSame(d, parser.postProcess(d));
            assertEquals(100_000.0, d.getBasic().getOriginalPrice());
            assertEquals(109_680.0, d.getBasic().getPrice(), 0.001);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void eurCurrencyIsConvertedToUsd() {
        RealEstateListing d = listing("EUR", 100_000.0, null);
        assertSame(d, new TestParser(1.10).postProcess(d));
        assertEquals(100_000.0, d.getBasic().getOriginalPrice());
        assertEquals(110_000.0, d.getBasic().getPrice(), 1e-9);
    }

    @Test
    void onlyPriceBecomesOriginalAndConverted() {
        RealEstateListing d = listing("GBP", 100_000.0, null);
        assertSame(d, new TestParser(GBP_TO_USD).postProcess(d));
        assertEquals(100_000.0, d.getBasic().getOriginalPrice());
        assertEquals(100_000.0 * GBP_TO_USD, d.getBasic().getPrice(), 1e-9);
    }

    @Test
    void equalPriceAndOriginalAreTreatedAsSiteAmount() {
        RealEstateListing d = listing("GBP", 200_000.0, 200_000.0);
        assertSame(d, new TestParser(GBP_TO_USD).postProcess(d));
        assertEquals(200_000.0, d.getBasic().getOriginalPrice());
        assertEquals(200_000.0 * GBP_TO_USD, d.getBasic().getPrice(), 1e-9);
    }

    @Test
    void alreadyConvertedConsistentPairIsAccepted() {
        double gbp = 300_000.0;
        RealEstateListing d = listing("GBP", gbp * GBP_TO_USD, gbp);
        assertSame(d, new TestParser(GBP_TO_USD).postProcess(d));
        assertEquals(gbp, d.getBasic().getOriginalPrice());
        assertEquals(gbp * GBP_TO_USD, d.getBasic().getPrice(), 1e-9);
    }

    @Test
    void swappedFieldsAreNormalizedWhenRateLinksThem() {
        double gbp = 250_000.0;
        RealEstateListing d = listing("GBP", gbp, gbp * GBP_TO_USD);
        assertSame(d, new TestParser(GBP_TO_USD).postProcess(d));
        assertEquals(gbp, d.getBasic().getOriginalPrice());
        assertEquals(gbp * GBP_TO_USD, d.getBasic().getPrice(), 1e-9);
    }

    @Test
    void alreadyConvertedInconsistentPairFails() {
        RealEstateListing d = listing("GBP", 999_999.0, 300_000.0);
        assertNull(new TestParser(GBP_TO_USD).postProcess(d));
    }

    @Test
    void onlyOriginalPriceFillsUsdPrice() {
        RealEstateListing d = listing("GBP", null, 50_000.0);
        assertSame(d, new TestParser(GBP_TO_USD).postProcess(d));
        assertEquals(50_000.0, d.getBasic().getOriginalPrice());
        assertEquals(50_000.0 * GBP_TO_USD, d.getBasic().getPrice(), 1e-9);
    }
}
