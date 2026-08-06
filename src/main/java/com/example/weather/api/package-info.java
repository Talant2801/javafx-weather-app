/**
 * The outbound HTTP boundary.
 *
 * <p>{@link com.example.weather.api.WeatherClient} is the only thing the rest of the app is allowed
 * to depend on; the concrete Open-Meteo implementation lives behind it so the provider can be
 * swapped or stubbed in tests. Everything provider-specific — URLs, query strings, JSON shapes,
 * {@code IOException} handling — stops at this layer.
 */
package com.example.weather.api;
