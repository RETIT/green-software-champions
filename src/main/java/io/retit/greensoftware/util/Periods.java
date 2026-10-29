package io.retit.greensoftware.util;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for reporting periods in the format YYYY-MM (e.g., "2024-03"), which is the
 * ISO-8601 representation of {@link YearMonth}.
 */
public final class Periods {

    private Periods() {
    }

    /** The current calendar month. */
    public static String current() {
        return YearMonth.now().toString();
    }

    /** The calendar month before the current one, i.e. the latest complete month. */
    public static String previous() {
        return previous(current());
    }

    /** The calendar month before {@code period}. */
    public static String previous(String period) {
        return toYearMonth(period).minusMonths(1).toString();
    }

    /**
     * The {@code months} consecutive periods ending at (and including) {@code endPeriod},
     * ordered oldest first.
     */
    public static List<String> window(String endPeriod, int months) {
        YearMonth end = toYearMonth(endPeriod);
        List<String> periods = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            periods.add(end.minusMonths(i).toString());
        }
        return periods;
    }

    /** The number of days in {@code period}. */
    public static int lengthInDays(String period) {
        return toYearMonth(period).lengthOfMonth();
    }

    /** Parses {@code period} into a {@link YearMonth}. */
    public static YearMonth toYearMonth(String period) {
        return YearMonth.parse(period);
    }
}
