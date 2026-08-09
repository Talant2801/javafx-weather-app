package com.example.weather.controller;

import com.example.weather.exception.CityNotFoundException;
import com.example.weather.model.DailyForecast;
import com.example.weather.model.Location;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherData;
import com.example.weather.service.SearchHistory;
import com.example.weather.service.WeatherCache;
import com.example.weather.service.WeatherService;
import com.example.weather.util.Config;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Loads the real FXML with the real controller and drives it against a stubbed service.
 *
 * <p>Compilation cannot catch an {@code fx:id} that does not match a field, or an
 * {@code onAction} pointing at a method that no longer exists — those only fail when the loader
 * runs. This test runs it.
 *
 * <p>Needs a display, so it is skipped unless {@code DISPLAY} is set; a headless CI box would need
 * Monocle to run it.
 */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class WeatherViewSmokeTest {

    private static final Location BERLIN = new Location("Berlin", "Germany", "State of Berlin", 52.52, 13.41);

    @BeforeAll
    static void startToolkit() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertThat(started.await(20, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @Timeout(30)
    @DisplayName("the FXML loads and every fx:id is bound")
    void fxmlLoadsAndBinds() throws Exception {
        Parent root = onFxThread(() -> load(stubService()));

        assertThat(root.lookup(".search-field")).isNotNull();
        assertThat(root.lookup("#searchButton")).isNotNull();
        assertThat(root.lookup("#unitToggle")).isNotNull();
        assertThat(root.lookup("#forecastBox")).isNotNull();
    }

    @Test
    @Timeout(30)
    @DisplayName("a successful search fills the current card and the forecast strip")
    void searchRendersWeather() throws Exception {
        Parent root = onFxThread(() -> load(stubService()));

        clickSearch(root, "Berlin");

        assertThat(labelText(root, "#cityLabel")).isEqualTo("Berlin, State of Berlin, Germany");
        assertThat(labelText(root, "#temperatureLabel")).isEqualTo("20°C");
        assertThat(labelText(root, "#conditionLabel")).isEqualTo("Clear sky");
        assertThat(labelText(root, "#humidityLabel")).isEqualTo("55%");
        assertThat(labelText(root, "#windLabel")).isEqualTo("10 km/h");
        assertThat(((HBox) root.lookup("#forecastBox")).getChildren()).hasSize(5);
    }

    @Test
    @Timeout(30)
    @DisplayName("the unit toggle re-renders in Fahrenheit without a second network call")
    void unitToggleConverts() throws Exception {
        Parent root = onFxThread(() -> load(stubService()));
        clickSearch(root, "Berlin");

        // fire() on a ToggleButton flips the selection itself and then raises the action event —
        // exactly what a real click does, so do not pre-set the state or the two cancel out.
        onFxThreadAndWait(() -> ((javafx.scene.control.ToggleButton) root.lookup("#unitToggle")).fire());
        waitUntil(root, r -> labelText(r, "#temperatureLabel").endsWith("°F"));

        assertThat(labelText(root, "#temperatureLabel")).isEqualTo("68°F");
        assertThat(labelText(root, "#windLabel")).isEqualTo("6 mph");
    }

    @Test
    @Timeout(30)
    @DisplayName("a failed search shows a friendly message and no results")
    void failureShowsMessage() throws Exception {
        Parent root = onFxThread(() -> load(failingService()));

        clickSearch(root, "Atlantis");

        assertThat(labelText(root, "#errorLabel")).contains("Atlantis").contains("spelling");
        assertThat(root.lookup("#weatherPane").isVisible()).isFalse();
    }

    @Test
    @Timeout(30)
    @DisplayName("searching adds a clickable history chip")
    void searchPopulatesHistory() throws Exception {
        Parent root = onFxThread(() -> load(stubService()));

        clickSearch(root, "Berlin");

        HBox history = (HBox) root.lookup("#historyBox");
        assertThat(history.getChildren()).hasSize(1);
        assertThat(((Button) history.getChildren().getFirst()).getText()).isEqualTo("Berlin");
    }

    // --- harness -------------------------------------------------------------------------------

    private void clickSearch(Parent root, String city) throws Exception {
        onFxThreadAndWait(() -> {
            ((TextField) root.lookup("#searchField")).setText(city);
            ((Button) root.lookup("#searchButton")).fire();
        });
        // The search runs on a background thread and posts its result back with Platform.runLater,
        // so wait for the outcome to actually appear rather than guessing at a sleep.
        waitUntil(root, r -> r.lookup("#weatherPane").isVisible() || r.lookup("#errorLabel").isVisible());
    }

    /** Polls a condition on the FX thread until it holds or the deadline passes. */
    private void waitUntil(Parent root, java.util.function.Predicate<Parent> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> satisfied = new AtomicReference<>(false);
            onFxThreadAndWait(() -> satisfied.set(condition.test(root)));
            if (satisfied.get()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition was not met within 15s");
    }

    private static Parent load(WeatherService service) {
        FXMLLoader loader = new FXMLLoader(
                WeatherViewSmokeTest.class.getResource("/com/example/weather/view/weather-view.fxml"));
        loader.setControllerFactory(type -> new WeatherController(service, new SearchHistory(5)));
        try {
            Parent root = loader.load();
            new Scene(root); // some controls need a scene before lookup() works
            return root;
        } catch (Exception e) {
            throw new IllegalStateException("FXML failed to load", e);
        }
    }

    private static String labelText(Parent root, String selector) {
        return ((Label) root.lookup(selector)).getText();
    }

    private static Parent onFxThread(java.util.function.Supplier<Parent> supplier) throws Exception {
        AtomicReference<Parent> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(supplier.get());
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void onFxThreadAndWait(Runnable action) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
    }

    /** A service whose client always returns the same canonical metric snapshot. */
    private static WeatherService stubService() {
        return new WeatherService(new com.example.weather.api.WeatherClient() {
            @Override
            public Location geocode(String cityName) {
                return BERLIN;
            }

            @Override
            public WeatherData fetchWeather(Location location) {
                return snapshot();
            }
        }, new WeatherCache(Duration.ofMinutes(10)), testConfig());
    }

    private static WeatherService failingService() {
        return new WeatherService(new com.example.weather.api.WeatherClient() {
            @Override
            public Location geocode(String cityName) {
                throw new CityNotFoundException(cityName);
            }

            @Override
            public WeatherData fetchWeather(Location location) {
                throw new UnsupportedOperationException();
            }
        }, new WeatherCache(Duration.ofMinutes(10)), testConfig());
    }

    private static WeatherData snapshot() {
        List<DailyForecast> days = List.of(
                new DailyForecast(LocalDate.of(2026, 8, 13), 0, 25.0, 15.0),
                new DailyForecast(LocalDate.of(2026, 8, 14), 3, 26.0, 16.0),
                new DailyForecast(LocalDate.of(2026, 8, 15), 61, 22.0, 14.0),
                new DailyForecast(LocalDate.of(2026, 8, 16), 95, 21.0, 13.0),
                new DailyForecast(LocalDate.of(2026, 8, 17), 71, 19.0, 11.0));
        return new WeatherData(BERLIN, LocalDateTime.of(2026, 8, 13, 12, 0),
                20.0, 18.0, 55, 10.0, 0, days, Units.METRIC);
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
}
