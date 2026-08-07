package com.example.weather.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * The {@code daily} block, which Open-Meteo returns as parallel arrays rather than a list of
 * objects: {@code time[i]}, {@code weather_code[i]}, {@code temperature_2m_max[i]} and
 * {@code temperature_2m_min[i]} together describe day {@code i}.
 *
 * <p>Keeping that awkward shape here, and zipping it into a list of
 * {@link com.example.weather.model.DailyForecast} in the client, is the whole reason DTOs exist as
 * a separate layer.
 */
public record DailyForecastDto(
        @JsonProperty("time") List<LocalDate> time,
        @JsonProperty("weather_code") List<Integer> weatherCode,
        @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
        @JsonProperty("temperature_2m_min") List<Double> temperatureMin) {
}
