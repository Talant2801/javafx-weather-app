/**
 * The JavaFX controller layer: view state and event wiring only.
 *
 * <p>Its job is to translate user gestures into service calls, keep the work off the JavaFX
 * Application Thread, and push results back onto it. No HTTP, no parsing, no business rules — if a
 * method here starts computing something, it belongs in {@link com.example.weather.service}.
 */
package com.example.weather.controller;
