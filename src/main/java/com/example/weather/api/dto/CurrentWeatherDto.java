package com.example.weather.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** The {@code current} block. Field names are snake_case on the wire, so each one is mapped explicitly. */
public record CurrentWeatherDto(
        @JsonProperty("time") LocalDateTime time,
        @JsonProperty("temperature_2m") double temperature,
        @JsonProperty("apparent_temperature") double apparentTemperature,
        @JsonProperty("relative_humidity_2m") int relativeHumidity,
        @JsonProperty("wind_speed_10m") double windSpeed,
        @JsonProperty("weather_code") int weatherCode) {
}
