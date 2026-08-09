package com.example.weather;

import com.example.weather.api.OpenMeteoClient;
import com.example.weather.api.WeatherClient;
import com.example.weather.controller.WeatherController;
import com.example.weather.service.SearchHistory;
import com.example.weather.service.WeatherCache;
import com.example.weather.service.WeatherService;
import com.example.weather.util.Config;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Objects;

/**
 * JavaFX entry point and composition root.
 *
 * <p>This is the one place that knows which implementations are in play: it builds the Open-Meteo
 * client, wraps it in the service, and hands the result to the controller. Every other class asks
 * for what it needs through a constructor and stays ignorant of how it was made — which is exactly
 * what lets the tests substitute a mock client without a framework.
 */
public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String VIEW = "/com/example/weather/view/weather-view.fxml";
    private static final String STYLESHEET = "/com/example/weather/view/styles.css";
    private static final String TITLE = "Weather";

    private WeatherController controller;

    @Override
    public void start(Stage stage) {
        Config config = Config.load();

        // Wiring, top to bottom. Swapping providers means changing this one line.
        WeatherClient client = new OpenMeteoClient(config);
        WeatherService service = new WeatherService(client, new WeatherCache(config.cacheTtl()), config);
        SearchHistory history = new SearchHistory(config.historySize());

        FXMLLoader loader = new FXMLLoader(resource(VIEW));
        // The controller has a constructor with dependencies, so FXMLLoader cannot instantiate it
        // by itself; the factory supplies the wired instance for the fx:controller class.
        loader.setControllerFactory(type -> new WeatherController(service, history));

        Scene scene;
        try {
            scene = new Scene(loader.load());
        } catch (IOException e) {
            // A missing or broken FXML is a packaging error: there is no sensible degraded mode.
            throw new UncheckedIOException("Could not load " + VIEW, e);
        }
        scene.getStylesheets().add(resource(STYLESHEET).toExternalForm());
        controller = loader.getController();

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.setMinWidth(680);
        stage.setMinHeight(560);
        stage.show();
        log.info("{} started", TITLE);
    }

    @Override
    public void stop() {
        // Let the background workers go before the JVM does.
        if (controller != null) {
            controller.shutdown();
        }
    }

    private static URL resource(String path) {
        return Objects.requireNonNull(Main.class.getResource(path), "Missing classpath resource " + path);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
