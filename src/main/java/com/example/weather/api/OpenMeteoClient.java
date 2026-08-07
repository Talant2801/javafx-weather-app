package com.example.weather.api;

import com.example.weather.api.dto.CurrentWeatherDto;
import com.example.weather.api.dto.DailyForecastDto;
import com.example.weather.api.dto.ForecastResponseDto;
import com.example.weather.api.dto.GeocodingResponseDto;
import com.example.weather.api.dto.GeocodingResultDto;
import com.example.weather.exception.ApiUnavailableException;
import com.example.weather.exception.CityNotFoundException;
import com.example.weather.exception.MalformedResponseException;
import com.example.weather.exception.RateLimitException;
import com.example.weather.model.DailyForecast;
import com.example.weather.model.Location;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import com.example.weather.util.Config;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Open-Meteo implementation of {@link WeatherClient}.
 *
 * <p>This class is the only place in the application that knows Open-Meteo exists. It owns the URL
 * shapes, the JSON parsing, and — importantly — the translation of every low-level failure into the
 * domain exception vocabulary, so nothing above it ever has to catch {@code IOException}.
 *
 * <p>Thread-safe: {@link HttpClient} and {@link ObjectMapper} are both safe to share once
 * configured, and this class holds no mutable state.
 */
public class OpenMeteoClient implements WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private static final String CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code";
    private static final String DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Config config;

    public OpenMeteoClient(Config config) {
        this(defaultHttpClient(config), config);
    }

    /**
     * Visible for testing: lets a test supply a mocked {@link HttpClient} and exercise the parsing,
     * mapping and error handling without touching the network.
     */
    public OpenMeteoClient(HttpClient httpClient, Config config) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.config = Objects.requireNonNull(config, "config");
        this.objectMapper = JsonMapper.builder()
                // The provider adds fields over time; unknown ones are not our problem.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(new JavaTimeModule())
                .build();
    }

    private static HttpClient defaultHttpClient(Config config) {
        return HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Location geocode(String cityName) {
        String trimmed = Objects.requireNonNull(cityName, "cityName").trim();
        if (trimmed.isEmpty()) {
            throw new CityNotFoundException(cityName);
        }

        URI uri = URI.create(config.geocodingUrl()
                + "?name=" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8)
                + "&count=1");

        GeocodingResponseDto dto = get(uri, GeocodingResponseDto.class);
        if (dto.results() == null || dto.results().isEmpty()) {
            // A city that does not exist is a normal user mistake, not an error condition:
            // logged at debug, surfaced as a friendly message.
            log.debug("Geocoding returned no results for '{}'", trimmed);
            throw new CityNotFoundException(trimmed);
        }
        return toLocation(dto.results().getFirst());
    }

    @Override
    public WeatherData fetchWeather(Location location) {
        Objects.requireNonNull(location, "location");

        URI uri = URI.create(config.forecastUrl()
                + "?latitude=" + location.latitude()
                + "&longitude=" + location.longitude()
                + "&current=" + CURRENT_FIELDS
                + "&daily=" + DAILY_FIELDS
                + "&timezone=auto");

        ForecastResponseDto dto = get(uri, ForecastResponseDto.class);
        return toWeatherData(location, dto);
    }

    /** Performs the request and deserialises the body, mapping every failure mode to a domain exception. */
    private <T> T get(URI uri, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();

        log.debug("GET {}", uri);
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ApiUnavailableException("The weather service took too long to respond", e);
        } catch (ConnectException | UnknownHostException e) {
            throw new ApiUnavailableException("Could not reach the weather service", e);
        } catch (IOException e) {
            throw new ApiUnavailableException("The connection to the weather service failed", e);
        } catch (InterruptedException e) {
            // Never swallow an interrupt: restore the flag so whoever owns this thread can still
            // see that a shutdown was requested.
            Thread.currentThread().interrupt();
            throw new ApiUnavailableException("The weather request was interrupted", e);
        }

        checkStatus(response, uri);

        try {
            return objectMapper.readValue(response.body(), responseType);
        } catch (JsonMappingException e) {
            throw new MalformedResponseException("The weather service returned an unexpected structure", e);
        } catch (IOException e) {
            throw new MalformedResponseException("The weather service returned invalid JSON", e);
        }
    }

    private void checkStatus(HttpResponse<String> response, URI uri) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        log.warn("Open-Meteo returned HTTP {} for {}", status, uri);
        if (status == 429) {
            throw new RateLimitException("Too many requests to the weather service");
        }
        if (status >= 500) {
            throw new ApiUnavailableException("The weather service is having problems (HTTP " + status + ")");
        }
        // 4xx other than 429 means we built a request the provider rejected — our bug, not theirs.
        throw new MalformedResponseException("The weather service rejected the request (HTTP " + status + ")");
    }

    // --- DTO -> domain -------------------------------------------------------------------------
    // Mapping lives here so the wire format never leaks upwards. Anything the domain does not need
    // (population, elevation, generation time, unit labels) is dropped at this line.

    private Location toLocation(GeocodingResultDto dto) {
        return new Location(dto.name(), dto.country(), dto.admin1(), dto.latitude(), dto.longitude());
    }

    private WeatherData toWeatherData(Location location, ForecastResponseDto dto) {
        CurrentWeatherDto current = dto.current();
        if (current == null) {
            throw new MalformedResponseException("The weather service returned no current conditions");
        }
        return new WeatherData(
                location,
                current.time(),
                current.temperature(),
                current.apparentTemperature(),
                current.relativeHumidity(),
                current.windSpeed(),
                current.weatherCode(),
                toDailyForecasts(dto.daily()),
                // Open-Meteo defaults to Celsius and km/h, which is exactly our canonical form.
                Units.METRIC);
    }

    /** Zips Open-Meteo's parallel daily arrays into a list of {@link DailyForecast}. */
    private List<DailyForecast> toDailyForecasts(DailyForecastDto daily) {
        if (daily == null || daily.time() == null) {
            return List.of();
        }
        int days = daily.time().size();
        if (sizeOf(daily.weatherCode()) != days
                || sizeOf(daily.temperatureMax()) != days
                || sizeOf(daily.temperatureMin()) != days) {
            // Parallel arrays only mean anything if they line up; a mismatch would silently pair
            // the wrong temperature with the wrong day.
            throw new MalformedResponseException("The daily forecast arrays have mismatched lengths");
        }

        List<DailyForecast> forecasts = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            forecasts.add(new DailyForecast(
                    daily.time().get(i),
                    daily.weatherCode().get(i),
                    daily.temperatureMax().get(i),
                    daily.temperatureMin().get(i)));
        }
        return forecasts;
    }

    private static int sizeOf(List<?> list) {
        return list == null ? -1 : list.size();
    }
}
