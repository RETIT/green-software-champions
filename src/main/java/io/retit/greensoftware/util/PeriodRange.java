package io.retit.greensoftware.util;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A range of consecutive reporting periods, see {@link Periods}.
 *
 * @param first The oldest period of the range (inclusive).
 * @param last  The latest period of the range (inclusive), not before {@code first}.
 */
public record PeriodRange(YearMonth first, YearMonth last) {

    public PeriodRange {
        if (first.isAfter(last)) {
            throw new IllegalArgumentException("Period range ends before it starts: " + first + " to " + last);
        }
    }

    /** The range consisting of {@code period} only. */
    public static PeriodRange of(YearMonth period) {
        return new PeriodRange(period, period);
    }

    /** The range of the {@code months} consecutive periods ending at (and including) {@code last}. */
    public static PeriodRange ending(YearMonth last, int months) {
        if (months < 1) {
            throw new IllegalArgumentException("A period range has at least one month, got " + months);
        }
        return new PeriodRange(last.minusMonths(months - 1L), last);
    }

    /** The number of periods in this range. */
    public int months() {
        return (int) first.until(last, ChronoUnit.MONTHS) + 1;
    }

    /** The periods of this range in the format YYYY-MM, ordered oldest first. */
    public List<String> periods() {
        return Periods.window(last.toString(), months());
    }

    @Override
    public String toString() {
        return first + " to " + last;
    }
}
