package io.retit.greensoftware.emissions;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PeriodRangeTest {

    @Test
    void ending_coversTheGivenNumberOfMonthsAcrossAYearBoundary() {
        PeriodRange range = PeriodRange.ending(YearMonth.of(2025, 2), 3);

        assertEquals(new PeriodRange(YearMonth.of(2024, 12), YearMonth.of(2025, 2)), range);
        assertEquals(3, range.months());
        assertEquals(List.of("2024-12", "2025-01", "2025-02"), range.periods());
    }

    @Test
    void of_coversOnlyThatPeriod() {
        assertEquals(List.of("2025-02"), PeriodRange.of(YearMonth.of(2025, 2)).periods());
    }

    @Test
    void rejectsARangeEndingBeforeItStarts() {
        assertThrows(IllegalArgumentException.class,
                () -> new PeriodRange(YearMonth.of(2025, 2), YearMonth.of(2025, 1)));
        assertThrows(IllegalArgumentException.class, () -> PeriodRange.ending(YearMonth.of(2025, 2), 0));
    }
}
