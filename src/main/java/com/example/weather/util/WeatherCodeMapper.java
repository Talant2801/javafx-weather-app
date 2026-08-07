package com.example.weather.util;

import com.example.weather.model.WeatherCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Translates WMO 4677 weather codes — the integers Open-Meteo reports — into text and an icon.
 *
 * <p>A dedicated class rather than a switch inside the controller: the mapping is provider-agnostic
 * (WMO codes are a standard, not an Open-Meteo invention), it is the one piece of display logic
 * that is worth exhaustively testing, and localising the app later means changing this file only.
 */
public final class WeatherCodeMapper {

    private static final Logger log = LoggerFactory.getLogger(WeatherCodeMapper.class);

    private static final Map<Integer, WeatherCondition> CONDITIONS = Map.ofEntries(
            entry(0, "Clear sky", "☀"),
            entry(1, "Mainly clear", "🌤"),
            entry(2, "Partly cloudy", "⛅"),
            entry(3, "Overcast", "☁"),
            entry(45, "Fog", "🌫"),
            entry(48, "Depositing rime fog", "🌫"),
            entry(51, "Light drizzle", "🌦"),
            entry(53, "Moderate drizzle", "🌦"),
            entry(55, "Dense drizzle", "🌦"),
            entry(56, "Light freezing drizzle", "🌧"),
            entry(57, "Dense freezing drizzle", "🌧"),
            entry(61, "Slight rain", "🌦"),
            entry(63, "Moderate rain", "🌧"),
            entry(65, "Heavy rain", "🌧"),
            entry(66, "Light freezing rain", "🌧"),
            entry(67, "Heavy freezing rain", "🌧"),
            entry(71, "Slight snowfall", "🌨"),
            entry(73, "Moderate snowfall", "🌨"),
            entry(75, "Heavy snowfall", "❄"),
            entry(77, "Snow grains", "🌨"),
            entry(80, "Slight rain showers", "🌦"),
            entry(81, "Moderate rain showers", "🌧"),
            entry(82, "Violent rain showers", "⛈"),
            entry(85, "Slight snow showers", "🌨"),
            entry(86, "Heavy snow showers", "❄"),
            entry(95, "Thunderstorm", "⛈"),
            entry(96, "Thunderstorm with slight hail", "⛈"),
            entry(99, "Thunderstorm with heavy hail", "⛈"));

    private static final String UNKNOWN_DESCRIPTION = "Unknown conditions";
    private static final String UNKNOWN_ICON = "❔";

    private WeatherCodeMapper() {
    }

    private static Map.Entry<Integer, WeatherCondition> entry(int code, String description, String icon) {
        return Map.entry(code, new WeatherCondition(code, description, icon));
    }

    /**
     * Describes a WMO code.
     *
     * <p>An unrecognised code returns a neutral placeholder rather than throwing: a code we have
     * not seen before is not a reason to fail a search the user is watching. It is logged so the
     * gap can be closed.
     */
    public static WeatherCondition describe(int weatherCode) {
        WeatherCondition condition = CONDITIONS.get(weatherCode);
        if (condition == null) {
            log.warn("Unmapped WMO weather code: {}", weatherCode);
            return new WeatherCondition(weatherCode, UNKNOWN_DESCRIPTION, UNKNOWN_ICON);
        }
        return condition;
    }

    /** Convenience for labels that only need the words. */
    public static String describeText(int weatherCode) {
        return describe(weatherCode).description();
    }

    /** Convenience for labels that only need the glyph. */
    public static String iconFor(int weatherCode) {
        return describe(weatherCode).icon();
    }
}
