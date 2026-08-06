package com.example.weather.model;

/**
 * The unit system a {@link WeatherData} is expressed in.
 *
 * <p>Everything that crosses the API boundary is stored canonically in {@link #METRIC} (degrees
 * Celsius, km/h). Conversion happens once, on read, via {@link WeatherData#convertedTo(Units)}.
 * Keeping one canonical representation means the cache never has to store the same city twice and
 * conversion never gets applied twice by accident.
 *
 * <p>Modelled as an enum rather than a record because the set of supported unit systems is closed
 * and each constant owns its own conversion behaviour.
 */
public enum Units {

    /** Degrees Celsius and km/h. This is the canonical form used inside the app. */
    METRIC("°C", "km/h") {
        @Override
        public double temperatureFromCelsius(double celsius) {
            return celsius;
        }

        @Override
        public double windSpeedFromKmh(double kilometresPerHour) {
            return kilometresPerHour;
        }
    },

    /** Degrees Fahrenheit and mph. */
    IMPERIAL("°F", "mph") {
        @Override
        public double temperatureFromCelsius(double celsius) {
            return celsius * 9.0 / 5.0 + 32.0;
        }

        @Override
        public double windSpeedFromKmh(double kilometresPerHour) {
            return kilometresPerHour * 0.621_371_192;
        }
    };

    private final String temperatureSymbol;
    private final String windSpeedSymbol;

    Units(String temperatureSymbol, String windSpeedSymbol) {
        this.temperatureSymbol = temperatureSymbol;
        this.windSpeedSymbol = windSpeedSymbol;
    }

    /** Converts a canonical Celsius value into this unit system. */
    public abstract double temperatureFromCelsius(double celsius);

    /** Converts a canonical km/h value into this unit system. */
    public abstract double windSpeedFromKmh(double kilometresPerHour);

    /** e.g. {@code "°C"}. */
    public String temperatureSymbol() {
        return temperatureSymbol;
    }

    /** e.g. {@code "km/h"}. */
    public String windSpeedSymbol() {
        return windSpeedSymbol;
    }

    /** The other unit system; used by the UI toggle. */
    public Units toggled() {
        return this == METRIC ? IMPERIAL : METRIC;
    }
}
