package com.example.weather.service;

import com.example.weather.model.Location;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WeatherCacheTest {

    private static final Location BERLIN = new Location("Berlin", "Germany", "State of Berlin", 52.52, 13.41);
    private static final Duration TTL = Duration.ofMinutes(10);

    private MutableClock clock;
    private WeatherCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-13T10:00:00Z"));
        cache = new WeatherCache(TTL, clock);
    }

    @Test
    @DisplayName("an unknown city is a miss")
    void missOnUnknownCity() {
        assertThat(cache.get("Berlin")).isEmpty();
    }

    @Test
    @DisplayName("a stored city is returned while it is fresh")
    void hitWithinTtl() {
        cache.put("Berlin", snapshot());

        clock.advance(Duration.ofMinutes(9).plusSeconds(59));

        assertThat(cache.get("Berlin")).isPresent();
    }

    @Test
    @DisplayName("an entry stops being served the moment the TTL passes")
    void missAfterTtl() {
        cache.put("Berlin", snapshot());

        clock.advance(TTL.plusSeconds(1));

        assertThat(cache.get("Berlin")).isEmpty();
    }

    @Test
    @DisplayName("expired entries are evicted on read rather than left to accumulate")
    void expiredEntryIsEvicted() {
        cache.put("Berlin", snapshot());
        clock.advance(TTL.plusSeconds(1));

        cache.get("Berlin");

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("keys ignore case and surrounding whitespace")
    void keysAreNormalised() {
        cache.put("  Berlin ", snapshot());

        assertThat(cache.get("BERLIN")).isPresent();
        assertThat(cache.get("berlin")).isPresent();
    }

    @Test
    @DisplayName("storing the same city twice replaces rather than duplicates")
    void putReplaces() {
        cache.put("Berlin", snapshot());
        cache.put("berlin", snapshot());

        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalidate drops one city and leaves the rest")
    void invalidateDropsOneCity() {
        cache.put("Berlin", snapshot());
        cache.put("Paris", snapshot());

        cache.invalidate("Berlin");

        assertThat(cache.get("Berlin")).isEmpty();
        assertThat(cache.get("Paris")).isPresent();
    }

    @Test
    @DisplayName("clear empties the cache")
    void clearEmpties() {
        cache.put("Berlin", snapshot());
        cache.put("Paris", snapshot());

        cache.clear();

        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("converted data is refused, so the cache can only ever hold canonical values")
    void refusesNonMetricData() {
        WeatherData imperial = snapshot().convertedTo(Units.IMPERIAL);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put("Berlin", imperial))
                .withMessageContaining("METRIC");
    }

    @Test
    @DisplayName("a non-positive TTL is a configuration mistake and fails fast")
    void rejectsNonPositiveTtl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WeatherCache(Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new WeatherCache(Duration.ofMinutes(-1)));
    }

    private static WeatherData snapshot() {
        return new WeatherData(
                BERLIN,
                LocalDateTime.of(2026, 8, 13, 12, 0),
                20.0, 18.0, 55, 10.0, 0,
                List.of(),
                Units.METRIC);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
