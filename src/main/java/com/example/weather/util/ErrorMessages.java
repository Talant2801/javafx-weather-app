package com.example.weather.util;

import com.example.weather.exception.ApiUnavailableException;
import com.example.weather.exception.CityNotFoundException;
import com.example.weather.exception.MalformedResponseException;
import com.example.weather.exception.RateLimitException;
import com.example.weather.exception.WeatherApiException;

import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;

/**
 * Turns an exception into a sentence a person can act on.
 *
 * <p>A pure function in its own class rather than a private method on the controller, for two
 * reasons: it can be unit tested without starting the JavaFX toolkit, and it keeps the wording of
 * every failure case in one place instead of scattered across catch blocks.
 *
 * <p>Nothing here ever exposes a stack trace or a class name — those go to the log, where they help
 * a developer, not to a label, where they only frighten a user.
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    /** The message to show on screen for a failure. Never null, never technical. */
    public static String userMessageFor(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return switch (cause) {
            case CityNotFoundException e ->
                    "We couldn't find \"" + e.cityName() + "\". Check the spelling and try again.";
            case RateLimitException ignored ->
                    "Too many searches in a row. Give it a minute, then try again.";
            case MalformedResponseException ignored ->
                    "The weather service sent something we couldn't read. Please try again shortly.";
            case ApiUnavailableException e when isTimeout(e) ->
                    "The weather service is taking too long to answer. Please try again.";
            case ApiUnavailableException ignored ->
                    "Can't reach the weather service. Check your internet connection and try again.";
            case WeatherApiException ignored ->
                    "Something went wrong fetching the weather. Please try again.";
            case null, default ->
                    "Something unexpected went wrong. Please try again.";
        };
    }

    /**
     * A timeout and an unreachable network both arrive as {@link ApiUnavailableException}; the
     * original cause is what tells them apart, and the two need different advice.
     */
    private static boolean isTimeout(ApiUnavailableException e) {
        return e.getCause() instanceof HttpTimeoutException;
    }

    /** {@link CompletionException} wraps whatever the background task threw; look through it. */
    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
