package com.example.weather.model;

import java.util.Objects;

/**
 * The display form of a WMO weather code, produced by
 * {@link com.example.weather.util.WeatherCodeMapper}.
 *
 * <p>Kept out of {@link WeatherData} on purpose: the raw code is the fact the API reports, while
 * the description and icon are a presentation concern that could be localised or re-themed without
 * touching the domain data.
 *
 * @param code        the original WMO code, useful for logging unmapped values
 * @param description human-readable text, e.g. {@code "Light drizzle"}
 * @param icon        a single emoji glyph used as the weather icon
 */
public record WeatherCondition(int code, String description, String icon) {

    public WeatherCondition {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
    }
}
