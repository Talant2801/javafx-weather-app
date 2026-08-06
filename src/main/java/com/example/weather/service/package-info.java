/**
 * Application logic: orchestration, caching and unit conversion.
 *
 * <p>The service turns the two-step "geocode, then fetch forecast" dance into a single call, decides
 * when a cached snapshot is still fresh, and hands the controller domain objects in the unit system
 * the user asked for. It knows nothing about JavaFX and nothing about HTTP.
 */
package com.example.weather.service;
