package com.example.weather.exception;

/**
 * The provider answered 429: too many requests.
 *
 * <p>Extends {@link ApiUnavailableException} so a caller that only cares about "the API is not
 * usable right now" can catch the parent, while the UI can still catch this one to say something
 * more specific about slowing down.
 */
public class RateLimitException extends ApiUnavailableException {

    public RateLimitException(String message) {
        super(message);
    }
}
