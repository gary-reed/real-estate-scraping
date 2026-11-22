package com.realestate.dialect.proxy;

import com.scraper.commons.utils.UserConfigFiles;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code $webshare_api_token} from the gitignored proxy file and sets
 * {@code scraper.proxy.webshare.api-token}, which is the property the framework already uses
 * for the live proxy-list call. Registered from {@code RealEstateDialectConfiguration}.
 * A blank value leaves {@code WEBSHARE_API_TOKEN} alone.
 */
public class ProxyFileEnvironmentPostProcessor implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Pattern TOKEN = Pattern.compile("(?m)^\\$webshare_api_token\\s*=\\s*(\\S+)\\s*$");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        if (environment instanceof ConfigurableEnvironment configurable) {
            applyFrom(configurable);
        }
    }

    static void applyFrom(ConfigurableEnvironment environment) {
        String location = environment.getProperty("scraper.proxy.definitions-path", "proxies.txt");
        UserConfigFiles.find(location).ifPresent(path -> apply(environment, path));
    }

    static void apply(ConfigurableEnvironment environment, Path file) {
        String token = readToken(file);
        if (token == null) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "proxy-file", Map.of("scraper.proxy.webshare.api-token", token)));
        // application.yaml sets this key from WEBSHARE_API_TOKEN. System properties outrank that file.
        System.setProperty("scraper.proxy.webshare.api-token", token);
    }

    static String readToken(Path file) {
        try {
            Matcher matcher = TOKEN.matcher(Files.readString(file));
            if (!matcher.find() || matcher.group(1).isBlank()) {
                return null;
            }
            return matcher.group(1);
        } catch (Exception e) {
            return null;
        }
    }
}
