package com.example.weather.service;

import com.example.weather.api.WeatherClient;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The service is tested entirely against a mocked {@link WeatherClient} — which is the payoff for
 * making {@code WeatherClient} an interface. No HTTP, no JSON, no network in this file.
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private static final Location BERLIN = new Location("Berlin", "Germany", "State of Berlin", 52.52, 13.41);
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    @Mock
    private WeatherClient client;

    private MutableClock clock;
    private WeatherCache cache;
    private WeatherService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        cache = new WeatherCache(Duration.ofMinutes(10), clock);
        service = new WeatherService(client, cache, testConfig());
    }

    @Nested
    @DisplayName("orchestration")
    class Orchestration {

        @Test
        @DisplayName("geocodes first, then fetches the forecast for the resolved coordinates")
        void geocodesThenFetches() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(7));

            WeatherData result = service.getWeather("Berlin", Units.METRIC);

            InOrder order = inOrder(client);
            order.verify(client).geocode("Berlin");
            order.verify(client).fetchWeather(BERLIN);
            order.verifyNoMoreInteractions();
            assertThat(result.location()).isEqualTo(BERLIN);
        }

        @Test
        @DisplayName("trims the forecast to the configured number of days")
        void trimsForecastToConfiguredDays() {
            when(client.geocode(anyString())).thenReturn(BERLIN);
            when(client.fetchWeather(any())).thenReturn(metricSnapshot(7));

            WeatherData result = service.getWeather("Berlin", Units.METRIC);

            // The provider returns seven days; the strip shows five.
            assertThat(result.dailyForecast()).hasSize(5);
            assertThat(result.dailyForecast().getFirst().date()).isEqualTo(LocalDate.of(2026, 8, 13));
            assertThat(result.dailyForecast().getLast().date()).isEqualTo(LocalDate.of(2026, 8, 17));
        }

        @Test
        @DisplayName("a shorter forecast than configured is passed through untouched")
        void keepsShortForecasts() {
            when(client.geocode(anyString())).thenReturn(BERLIN);
            when(client.fetchWeather(any())).thenReturn(metricSnapshot(3));

            assertThat(service.getWeather("Berlin", Units.METRIC).dailyForecast()).hasSize(3);
        }

        @Test
        @DisplayName("blank input never reaches the client")
        void rejectsBlankCity() {
            assertThatExceptionOfType(CityNotFoundException.class)
                    .isThrownBy(() -> service.getWeather("   ", Units.METRIC));

            verifyNoInteractions(client);
        }
    }

    @Nested
    @DisplayName("caching")
    class Caching {

        @Test
        @DisplayName("a second search inside the TTL is served from cache without touching the client")
        void secondCallHitsCache() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(5));

            service.getWeather("Berlin", Units.METRIC);
            clock.advance(Duration.ofMinutes(9));
            WeatherData second = service.getWeather("Berlin", Units.METRIC);

            verify(client, times(1)).geocode(anyString());
            verify(client, times(1)).fetchWeather(any());
            assertThat(second.temperature()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("once the TTL passes the service goes back to the provider")
        void refetchesAfterTtl() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(5));

            service.getWeather("Berlin", Units.METRIC);
            clock.advance(Duration.ofMinutes(10).plusSeconds(1));
            service.getWeather("Berlin", Units.METRIC);

            verify(client, times(2)).fetchWeather(BERLIN);
        }

        @Test
        @DisplayName("cache lookup ignores case and surrounding whitespace")
        void cacheKeyIsNormalised() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(5));

            service.getWeather("Berlin", Units.METRIC);
            service.getWeather("  berlin ", Units.METRIC);

            verify(client, times(1)).fetchWeather(any());
        }

        @Test
        @DisplayName("a failed lookup is not cached")
        void failuresAreNotCached() {
            when(client.geocode("Berlin")).thenThrow(new ApiUnavailableException("down"));

            assertThatExceptionOfType(ApiUnavailableException.class)
                    .isThrownBy(() -> service.getWeather("Berlin", Units.METRIC));

            assertThat(cache.size()).isZero();
        }

        @Test
        @DisplayName("refresh drops the entry so the next call goes to the network")
        void refreshInvalidates() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(5));

            service.getWeather("Berlin", Units.METRIC);
            service.refresh("Berlin");
            service.getWeather("Berlin", Units.METRIC);

            verify(client, times(2)).fetchWeather(BERLIN);
        }

        @Test
        @DisplayName("the cache holds canonical metric data even when the caller asked for imperial")
        void cacheStoresCanonicalUnits() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(5));

            service.getWeather("Berlin", Units.IMPERIAL);

            // Storing the converted copy would mean a second entry per unit system, and would let
            // a Fahrenheit reading be handed to a Celsius view.
            assertThat(cache.get("Berlin")).hasValueSatisfying(cached -> {
                assertThat(cached.units()).isEqualTo(Units.METRIC);
                assertThat(cached.temperature()).isEqualTo(20.0);
            });
        }
    }

    @Nested
    @DisplayName("unit conversion")
    class UnitConversion {

        @Test
        @DisplayName("metric passes the provider's values through untouched")
        void metricIsIdentity() {
            when(client.geocode(anyString())).thenReturn(BERLIN);
            when(client.fetchWeather(any())).thenReturn(metricSnapshot(5));

            WeatherData result = service.getWeather("Berlin", Units.METRIC);

            assertThat(result.units()).isEqualTo(Units.METRIC);
            assertThat(result.temperature()).isEqualTo(20.0);
            assertThat(result.windSpeed()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("imperial converts temperature, feels-like and wind")
        void convertsToImperial() {
            when(client.geocode(anyString())).thenReturn(BERLIN);
            when(client.fetchWeather(any())).thenReturn(metricSnapshot(5));

            WeatherData result = service.getWeather("Berlin", Units.IMPERIAL);

            assertThat(result.units()).isEqualTo(Units.IMPERIAL);
            assertThat(result.temperature()).isEqualTo(68.0);                       // 20 C
            assertThat(result.feelsLike()).isEqualTo(64.4, within(0.001));          // 18 C
            assertThat(result.windSpeed()).isEqualTo(6.214, within(0.001));         // 10 km/h
            assertThat(result.humidity()).isEqualTo(55);                            // unitless
        }

        @Test
        @DisplayName("the forecast strip is converted along with the current reading")
        void convertsForecastDays() {
            when(client.geocode(anyString())).thenReturn(BERLIN);
            when(client.fetchWeather(any())).thenReturn(metricSnapshot(5));

            WeatherData result = service.getWeather("Berlin", Units.IMPERIAL);

            DailyForecast firstDay = result.dailyForecast().getFirst();
            assertThat(firstDay.maxTemperature()).isEqualTo(77.0);   // 25 C
            assertThat(firstDay.minTemperature()).isEqualTo(59.0);   // 15 C
        }

        @Test
        @DisplayName("switching units on a cached city converts from canonical data, not twice")
        void repeatedConversionDoesNotCompound() {
            when(client.geocode(anyString())).thenReturn(BERLIN);
            when(client.fetchWeather(any())).thenReturn(metricSnapshot(5));

            service.getWeather("Berlin", Units.IMPERIAL);
            WeatherData again = service.getWeather("Berlin", Units.IMPERIAL);

            // 68 F, not 154 F: the cached value never left Celsius.
            assertThat(again.temperature()).isEqualTo(68.0);
        }
    }

    @Nested
    @DisplayName("error paths")
    class ErrorPaths {

        @Test
        @DisplayName("an unknown city propagates as CityNotFoundException")
        void propagatesCityNotFound() {
            when(client.geocode("Atlantis")).thenThrow(new CityNotFoundException("Atlantis"));

            assertThatExceptionOfType(CityNotFoundException.class)
                    .isThrownBy(() -> service.getWeather("Atlantis", Units.METRIC))
                    .withMessageContaining("Atlantis");

            verify(client, never()).fetchWeather(any());
        }

        @Test
        @DisplayName("a timeout during the forecast call propagates as ApiUnavailableException")
        void propagatesTimeout() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN))
                    .thenThrow(new ApiUnavailableException("The weather service took too long to respond"));

            assertThatExceptionOfType(ApiUnavailableException.class)
                    .isThrownBy(() -> service.getWeather("Berlin", Units.METRIC))
                    .withMessageContaining("too long");
        }

        @Test
        @DisplayName("a malformed response propagates unchanged")
        void propagatesMalformedResponse() {
            when(client.geocode("Berlin")).thenReturn(BERLIN);
            when(client.fetchWeather(BERLIN)).thenThrow(new MalformedResponseException("bad JSON"));

            assertThatExceptionOfType(MalformedResponseException.class)
                    .isThrownBy(() -> service.getWeather("Berlin", Units.METRIC));
        }

        @Test
        @DisplayName("a rate limit propagates and stays distinguishable from a generic outage")
        void propagatesRateLimit() {
            when(client.geocode("Berlin")).thenThrow(new RateLimitException("Too many requests"));

            assertThatExceptionOfType(RateLimitException.class)
                    .isThrownBy(() -> service.getWeather("Berlin", Units.METRIC));
        }

        @Test
        @DisplayName("a cached city keeps working after the provider goes down")
        void cacheShieldsUserFromLaterOutage() {
            // First call succeeds, every later call would fail — but within the TTL none is made,
            // so the user keeps seeing data instead of an error.
            when(client.geocode("Berlin")).thenReturn(BERLIN).thenThrow(new ApiUnavailableException("down"));
            when(client.fetchWeather(BERLIN)).thenReturn(metricSnapshot(5));
            service.getWeather("Berlin", Units.METRIC);

            WeatherData duringOutage = service.getWeather("Berlin", Units.METRIC);

            assertThat(duringOutage.temperature()).isEqualTo(20.0);
            verify(client, times(1)).geocode("Berlin");
            verify(client, times(1)).fetchWeather(BERLIN);
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    /** A canonical metric snapshot: 20 C, feels like 18 C, 55 %, 10 km/h, clear sky. */
    private static WeatherData metricSnapshot(int days) {
        List<DailyForecast> forecast = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            forecast.add(new DailyForecast(LocalDate.of(2026, 8, 13).plusDays(i), 0, 25.0, 15.0));
        }
        return new WeatherData(
                BERLIN,
                LocalDateTime.of(2026, 8, 13, 12, 0),
                20.0,
                18.0,
                55,
                10.0,
                0,
                forecast,
                Units.METRIC);
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

    /** A clock the test moves by hand, so TTL expiry needs no sleeping. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
