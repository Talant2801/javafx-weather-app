package com.example.weather.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ConfigTest {

    @Test
    @DisplayName("the packaged application.properties is complete and parseable")
    void loadsShippedDefaults() {
        // Guards against a key being renamed in code but not in the file, which would otherwise
        // only surface as a crash on startup.
        Config config = Config.load();

        assertThat(config.geocodingUrl()).startsWith("https://");
        assertThat(config.forecastUrl()).startsWith("https://");
        assertThat(config.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(config.cacheTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(config.forecastDays()).isEqualTo(5);
        assertThat(config.historySize()).isEqualTo(5);
    }

    @Test
    @DisplayName("a system property wins over the properties file")
    void systemPropertyOverridesFile() {
        Properties props = new Properties();
        props.setProperty("cache.ttl.minutes", "10");
        Config config = new Config(props);

        System.setProperty("cache.ttl.minutes", "1");
        try {
            assertThat(config.cacheTtl()).isEqualTo(Duration.ofMinutes(1));
        } finally {
            System.clearProperty("cache.ttl.minutes");
        }

        assertThat(config.cacheTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("a missing key fails loudly instead of defaulting to something surprising")
    void missingKeyThrows() {
        Config config = new Config(new Properties());

        assertThatIllegalStateException()
                .isThrownBy(config::forecastUrl)
                .withMessageContaining("openmeteo.forecast.url");
    }

    @Test
    @DisplayName("a blank value counts as missing")
    void blankValueThrows() {
        Properties props = new Properties();
        props.setProperty("openmeteo.forecast.url", "   ");

        assertThatIllegalStateException().isThrownBy(() -> new Config(props).forecastUrl());
    }

    @Test
    @DisplayName("a non-numeric value names the key it came from")
    void nonNumericValueThrows() {
        Properties props = new Properties();
        props.setProperty("forecast.days", "five");

        assertThatIllegalStateException()
                .isThrownBy(() -> new Config(props).forecastDays())
                .withMessageContaining("forecast.days")
                .withMessageContaining("five");
    }

    @Test
    @DisplayName("the config copies the properties it was given")
    void copiesInputProperties() {
        Properties props = new Properties();
        props.setProperty("forecast.days", "5");
        Config config = new Config(props);

        props.setProperty("forecast.days", "99");

        assertThat(config.forecastDays()).isEqualTo(5);
    }
}
