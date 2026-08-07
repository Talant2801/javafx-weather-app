package com.example.weather.api;

import com.example.weather.model.Location;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import com.example.weather.util.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hits the real Open-Meteo API.
 *
 * <p>Disabled unless {@code RUN_INTEGRATION_TESTS=true}, so a normal {@code ./gradlew test} stays
 * fast, offline and deterministic. Run it with:
 *
 * <pre>RUN_INTEGRATION_TESTS=true ./gradlew test --tests '*OpenMeteoClientIT'</pre>
 *
 * <p>Its job is not to assert on the weather — that changes by the minute — but to catch the thing
 * unit tests structurally cannot: the provider changing its contract underneath us.
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
class OpenMeteoClientIT {

    private final WeatherClient client = new OpenMeteoClient(Config.load());

    @Test
    @DisplayName("resolves a real city and fetches a usable forecast")
    void endToEndAgainstLiveApi() {
        Location location = client.geocode("Warsaw");

        assertThat(location.name()).isEqualTo("Warsaw");
        assertThat(location.country()).isEqualTo("Poland");
        assertThat(location.latitude()).isBetween(51.0, 53.0);
        assertThat(location.longitude()).isBetween(20.0, 22.0);

        WeatherData data = client.fetchWeather(location);

        assertThat(data.units()).isEqualTo(Units.METRIC);
        assertThat(data.temperature()).isBetween(-60.0, 60.0);
        assertThat(data.humidity()).isBetween(0, 100);
        assertThat(data.windSpeed()).isNotNegative();
        assertThat(data.observedAt()).isNotNull();
        assertThat(data.dailyForecast()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(data.dailyForecast()).allSatisfy(day -> {
            assertThat(day.date()).isNotNull();
            assertThat(day.maxTemperature()).isGreaterThanOrEqualTo(day.minTemperature());
        });
    }
}
