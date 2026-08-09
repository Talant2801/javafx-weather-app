package com.example.weather.controller;

import com.example.weather.model.DailyForecast;
import com.example.weather.model.Units;
import com.example.weather.model.WeatherCondition;
import com.example.weather.model.WeatherData;
import com.example.weather.service.SearchHistory;
import com.example.weather.service.WeatherService;
import com.example.weather.util.ErrorMessages;
import com.example.weather.util.WeatherCodeMapper;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Binds the view to {@link WeatherService}.
 *
 * <p>Everything here is either an event handler or a rendering step. There is no HTTP, no parsing
 * and no business rule in this file — the controller's entire job is to decide <em>when</em> to ask
 * the service for something and <em>where</em> to put the answer.
 */
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter OBSERVED_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final WeatherService weatherService;
    private final SearchHistory searchHistory;

    /**
     * Background workers for network calls.
     *
     * <p>Daemon threads: if the window is closed while a request is in flight, the JVM must still
     * be able to exit. Two threads is plenty — the UI only ever has one search outstanding.
     */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "weather-io");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Guards against a slow earlier search overwriting a fast later one.
     *
     * <p>Only ever touched on the JavaFX Application Thread, so a plain long is enough — no
     * synchronisation needed.
     */
    private long latestRequestId;

    private WeatherData currentSnapshot;

    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private ToggleButton unitToggle;
    @FXML private HBox historyBox;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label errorLabel;
    @FXML private VBox weatherPane;
    @FXML private Label placeholderLabel;
    @FXML private Label cityLabel;
    @FXML private Label observedAtLabel;
    @FXML private Label conditionIcon;
    @FXML private Label temperatureLabel;
    @FXML private Label conditionLabel;
    @FXML private Label feelsLikeLabel;
    @FXML private Label humidityLabel;
    @FXML private Label windLabel;
    @FXML private HBox forecastBox;

    public WeatherController(WeatherService weatherService, SearchHistory searchHistory) {
        this.weatherService = Objects.requireNonNull(weatherService, "weatherService");
        this.searchHistory = Objects.requireNonNull(searchHistory, "searchHistory");
    }

    @FXML
    private void initialize() {
        weatherPane.setVisible(false);
        weatherPane.setManaged(false);
        hideError();
        setLoading(false);
        // Enter in the search box does the same thing as the button.
        searchField.setOnAction(event -> onSearch());
        unitToggle.setText(Units.METRIC.temperatureSymbol());
    }

    @FXML
    private void onSearch() {
        search(searchField.getText());
    }

    @FXML
    private void onToggleUnits() {
        Units units = currentUnits();
        unitToggle.setText(units.temperatureSymbol());
        if (currentSnapshot == null) {
            return;
        }
        // Re-asking the service is deliberate: the city is already cached, so this is a local
        // conversion rather than a network call, and the conversion logic stays in one place.
        search(currentSnapshot.location().name());
    }

    /**
     * Runs a search off the JavaFX Application Thread.
     *
     * <p><b>Why this matters.</b> JavaFX renders and dispatches every event on a single thread. A
     * blocking HTTP call made on it freezes the whole window — no repaint, no typing, no closing —
     * for as long as the request takes, which with a 10-second timeout is a visibly hung app. So the
     * work happens on {@link #executor}, and because scene graph nodes may only be touched from the
     * Application Thread, the result is handed back through {@link Platform#runLater}. Both halves
     * of that rule are load-bearing: doing IO on the FX thread hangs the UI, and touching a Label
     * from a background thread throws {@code IllegalStateException} — or worse, silently corrupts
     * the scene graph.
     */
    private void search(String rawCity) {
        String city = rawCity == null ? "" : rawCity.trim();
        if (city.isEmpty()) {
            showErrorMessage("Type a city name to search.");
            return;
        }

        long requestId = ++latestRequestId;
        Units units = currentUnits();
        setLoading(true);
        hideError();

        CompletableFuture
                .supplyAsync(() -> weatherService.getWeather(city, units), executor)
                .whenComplete((data, throwable) -> Platform.runLater(() -> {
                    if (requestId != latestRequestId) {
                        // A newer search already started; this answer is stale, drop it.
                        log.debug("Discarding stale response for '{}'", city);
                        return;
                    }
                    setLoading(false);
                    if (throwable != null) {
                        handleFailure(city, throwable);
                    } else {
                        showWeather(data);
                    }
                }));
    }

    private void handleFailure(String city, Throwable throwable) {
        // The stack trace belongs in the log, where it helps; the user gets a sentence.
        log.warn("Search for '{}' failed", city, throwable);
        showErrorMessage(ErrorMessages.userMessageFor(throwable));
    }

    private void showWeather(WeatherData data) {
        currentSnapshot = data;
        searchHistory.add(data.location().name());
        renderHistory();

        Units units = data.units();
        WeatherCondition condition = WeatherCodeMapper.describe(data.weatherCode());

        cityLabel.setText(data.location().displayName());
        observedAtLabel.setText("As of " + OBSERVED_FORMAT.format(data.observedAt()));
        conditionIcon.setText(condition.icon());
        conditionLabel.setText(condition.description());
        temperatureLabel.setText(formatTemperature(data.temperature(), units));
        feelsLikeLabel.setText(formatTemperature(data.feelsLike(), units));
        humidityLabel.setText(data.humidity() + "%");
        windLabel.setText(String.format(Locale.ENGLISH, "%.0f %s", data.windSpeed(), units.windSpeedSymbol()));

        renderForecast(data);

        placeholderLabel.setVisible(false);
        placeholderLabel.setManaged(false);
        weatherPane.setVisible(true);
        weatherPane.setManaged(true);
    }

    private void renderForecast(WeatherData data) {
        forecastBox.getChildren().clear();
        for (DailyForecast day : data.dailyForecast()) {
            forecastBox.getChildren().add(forecastCard(day, data.units()));
        }
    }

    private VBox forecastCard(DailyForecast day, Units units) {
        Label name = new Label(DAY_FORMAT.format(day.date()));
        name.getStyleClass().add("forecast-day");

        Label date = new Label(DATE_FORMAT.format(day.date()));
        date.getStyleClass().add("forecast-date");

        Label icon = new Label(WeatherCodeMapper.iconFor(day.weatherCode()));
        icon.getStyleClass().add("forecast-icon");

        Label high = new Label(formatTemperature(day.maxTemperature(), units));
        high.getStyleClass().add("forecast-high");

        Label low = new Label(formatTemperature(day.minTemperature(), units));
        low.getStyleClass().add("forecast-low");

        VBox card = new VBox(name, date, icon, high, low);
        card.getStyleClass().add("forecast-card");
        card.setAlignment(Pos.CENTER);
        HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);
        return card;
    }

    private void renderHistory() {
        historyBox.getChildren().clear();
        for (String city : searchHistory.entries()) {
            Button button = new Button(city);
            button.getStyleClass().add("history-chip");
            button.setOnAction(event -> {
                searchField.setText(city);
                search(city);
            });
            historyBox.getChildren().add(button);
        }
        historyBox.setVisible(!searchHistory.isEmpty());
        historyBox.setManaged(!searchHistory.isEmpty());
    }

    private String formatTemperature(double value, Units units) {
        return String.format(Locale.ENGLISH, "%.0f%s", value, units.temperatureSymbol());
    }

    private Units currentUnits() {
        return unitToggle.isSelected() ? Units.IMPERIAL : Units.METRIC;
    }

    private void setLoading(boolean loading) {
        loadingIndicator.setVisible(loading);
        loadingIndicator.setManaged(loading);
        // Disabling the controls is what stops a user queueing five searches while one is running.
        searchButton.setDisable(loading);
        searchField.setDisable(loading);
        unitToggle.setDisable(loading);
    }

    private void showErrorMessage(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /** Stops the background threads. Called when the window closes. */
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                log.debug("Background workers did not stop within 2s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
