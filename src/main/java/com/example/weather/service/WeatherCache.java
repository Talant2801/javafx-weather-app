package com.example.weather.service;

import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-city cache with a fixed time-to-live.
 *
 * <p>Weather does not change between two searches a minute apart, and Open-Meteo asks callers to be
 * polite; a short TTL removes almost all repeat traffic — especially from the history buttons,
 * where re-clicking a city is the common case.
 *
 * <p>Two deliberate choices worth defending:
 * <ul>
 *   <li><b>Only canonical {@link Units#METRIC} data is storable.</b> Caching converted values would
 *       need one entry per city <i>and</i> unit system, and would let a Fahrenheit reading be served
 *       to a Celsius view. Conversion happens on read instead, in the service.</li>
 *   <li><b>The {@link Clock} is injected.</b> Expiry can then be tested by moving a fake clock
 *       forward rather than by sleeping, so the tests stay fast and deterministic.</li>
 * </ul>
 *
 * <p>Thread-safe: entries live in a {@link ConcurrentHashMap} and are immutable once stored.
 */
public class WeatherCache {

    private static final Logger log = LoggerFactory.getLogger(WeatherCache.class);

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public WeatherCache(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    /** Visible for testing: lets a test drive expiry with a fake clock. */
    public WeatherCache(Duration ttl, Clock clock) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Cache TTL must be positive, was " + ttl);
        }
    }

    /** Returns the cached snapshot for a city if one is present and still fresh. */
    public Optional<WeatherData> get(String cityName) {
        String key = key(cityName);
        Entry entry = entries.get(key);
        if (entry == null) {
            log.debug("Cache miss for '{}'", key);
            return Optional.empty();
        }
        if (clock.instant().isAfter(entry.expiresAt())) {
            // Evict on read: there is no background sweeper, and an app-sized cache does not need one.
            entries.remove(key, entry);
            log.debug("Cache entry for '{}' expired", key);
            return Optional.empty();
        }
        log.debug("Cache hit for '{}'", key);
        return Optional.of(entry.data());
    }

    /**
     * Stores a snapshot under the given city name.
     *
     * @throws IllegalArgumentException if the data is not in canonical metric units
     */
    public void put(String cityName, WeatherData data) {
        Objects.requireNonNull(data, "data");
        if (data.units() != Units.METRIC) {
            throw new IllegalArgumentException("Only canonical METRIC data may be cached, was " + data.units());
        }
        entries.put(key(cityName), new Entry(data, clock.instant().plus(ttl)));
    }

    /** Drops one city, e.g. after a forced refresh. */
    public void invalidate(String cityName) {
        entries.remove(key(cityName));
    }

    /** Drops everything. */
    public void clear() {
        entries.clear();
    }

    /** Number of entries held, expired or not. Visible for testing. */
    public int size() {
        return entries.size();
    }

    /** "  Berlin " and "berlin" are the same city as far as the cache is concerned. */
    private static String key(String cityName) {
        return Objects.requireNonNull(cityName, "cityName").trim().toLowerCase(Locale.ROOT);
    }

    private record Entry(WeatherData data, Instant expiresAt) {
    }
}
