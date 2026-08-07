package com.example.weather.util;

import com.example.weather.model.WeatherCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherCodeMapperTest {

    @ParameterizedTest(name = "code {0} -> \"{1}\"")
    @CsvSource({
            "0,  Clear sky",
            "1,  Mainly clear",
            "2,  Partly cloudy",
            "3,  Overcast",
            "45, Fog",
            "48, Depositing rime fog",
            "51, Light drizzle",
            "53, Moderate drizzle",
            "55, Dense drizzle",
            "56, Light freezing drizzle",
            "57, Dense freezing drizzle",
            "61, Slight rain",
            "63, Moderate rain",
            "65, Heavy rain",
            "66, Light freezing rain",
            "67, Heavy freezing rain",
            "71, Slight snowfall",
            "73, Moderate snowfall",
            "75, Heavy snowfall",
            "77, Snow grains",
            "80, Slight rain showers",
            "81, Moderate rain showers",
            "82, Violent rain showers",
            "85, Slight snow showers",
            "86, Heavy snow showers",
            "95, Thunderstorm",
            "96, Thunderstorm with slight hail",
            "99, Thunderstorm with heavy hail"
    })
    @DisplayName("every documented WMO code maps to its description")
    void mapsEveryDocumentedCode(int code, String expectedDescription) {
        WeatherCondition condition = WeatherCodeMapper.describe(code);

        assertThat(condition.code()).isEqualTo(code);
        assertThat(condition.description()).isEqualTo(expectedDescription);
        assertThat(condition.icon()).isNotBlank();
    }

    @ParameterizedTest(name = "unmapped code {0}")
    @ValueSource(ints = {-1, 4, 30, 100, 12_345})
    @DisplayName("an unknown code degrades to a placeholder instead of throwing")
    void fallsBackForUnknownCodes(int code) {
        // A code we have never seen must not break a search the user is watching.
        WeatherCondition condition = WeatherCodeMapper.describe(code);

        assertThat(condition.code()).isEqualTo(code);
        assertThat(condition.description()).isEqualTo("Unknown conditions");
        assertThat(condition.icon()).isNotBlank();
    }

    @Test
    @DisplayName("the text and icon shortcuts agree with describe()")
    void shortcutsMatchDescribe() {
        assertThat(WeatherCodeMapper.describeText(95)).isEqualTo(WeatherCodeMapper.describe(95).description());
        assertThat(WeatherCodeMapper.iconFor(95)).isEqualTo(WeatherCodeMapper.describe(95).icon());
    }
}
