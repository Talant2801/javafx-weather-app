package com.example.weather.api.dto;

/**
 * One geocoding hit. The real payload also carries population, postcodes, feature codes and a
 * handful of admin levels; the fields we do not model are dropped by
 * {@code FAIL_ON_UNKNOWN_PROPERTIES = false} rather than being carried around unused.
 */
public record GeocodingResultDto(
        String name,
        String country,
        String admin1,
        double latitude,
        double longitude,
        String timezone) {
}
