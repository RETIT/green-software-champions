package io.retit.greensoftware.web.format;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TeamColorTest {

    @ParameterizedTest
    @ValueSource(strings = {"#4CAF50", "#fff", "#ffff", "#11223344", "red", "rebeccapurple", "rgb(1, 2, 3)",
            "rgba(1,2,3,0.5)", "hsl(120deg 50% 50% / 20%)", "oklch(40.1% 0.123 21.57)"})
    void cssColorsAreKept(String color) {
        assertEquals(color, TeamColor.cssColor(color));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "#12", "#12345", "red; background: url(https://example.com)", "url(https://example.com)",
            "rgb(1,2,3);x:y", "var(--retit-green)", "\"><script>", "red}"})
    void othersAreReplaced(String color) {
        assertEquals("#999999", TeamColor.cssColor(color));
    }
}
