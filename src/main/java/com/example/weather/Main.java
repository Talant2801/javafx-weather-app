package com.example.weather;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX entry point. Builds the stage and nothing else — every piece of behaviour lives in the
 * controller and the layers behind it.
 */
public class Main extends Application {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static final String TITLE = "Weather";

    @Override
    public void start(Stage stage) {
        // Stage 1 placeholder: replaced by the FXML view in stage 4.
        Label label = new Label("Weather app — stage 1 skeleton");
        Scene scene = new Scene(new StackPane(label), 420, 240);

        stage.setTitle(TITLE);
        stage.setScene(scene);
        stage.show();
        log.info("{} started", TITLE);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
