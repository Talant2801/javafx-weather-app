package com.example.weather.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * A complete weather snapshot for one location: current conditions plus the daily forecast strip.
 *
 * <p>Instances created by the API client are always in {@link Units#METRIC} — that is the canonical
 * form the cache stores. {@link #convertedTo(Units)} produces the display copy.
 *
 * @param location       the place these readings belong to
 * @param observedAt     local time of the current-conditions reading at that place
 * @param temperature    current temperature, in {@code units}
 * @param feelsLike      apparent temperature, in {@code units}
 * @param humidity       relative humidity, 0..100 %
 * @param windSpeed      wind speed at 10 m, in {@code units}
 * @param weatherCode    WMO code for current conditions
 * @param dailyForecast  upcoming days, oldest first; never null
 * @param units          the unit system the numeric fields above are expressed in
 */
public record WeatherData(
        Location location,
        LocalDateTime observedAt,
        double temperature,
        double feelsLike,
        int humidity,
        double windSpeed,
        int weatherCode,
        List<DailyForecast> dailyForecast,
        Units units) {

    public WeatherData {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(observedAt, "observedAt");
        Objects.requireNonNull(units, "units");
        // Defensive copy: records are only as immutable as the objects they hold.
        dailyForecast = List.copyOf(Objects.requireNonNull(dailyForecast, "dailyForecast"));
        if (humidity < 0 || humidity > 100) {
            throw new IllegalArgumentException("Humidity out of range: " + humidity);
        }
    }

    /**
     * Returns this snapshot expressed in {@code units}.
     *
     * <p>Only legal on a canonical {@link Units#METRIC} instance — converting an already-converted
     * snapshot is a bug, so it fails loudly instead of silently double-converting.
     */
    public WeatherData convertedTo(Units target) {
        Objects.requireNonNull(target, "target");
        if (this.units != Units.METRIC) {
            throw new IllegalStateException("Can only convert from canonical METRIC data, was " + this.units);
        }
        if (target == Units.METRIC) {
            return this;
        }
        return new WeatherData(
                location,
                observedAt,
                target.temperatureFromCelsius(temperature),
                target.temperatureFromCelsius(feelsLike),
                humidity,
                target.windSpeedFromKmh(windSpeed),
                weatherCode,
                dailyForecast.stream().map(day -> day.convertedTo(target)).toList(),
                target);
    }
}
