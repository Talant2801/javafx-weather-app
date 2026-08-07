package com.example.weather.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Top level of {@code /v1/forecast}. */
public record ForecastResponseDto(
        @JsonProperty("timezone") String timezone,
        @JsonProperty("current") CurrentWeatherDto current,
        @JsonProperty("daily") DailyForecastDto daily) {
}
