package com.example.weather.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One day of the forecast strip.
 *
 * <p>Temperatures are expressed in the unit system of the enclosing {@link WeatherData}; a
 * {@code DailyForecast} is never handed around on its own, so it does not repeat that field.
 *
 * @param date           the local date at the queried location
 * @param weatherCode    WMO code, translated for display by
 *                       {@link com.example.weather.util.WeatherCodeMapper}
 * @param maxTemperature daily high
 * @param minTemperature daily low
 */
public record DailyForecast(LocalDate date, int weatherCode, double maxTemperature, double minTemperature) {

    public DailyForecast {
        Objects.requireNonNull(date, "date");
    }

    /** Returns a copy with both temperatures converted from canonical Celsius into {@code units}. */
    DailyForecast convertedTo(Units units) {
        return new DailyForecast(
                date,
                weatherCode,
                units.temperatureFromCelsius(maxTemperature),
                units.temperatureFromCelsius(minTemperature));
    }
}
