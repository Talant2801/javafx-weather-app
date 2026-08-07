package com.example.weather.api;

import com.example.weather.exception.ApiUnavailableException;
import com.example.weather.exception.CityNotFoundException;
import com.example.weather.exception.MalformedResponseException;
import com.example.weather.exception.RateLimitException;
import com.example.weather.model.DailyForecast;
import com.example.weather.model.Location;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import com.example.weather.util.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Exercises the client against a mocked {@link HttpClient}: real fixtures captured from Open-Meteo
 * go in, domain objects or domain exceptions come out. No network, no sleeping, no flakiness.
 */
@ExtendWith(MockitoExtension.class)
class OpenMeteoClientTest {

    private static final Location BERLIN = new Location("Berlin", "Germany", "State of Berlin", 52.52437, 13.41053);

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        client = new OpenMeteoClient(httpClient, testConfig());
    }

    @Nested
    @DisplayName("geocode")
    class Geocode {

        @Test
        @DisplayName("maps a real geocoding response onto a Location")
        void mapsGeocodingResponse() throws Exception {
            respondWith(200, fixture("/geocoding-berlin.json"));

            Location location = client.geocode("Berlin");

            assertThat(location.name()).isEqualTo("Berlin");
            assertThat(location.country()).isEqualTo("Germany");
            assertThat(location.admin1()).isEqualTo("State of Berlin");
            assertThat(location.latitude()).isEqualTo(52.52437);
            assertThat(location.longitude()).isEqualTo(13.41053);
            // Population, postcodes and feature codes are in the fixture and must not have leaked
            // into the domain — Location has no room for them, which is the point.
        }

        @Test
        @DisplayName("URL-encodes the city name and asks for a single result")
        void buildsTheRequestUrl() throws Exception {
            respondWith(200, fixture("/geocoding-berlin.json"));

            client.geocode("San Francisco");

            assertThat(capturedRequest().uri().toString())
                    .isEqualTo("https://geo.test/search?name=San+Francisco&count=1");
        }

        @Test
        @DisplayName("an absent results block means the city was not found")
        void throwsWhenNoResults() throws Exception {
            // Open-Meteo omits "results" entirely rather than returning an empty array.
            respondWith(200, fixture("/geocoding-no-results.json"));

            assertThatExceptionOfType(CityNotFoundException.class)
                    .isThrownBy(() -> client.geocode("zzzzznotacity"))
                    .withMessageContaining("zzzzznotacity")
                    .satisfies(e -> assertThat(e.cityName()).isEqualTo("zzzzznotacity"));
        }

        @Test
        @DisplayName("blank input never reaches the network")
        void rejectsBlankInput() {
            assertThatExceptionOfType(CityNotFoundException.class)
                    .isThrownBy(() -> client.geocode("   "));
        }
    }

    @Nested
    @DisplayName("fetchWeather")
    class FetchWeather {

        @Test
        @DisplayName("maps a real forecast response onto canonical metric WeatherData")
        void mapsForecastResponse() throws Exception {
            respondWith(200, fixture("/forecast-berlin.json"));

            WeatherData data = client.fetchWeather(BERLIN);

            assertThat(data.location()).isEqualTo(BERLIN);
            assertThat(data.observedAt()).isEqualTo(LocalDateTime.of(2026, 8, 13, 1, 15));
            assertThat(data.temperature()).isEqualTo(18.2);
            assertThat(data.feelsLike()).isEqualTo(16.9);
            assertThat(data.humidity()).isEqualTo(54);
            assertThat(data.windSpeed()).isEqualTo(6.7);
            assertThat(data.weatherCode()).isZero();
            // The client always yields metric; conversion is the service's job, on read.
            assertThat(data.units()).isEqualTo(Units.METRIC);
        }

        @Test
        @DisplayName("zips the parallel daily arrays into ordered days")
        void zipsDailyArrays() throws Exception {
            respondWith(200, fixture("/forecast-berlin.json"));

            WeatherData data = client.fetchWeather(BERLIN);

            assertThat(data.dailyForecast()).hasSize(7);
            assertThat(data.dailyForecast())
                    .extracting(DailyForecast::date)
                    .startsWith(LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 14));

            DailyForecast firstDay = data.dailyForecast().getFirst();
            assertThat(firstDay.weatherCode()).isZero();
            assertThat(firstDay.maxTemperature()).isEqualTo(27.4);
            assertThat(firstDay.minTemperature()).isEqualTo(15.4);
        }

        @Test
        @DisplayName("sends the coordinates and the fields the UI needs")
        void buildsTheRequestUrl() throws Exception {
            respondWith(200, fixture("/forecast-berlin.json"));

            client.fetchWeather(BERLIN);

            assertThat(capturedRequest().uri().toString())
                    .startsWith("https://forecast.test/forecast?latitude=52.52437&longitude=13.41053")
                    .contains("current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code")
                    .contains("daily=weather_code,temperature_2m_max,temperature_2m_min")
                    .endsWith("&timezone=auto");
        }

        @Test
        @DisplayName("applies the configured request timeout")
        void appliesRequestTimeout() throws Exception {
            respondWith(200, fixture("/forecast-berlin.json"));

            client.fetchWeather(BERLIN);

            assertThat(capturedRequest().timeout()).hasValue(java.time.Duration.ofSeconds(10));
        }

        @Test
        @DisplayName("daily arrays of differing lengths are rejected rather than mis-paired")
        void rejectsMismatchedDailyArrays() throws Exception {
            respondWith(200, fixture("/forecast-mismatched-arrays.json"));

            assertThatExceptionOfType(MalformedResponseException.class)
                    .isThrownBy(() -> client.fetchWeather(BERLIN))
                    .withMessageContaining("mismatched");
        }

        @Test
        @DisplayName("a response with no current block is malformed, not an empty reading")
        void rejectsMissingCurrentBlock() throws Exception {
            respondWith(200, "{\"timezone\":\"Europe/Berlin\"}");

            assertThatExceptionOfType(MalformedResponseException.class)
                    .isThrownBy(() -> client.fetchWeather(BERLIN));
        }
    }

    @Nested
    @DisplayName("failure translation")
    class FailureTranslation {

        @Test
        @DisplayName("a timeout becomes ApiUnavailableException, not HttpTimeoutException")
        void translatesTimeout() throws Exception {
            when(httpClient.<String>send(any(), any())).thenThrow(new HttpTimeoutException("request timed out"));

            assertThatExceptionOfType(ApiUnavailableException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withMessageContaining("too long")
                    .withCauseInstanceOf(HttpTimeoutException.class);
        }

        @Test
        @DisplayName("an unreachable network becomes ApiUnavailableException")
        void translatesConnectFailure() throws Exception {
            when(httpClient.<String>send(any(), any())).thenThrow(new ConnectException("Connection refused"));

            assertThatExceptionOfType(ApiUnavailableException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withMessageContaining("reach");
        }

        @Test
        @DisplayName("a generic IOException becomes ApiUnavailableException")
        void translatesIoException() throws Exception {
            when(httpClient.<String>send(any(), any())).thenThrow(new IOException("socket closed"));

            assertThatExceptionOfType(ApiUnavailableException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withCauseInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("an interrupt is translated and the interrupt flag is restored")
        void restoresInterruptFlag() throws Exception {
            when(httpClient.<String>send(any(), any())).thenThrow(new InterruptedException("shutting down"));

            try {
                assertThatExceptionOfType(ApiUnavailableException.class)
                        .isThrownBy(() -> client.geocode("Berlin"));
                // Swallowing the interrupt would leave a pool thread unable to notice shutdown.
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted(); // clear it so later tests are unaffected
            }
        }

        @Test
        @DisplayName("HTTP 429 becomes RateLimitException")
        void translatesRateLimit() throws Exception {
            respondWith(429, "{\"reason\":\"Minutely API request limit exceeded\"}");

            assertThatExceptionOfType(RateLimitException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withMessageContaining("Too many requests");
        }

        @Test
        @DisplayName("RateLimitException is catchable as ApiUnavailableException")
        void rateLimitIsAnApiFailure() throws Exception {
            respondWith(429, "{}");

            assertThatThrownBy(() -> client.geocode("Berlin"))
                    .isInstanceOf(ApiUnavailableException.class);
        }

        @Test
        @DisplayName("HTTP 503 becomes ApiUnavailableException")
        void translatesServerError() throws Exception {
            respondWith(503, "Service Unavailable");

            assertThatExceptionOfType(ApiUnavailableException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withMessageContaining("503");
        }

        @Test
        @DisplayName("HTTP 400 means we built a bad request, so it is reported as malformed")
        void translatesBadRequest() throws Exception {
            respondWith(400, "{\"error\":true,\"reason\":\"Cannot initialize WeatherVariable\"}");

            assertThatExceptionOfType(MalformedResponseException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withMessageContaining("400");
        }

        @Test
        @DisplayName("a body that is not JSON becomes MalformedResponseException")
        void translatesUnparseableBody() throws Exception {
            respondWith(200, "<html><body>502 Bad Gateway</body></html>");

            assertThatExceptionOfType(MalformedResponseException.class)
                    .isThrownBy(() -> client.geocode("Berlin"))
                    .withMessageContaining("invalid JSON");
        }

        @Test
        @DisplayName("a field of the wrong type becomes MalformedResponseException")
        void translatesTypeMismatch() throws Exception {
            respondWith(200, "{\"results\":[{\"name\":\"Berlin\",\"latitude\":\"not-a-number\",\"longitude\":13.4}]}");

            assertThatExceptionOfType(MalformedResponseException.class)
                    .isThrownBy(() -> client.geocode("Berlin"));
        }

        @Test
        @DisplayName("unknown JSON fields are ignored rather than failing the parse")
        void toleratesUnknownFields() throws Exception {
            // FAIL_ON_UNKNOWN_PROPERTIES = false: the provider may add fields at any time.
            respondWith(200, "{\"results\":[{\"name\":\"Berlin\",\"latitude\":52.5,\"longitude\":13.4,"
                    + "\"something_new\":{\"nested\":true}}],\"generationtime_ms\":0.5}");

            assertThat(client.geocode("Berlin").name()).isEqualTo("Berlin");
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private void respondWith(int status, String body) throws Exception {
        when(httpResponse.statusCode()).thenReturn(status);
        if (status >= 200 && status < 300) {
            when(httpResponse.body()).thenReturn(body);
        }
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);
    }

    private HttpRequest capturedRequest() throws Exception {
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any());
        return captor.getValue();
    }

    private static Config testConfig() {
        Properties props = new Properties();
        props.setProperty("openmeteo.geocoding.url", "https://geo.test/search");
        props.setProperty("openmeteo.forecast.url", "https://forecast.test/forecast");
        props.setProperty("http.connect.timeout.seconds", "5");
        props.setProperty("http.request.timeout.seconds", "10");
        props.setProperty("cache.ttl.minutes", "10");
        props.setProperty("forecast.days", "5");
        props.setProperty("history.size", "5");
        return new Config(props);
    }

    private static String fixture(String resource) {
        try (InputStream in = OpenMeteoClientTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test fixture " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
