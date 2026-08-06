/**
 * Jackson-mapped shapes of the Open-Meteo JSON responses.
 *
 * <p>These mirror the wire format field for field and are deliberately NOT the domain model: if the
 * provider renames a field or adds a nesting level, the change is absorbed here in the DTO plus the
 * mapping code, and {@link com.example.weather.model} stays untouched.
 */
package com.example.weather.api.dto;
