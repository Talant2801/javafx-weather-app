package com.example.weather;

/**
 * Entry point for the fat jar produced by {@code ./gradlew shadowJar}.
 *
 * <p>When the JavaFX runtime is on the classpath rather than the module path, the JVM refuses to
 * start a main class that extends {@code Application} ("JavaFX runtime components are missing").
 * Launching from a class that does <em>not</em> extend it side-steps that check. {@code ./gradlew
 * run} does not need this — the JavaFX Gradle plugin puts the modules on the module path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Main.main(args);
    }
}
