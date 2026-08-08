package com.example.weather.service;

import com.example.weather.api.WeatherClient;
import com.example.weather.exception.CityNotFoundException;
import com.example.weather.model.DailyForecast;
import com.example.weather.model.Location;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import com.example.weather.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The application's use case: "show me the weather in this city, in these units".
 *
 * <p>Turns the provider's two-step dance (geocode, then forecast) into one call, keeps the result
 * warm in the cache, trims the forecast to the number of days the UI shows, and converts to the
 * requested unit system on the way out.
 *
 * <p><b>Synchronous on purpose.</b> Nothing here touches threads. The controller owns concurrency —
 * it wraps these calls in a {@code CompletableFuture} so the UI stays responsive — which keeps this
 * class trivially testable: no executors, no latches, no timeouts in the tests.
 *
 * <p>Depends on the {@link WeatherClient} <i>interface</i>, never on the Open-Meteo implementation.
 */
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherClient client;
    private final WeatherCache cache;
    private final int forecastDays;

    public WeatherService(WeatherClient client, WeatherCache cache, Config config) {
        this.client = Objects.requireNonNull(client, "client");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.forecastDays = Objects.requireNonNull(config, "config").forecastDays();
    }

    /**
     * Resolves a city and returns its weather in the requested units.
     *
     * @param cityName raw user input
     * @param units    the unit system the caller wants to display
     * @return a snapshot expressed in {@code units}
     * @throws CityNotFoundException                              if the city cannot be resolved
     * @throws com.example.weather.exception.ApiUnavailableException    if the provider is unreachable
     * @throws com.example.weather.exception.MalformedResponseException if the response cannot be read
     */
    public WeatherData getWeather(String cityName, Units units) {
        Objects.requireNonNull(units, "units");
        String query = Objects.requireNonNull(cityName, "cityName").trim();
        if (query.isEmpty()) {
            // Fail before spending a request on something that cannot match.
            throw new CityNotFoundException(cityName);
        }

        Optional<WeatherData> cached = cache.get(query);
        if (cached.isPresent()) {
            log.debug("Serving '{}' from cache", query);
            return cached.get().convertedTo(units);
        }

        log.info("Fetching weather for '{}'", query);
        Location location = client.geocode(query);
        WeatherData fresh = trimForecast(client.fetchWeather(location));

        // Cache under what the user typed, so the next identical search hits, and under the
        // resolved name, so "berlin" and a history click on "Berlin" share one entry.
        cache.put(query, fresh);
        cache.put(location.name(), fresh);

        return fresh.convertedTo(units);
    }

    /** Drops the cached entry for a city so the next call goes to the network. */
    public void refresh(String cityName) {
        cache.invalidate(cityName);
    }

    /**
     * Keeps only the days the forecast strip shows.
     *
     * <p>Open-Meteo returns seven days whether we ask or not; trimming here rather than in the view
     * means the number of days is a configuration value, not a loop bound buried in the UI.
     */
    private WeatherData trimForecast(WeatherData data) {
        List<DailyForecast> days = data.dailyForecast();
        if (days.size() <= forecastDays) {
            return data;
        }
        return new WeatherData(
                data.location(),
                data.observedAt(),
                data.temperature(),
                data.feelsLike(),
                data.humidity(),
                data.windSpeed(),
                data.weatherCode(),
                days.subList(0, forecastDays),
                data.units());
    }
}
