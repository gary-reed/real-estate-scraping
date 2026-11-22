package com.realestate.dialect.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyFileEnvironmentPostProcessorTest {

    @Test
    void tokenInTheProxyFileBecomesTheWebshareProperty() throws Exception {
        Path dir = Files.createTempDirectory("proxies");
        Files.writeString(dir.resolve("proxies.txt"), """
                webshare | ISP_STATIC | Host=p.webshare.io | Port=10003
                $webshare_api_token = token-from-the-proxy-file
                """);
        System.setProperty("stw.config-dir", dir.toString());
        try {
            StandardEnvironment environment = new StandardEnvironment();
            environment.getSystemProperties().put("scraper.proxy.definitions-path", "proxies.txt");
            environment.getSystemProperties().put("scraper.proxy.webshare.api-token", "from-the-environment");
            new ProxyFileEnvironmentPostProcessor().applyFrom(environment);
            assertEquals("token-from-the-proxy-file", environment.getProperty("scraper.proxy.webshare.api-token"));
        } finally {
            System.clearProperty("stw.config-dir");
            System.clearProperty("scraper.proxy.definitions-path");
            System.clearProperty("scraper.proxy.webshare.api-token");
        }
    }

    @Test
    void aBlankTokenLeavesTheEnvironmentAlone() throws Exception {
        Path file = Files.createTempFile("proxies", ".txt");
        Files.writeString(file, "$webshare_api_token =\n");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getSystemProperties().put("scraper.proxy.webshare.api-token", "from-the-environment");
        try {
            ProxyFileEnvironmentPostProcessor.apply(environment, file);
            assertEquals("from-the-environment", environment.getProperty("scraper.proxy.webshare.api-token"));
            assertNull(ProxyFileEnvironmentPostProcessor.readToken(file));
        } finally {
            System.clearProperty("scraper.proxy.webshare.api-token");
        }
    }
}
