package com.example.weather.util;

import com.example.weather.exception.ApiUnavailableException;
import com.example.weather.exception.CityNotFoundException;
import com.example.weather.exception.MalformedResponseException;
import com.example.weather.exception.RateLimitException;
import com.example.weather.exception.WeatherApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorMessagesTest {

    @Test
    @DisplayName("an unknown city is quoted back so the user can see the typo")
    void quotesTheCityBack() {
        String message = ErrorMessages.userMessageFor(new CityNotFoundException("Berlim"));

        assertThat(message).contains("Berlim").contains("spelling");
    }

    @Test
    @DisplayName("a timeout and an unreachable network get different advice")
    void separatesTimeoutFromUnreachable() {
        String timeout = ErrorMessages.userMessageFor(
                new ApiUnavailableException("slow", new HttpTimeoutException("timed out")));
        String unreachable = ErrorMessages.userMessageFor(
                new ApiUnavailableException("down", new ConnectException("refused")));

        assertThat(timeout).contains("too long");
        assertThat(unreachable).contains("internet connection");
        assertThat(timeout).isNotEqualTo(unreachable);
    }

    @Test
    @DisplayName("rate limiting tells the user to wait rather than to check their connection")
    void rateLimitHasItsOwnAdvice() {
        String message = ErrorMessages.userMessageFor(new RateLimitException("429"));

        assertThat(message).contains("minute");
        assertThat(message).doesNotContain("connection");
    }

    @Test
    @DisplayName("a malformed response does not ask the user to fix anything")
    void malformedResponseIsNotBlamedOnTheUser() {
        String message = ErrorMessages.userMessageFor(new MalformedResponseException("bad json"));

        assertThat(message).contains("couldn't read");
    }

    @Test
    @DisplayName("the CompletionException wrapper from CompletableFuture is unwrapped")
    void unwrapsCompletionException() {
        Throwable wrapped = new CompletionException(new CityNotFoundException("Atlantis"));

        assertThat(ErrorMessages.userMessageFor(wrapped)).contains("Atlantis");
    }

    @Test
    @DisplayName("an unexpected exception still produces a friendly sentence")
    void fallsBackForUnknownFailures() {
        assertThat(ErrorMessages.userMessageFor(new IllegalStateException("boom")))
                .isEqualTo("Something unexpected went wrong. Please try again.");
    }

    @Test
    @DisplayName("a null throwable does not blow up the error handler")
    void handlesNull() {
        assertThat(ErrorMessages.userMessageFor(null)).isNotBlank();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyFailure")
    @DisplayName("no message ever leaks a class name, stack frame or HTTP status")
    void messagesStayHumanReadable(String label, Throwable throwable) {
        String message = ErrorMessages.userMessageFor(throwable);

        assertThat(message)
                .isNotBlank()
                .doesNotContain("Exception")
                .doesNotContain("java.")
                .doesNotContain("com.example")
                .doesNotContain("at ")
                .endsWith(".");
        assertThat(label).isNotBlank();
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> everyFailure() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "city not found", new CityNotFoundException("Nowhere")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "timeout", new ApiUnavailableException("slow", new HttpTimeoutException("t"))),
                org.junit.jupiter.params.provider.Arguments.of(
                        "network unreachable", new ApiUnavailableException("down", new IOException("io"))),
                org.junit.jupiter.params.provider.Arguments.of(
                        "rate limited", new RateLimitException("429")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "malformed response", new MalformedResponseException("bad")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "generic api failure", new WeatherApiException("odd")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "programming error", new NullPointerException("npe")));
    }
}
