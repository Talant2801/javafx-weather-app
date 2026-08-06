package com.example.weather.model;

import java.util.Objects;

/**
 * A geographic place resolved from a user-typed city name.
 *
 * <p>This is a domain record, not an API response: the geocoding provider returns far more fields
 * (population, feature codes, elevation, ...) and the client layer keeps those out of here.
 *
 * @param name      the city name as the provider spells it, e.g. {@code "Berlin"}
 * @param country   the country name, e.g. {@code "Germany"}; may be blank when unknown
 * @param admin1    first-level administrative area (state / region); may be blank
 * @param latitude  decimal degrees, -90..90
 * @param longitude decimal degrees, -180..180
 */
public record Location(String name, String country, String admin1, double latitude, double longitude) {

    public Location {
        Objects.requireNonNull(name, "name");
        country = country == null ? "" : country;
        admin1 = admin1 == null ? "" : admin1;
        if (name.isBlank()) {
            throw new IllegalArgumentException("Location name must not be blank");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude out of range: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude out of range: " + longitude);
        }
    }

    /** Human-readable label for the header, e.g. {@code "Berlin, Brandenburg, Germany"}. */
    public String displayName() {
        StringBuilder sb = new StringBuilder(name);
        if (!admin1.isBlank() && !admin1.equals(name)) {
            sb.append(", ").append(admin1);
        }
        if (!country.isBlank()) {
            sb.append(", ").append(country);
        }
        return sb.toString();
    }
}
