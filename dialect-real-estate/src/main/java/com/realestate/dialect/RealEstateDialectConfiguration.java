package com.realestate.dialect;

import com.realestate.dialect.currency.CurrencyRateController;
import com.realestate.dialect.currency.UsdRateClient;
import com.realestate.dialect.proxy.ProxyFileEnvironmentPostProcessor;
import com.realestate.dialect.sink.CsvListingSinkServer;
import com.realestate.dialect.sink.CsvSinkProperties;
import com.scraper.flavor.cms.dialect.Dialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers the real-estate dialect when this library is on a scraper's classpath (listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}).
 * Site parsers extend {@link RealEstateParser}; they are not created here.
 */
@AutoConfiguration
@EnableConfigurationProperties(CsvSinkProperties.class)
public class RealEstateDialectConfiguration {

    /** Copies {@code $webshare_api_token} from the proxy file before the framework reads it. */
    @Bean
    public static BeanFactoryPostProcessor webshareApiTokenFromProxyFile() {
        return new ProxyFileEnvironmentPostProcessor();
    }

    @Bean
    public Dialect realEstateDialect() {
        return new RealEstateDialect();
    }

    @Bean
    public RealEstateSettings realEstateSettings() {
        return new RealEstateSettings();
    }

    /**
     * Local sink consumer. The scraper points {@code scraper.sink-consumers.central-db} at it;
     * after a run the listings land in {@code outputs/<country>/<site>/<file-name>}.
     * {@code realestate.csv-sink.file-name} defaults to {@code listings.csv}.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "realestate.csv-sink", name = "enabled", havingValue = "true")
    public CsvListingSinkServer csvListingSinkServer(CsvSinkProperties properties) throws java.io.IOException {
        CsvListingSinkServer server = new CsvListingSinkServer(properties);
        server.start();
        return server;
    }

    /**
     * ECB rate via Frankfurter, quoted in USD. {@code realestate.usd-rate.url} overrides the host when the published URL moves.
     */
    @Bean
    public UsdRateClient usdRateClient(
            @Value("${realestate.usd-rate.url:" + UsdRateClient.FRANKFURTER + "}") String url) {
        return new UsdRateClient(url);
    }

    /**
     * {@code GET /api/v1/currencyRate/EUR} answers {@code {"rate": <how many USD is 1 EUR>}}.
     *
     * <p>Two different currencies are involved. This project stores listing prices in USD. The framework
     * still treats EUR as its own base: it calls this URL only when {@code scraper.currency} is not EUR,
     * and for an EUR site it leaves its stored rate at {@code 1.0}. An EUR scraper therefore still needs
     * a USD rate, and {@link UsdRateClient} loads that rate while the listing is parsed. The HTTP endpoint
     * stays so a USD or GBP scraper can answer the call the framework does make.</p>
     */
    @Bean
    @ConditionalOnWebApplication
    public CurrencyRateController currencyRateController(UsdRateClient usdRateClient) {
        return new CurrencyRateController(usdRateClient);
    }

    /** {@code GET /api/test/resolve-text}: runs the resolvers on a text, with the scraper's parser. */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnBean(RealEstateParser.class)
    public RealEstateResolveController realEstateResolveController(RealEstateParser parser) {
        return new RealEstateResolveController(parser);
    }
}
