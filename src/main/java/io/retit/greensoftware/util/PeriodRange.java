package io.retit.greensoftware.util;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** A range of consecutive reporting periods, both ends inclusive, see {@link Periods}. */
public record PeriodRange(YearMonth first, YearMonth last) {

    public PeriodRange {
        if (first.isAfter(last)) {
            throw new IllegalArgumentException("Period range ends before it starts: " + first + " to " + last);
        }
    }

    public static PeriodRange of(YearMonth period) {
        return new PeriodRange(period, period);
    }

    /** The {@code months} periods up to and including {@code last}. */
    public static PeriodRange ending(YearMonth last, int months) {
        if (months < 1) {
            throw new IllegalArgumentException("A period range has at least one month, got " + months);
        }
        return new PeriodRange(last.minusMonths(months - 1L), last);
    }

    public int months() {
        return (int) first.until(last, ChronoUnit.MONTHS) + 1;
    }

    /** As YYYY-MM, oldest first. */
    public List<String> periods() {
        return Periods.window(last.toString(), months());
    }

    @Override
    public String toString() {
        return first + " to " + last;
    }
}
