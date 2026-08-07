package com.example.weather.api.dto;

import java.util.List;

/**
 * Top level of {@code /v1/search}.
 *
 * <p>{@code results} is absent entirely — not empty — when nothing matches, so this field is
 * nullable and the client treats null and empty the same way.
 */
public record GeocodingResponseDto(List<GeocodingResultDto> results) {
}
