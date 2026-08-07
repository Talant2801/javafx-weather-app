package com.example.weather.api;

import com.example.weather.exception.ApiUnavailableException;
import com.example.weather.exception.CityNotFoundException;
import com.example.weather.exception.MalformedResponseException;
import com.example.weather.model.Location;
import com.example.weather.model.WeatherData;

/**
 * The application's view of a weather provider.
 *
 * <p>Two operations, both blocking, both speaking only domain types. Everything above this
 * interface — the service, the controller — depends on it and never on
 * {@link OpenMeteoClient}, which is what makes the provider swappable (OpenWeatherMap would be a
 * second implementation, not an edit) and what lets the service tests run against a Mockito mock
 * with no HTTP in sight.
 *
 * <p>Implementations are expected to be thread-safe: the service calls them from background threads.
 */
public interface WeatherClient {

    /**
     * Resolves a user-typed place name to coordinates, taking the provider's best match.
     *
     * @param cityName raw user input, e.g. {@code "berlin"}
     * @return the matched location
     * @throws CityNotFoundException      if nothing matched
     * @throws ApiUnavailableException    if the provider could not be reached
     * @throws MalformedResponseException if the response could not be understood
     */
    Location geocode(String cityName);

    /**
     * Fetches current conditions and the daily forecast for a location.
     *
     * @param location a location, normally one returned by {@link #geocode(String)}
     * @return a snapshot in canonical {@link com.example.weather.model.Units#METRIC} units
     * @throws ApiUnavailableException    if the provider could not be reached
     * @throws MalformedResponseException if the response could not be understood
     */
    WeatherData fetchWeather(Location location);
}
