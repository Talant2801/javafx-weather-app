package com.example.weather.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The last N cities the user searched, most recent first.
 *
 * <p>Lives in the service layer rather than the controller because it is state with rules —
 * bounded size, de-duplication, ordering — and rules belong somewhere unit tests can reach without
 * starting a JavaFX toolkit. The controller only renders what this returns.
 *
 * <p>Re-searching a city moves it to the front rather than adding a duplicate, matching how
 * recently-used lists behave everywhere else.
 *
 * <p>Not thread-safe: it is touched only from the JavaFX Application Thread.
 */
public class SearchHistory {

    private final Deque<String> entries = new ArrayDeque<>();
    private final int maxSize;

    public SearchHistory(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("History size must be at least 1, was " + maxSize);
        }
        this.maxSize = maxSize;
    }

    /** Records a search, moving an existing entry to the front and evicting the oldest if full. */
    public void add(String cityName) {
        String value = Objects.requireNonNull(cityName, "cityName").trim();
        if (value.isEmpty()) {
            return;
        }
        entries.removeIf(existing -> existing.equalsIgnoreCase(value));
        entries.addFirst(value);
        while (entries.size() > maxSize) {
            entries.removeLast();
        }
    }

    /** The remembered cities, most recent first. Immutable snapshot. */
    public List<String> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    /** Normalised form used for display consistency in tests and logs. */
    static String normalise(String cityName) {
        return cityName.trim().toLowerCase(Locale.ROOT);
    }
}
