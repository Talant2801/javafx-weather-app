package com.example.weather.exception;

/** The geocoding provider returned no match for what the user typed. */
public class CityNotFoundException extends WeatherApiException {

    private final String cityName;

    public CityNotFoundException(String cityName) {
        super("No location matched \"" + cityName + "\"");
        this.cityName = cityName;
    }

    /** The search term as the user typed it, so the UI can quote it back. */
    public String cityName() {
        return cityName;
    }
}
