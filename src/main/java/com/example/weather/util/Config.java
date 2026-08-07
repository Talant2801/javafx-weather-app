package com.example.weather.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Properties;

/**
 * Typed access to {@code application.properties}.
 *
 * <p>Exists so that no URL, timeout or TTL is ever written as a literal inside a class that does
 * real work: swapping the forecast endpoint for a local stub is a config change, not a code change.
 * Values resolve system property first, then the properties file, so a run can be redirected with
 * {@code -Dopenmeteo.forecast.url=...} without rebuilding.
 */
public final class Config {

    private static final String RESOURCE = "/application.properties";

    private final Properties properties;

    /** Visible for testing: build a config over an arbitrary property set. */
    public Config(Properties properties) {
        this.properties = new Properties();
        this.properties.putAll(properties);
    }

    /** Loads {@code /application.properties} from the classpath. */
    public static Config load() {
        Properties props = new Properties();
        try (InputStream in = Config.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            props.load(in);
        } catch (IOException e) {
            // Unreadable config is a packaging bug, not a runtime condition the user can act on.
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
        return new Config(props);
    }

    public String geocodingUrl() {
        return string("openmeteo.geocoding.url");
    }

    public String forecastUrl() {
        return string("openmeteo.forecast.url");
    }

    public Duration connectTimeout() {
        return Duration.ofSeconds(integer("http.connect.timeout.seconds"));
    }

    public Duration requestTimeout() {
        return Duration.ofSeconds(integer("http.request.timeout.seconds"));
    }

    public Duration cacheTtl() {
        return Duration.ofMinutes(integer("cache.ttl.minutes"));
    }

    public int forecastDays() {
        return integer("forecast.days");
    }

    public int historySize() {
        return integer("history.size");
    }

    private String string(String key) {
        String value = System.getProperty(key, properties.getProperty(key));
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing configuration key: " + key);
        }
        return value.trim();
    }

    private int integer(String key) {
        String value = string(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Configuration key " + key + " is not a number: " + value, e);
        }
    }
}
