package com.example.weather.exception;

/**
 * The provider could not be reached or did not answer usefully: connection refused, DNS failure,
 * timeout, or a 5xx response.
 *
 * <p>Deliberately covers both "no network" and "their server is broken" — from the user's side the
 * remedy is the same, and the distinction lives in the message and the log.
 */
public class ApiUnavailableException extends WeatherApiException {

    public ApiUnavailableException(String message) {
        super(message);
    }

    public ApiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
