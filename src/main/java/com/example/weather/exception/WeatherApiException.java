package com.example.weather.exception;

/**
 * Base type for every failure the weather layers raise.
 *
 * <p>Unchecked on purpose. These failures are handled in exactly one place — the controller, which
 * turns them into a message on screen — and making them checked would force {@code throws} clauses
 * through the {@code CompletableFuture} lambdas that carry the work off the UI thread, where
 * checked exceptions cannot be thrown at all.
 */
public class WeatherApiException extends RuntimeException {

    public WeatherApiException(String message) {
        super(message);
    }

    public WeatherApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
