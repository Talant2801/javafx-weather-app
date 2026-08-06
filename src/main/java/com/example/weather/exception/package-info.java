/**
 * The application's own exception vocabulary.
 *
 * <p>Low-level failures ({@code IOException}, {@code InterruptedException}, HTTP status codes,
 * Jackson parse errors) are caught at the client boundary and rethrown as these types, so upper
 * layers can decide what to tell the user without knowing how the data was fetched.
 */
package com.example.weather.exception;
