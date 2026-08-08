package com.example.weather.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SearchHistoryTest {

    @Test
    @DisplayName("the most recent search comes first")
    void mostRecentFirst() {
        SearchHistory history = new SearchHistory(5);

        history.add("Berlin");
        history.add("Paris");
        history.add("Tokyo");

        assertThat(history.entries()).containsExactly("Tokyo", "Paris", "Berlin");
    }

    @Test
    @DisplayName("only the last N cities are kept")
    void evictsOldestBeyondLimit() {
        SearchHistory history = new SearchHistory(5);

        for (String city : new String[]{"Berlin", "Paris", "Tokyo", "Lima", "Oslo", "Cairo"}) {
            history.add(city);
        }

        assertThat(history.entries()).containsExactly("Cairo", "Oslo", "Lima", "Tokyo", "Paris");
        assertThat(history.entries()).doesNotContain("Berlin");
    }

    @Test
    @DisplayName("re-searching a city moves it to the front instead of duplicating it")
    void repeatSearchMovesToFront() {
        SearchHistory history = new SearchHistory(5);
        history.add("Berlin");
        history.add("Paris");

        history.add("Berlin");

        assertThat(history.entries()).containsExactly("Berlin", "Paris");
    }

    @Test
    @DisplayName("de-duplication ignores case and whitespace but keeps what the user typed")
    void deduplicationIsCaseInsensitive() {
        SearchHistory history = new SearchHistory(5);
        history.add("Berlin");

        history.add("  berlin ");

        assertThat(history.entries()).containsExactly("berlin");
    }

    @Test
    @DisplayName("blank input is ignored")
    void ignoresBlankInput() {
        SearchHistory history = new SearchHistory(5);

        history.add("   ");

        assertThat(history.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("the returned list is an immutable snapshot")
    void entriesAreImmutable() {
        SearchHistory history = new SearchHistory(5);
        history.add("Berlin");

        var snapshot = history.entries();
        history.add("Paris");

        assertThat(snapshot).containsExactly("Berlin");
    }

    @Test
    @DisplayName("clear empties the history")
    void clearEmpties() {
        SearchHistory history = new SearchHistory(5);
        history.add("Berlin");

        history.clear();

        assertThat(history.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("a history that cannot hold anything is a configuration mistake")
    void rejectsNonPositiveSize() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SearchHistory(0));
    }
}
