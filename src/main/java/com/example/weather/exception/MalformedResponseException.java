package com.example.weather.exception;

/**
 * The provider answered, but the body was not what the contract promises: unparseable JSON, a
 * missing block, or parallel arrays of mismatched length.
 *
 * <p>Separate from {@link ApiUnavailableException} because retrying will not help — this one means
 * our DTOs and their API have drifted apart, which is a bug to fix rather than a blip to wait out.
 */
public class MalformedResponseException extends WeatherApiException {

    public MalformedResponseException(String message) {
        super(message);
    }

    public MalformedResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
