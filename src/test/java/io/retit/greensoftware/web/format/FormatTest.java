package io.retit.greensoftware.web.format;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatTest {

    private final Locale defaultLocale = Locale.getDefault();

    /** The pages are always in English, whatever the server's locale. */
    @BeforeEach
    void setGermanLocale() {
        Locale.setDefault(Locale.GERMANY);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0,          0.0 kg
            0.04,       0.0 kg
            -0.04,      0.0 kg
            9.94,       9.9 kg
            9.95,       10 kg
            12.5,       13 kg
            -12.4,      -12 kg
            999.49,     999 kg
            999.5,      1.00 t
            1235,       1.24 t
            -1500,      -1.50 t
            1234567890, '1,234,567.89 t'
            """)
    void co2(double kg, String expected) {
        assertEquals(expected, Format.co2(kg));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0,      0.0%,   0.00%
            12.345, 12.3%,  12.35%
            100,    100.0%, 100.00%
            """)
    void percent(double percent, String expected, String expectedWithTwoDigits) {
        assertEquals(expected, Format.percent(percent));
        assertEquals(expectedWithTwoDigits, Format.percent(percent, 2));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            5,     ↓ 5.0%, POSITIVE
            0.05,  ↓ 0.1%, POSITIVE
            -12.3, ↑ 12.3%, NEGATIVE
            0,     0.0%,   NEUTRAL
            0.04,  0.0%,   NEUTRAL
            -0.04, 0.0%,   NEUTRAL
            """)
    void reduction(double percent, String expected, Trend expectedTrend) {
        assertEquals(expected, Format.reduction(percent));
        assertEquals(expectedTrend, Trend.of(percent));
    }

    @Test
    void missingValuesAreEmpty() {
        assertEquals("—", Format.co2(null));
        assertEquals("—", Format.co2(Double.NaN));
        assertEquals("—", Format.percent(null));
        assertEquals("—", Format.reduction(null));
        assertEquals(Trend.NEUTRAL, Trend.of(null));
    }

    @Test
    void period() {
        assertEquals("March 2026", Format.period("2026-03"));
        assertEquals("September 2026", Format.period("2026-09"));
        assertEquals("—", Format.period("2026-13"));
        assertEquals("—", Format.period(""));
    }
}
