package io.retit.greensoftware.emissions;

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

    public static String current() {
        return YearMonth.now().toString();
    }

    /** The latest complete month. */
    public static String previous() {
        return previous(current());
    }

    public static String previous(String period) {
        return toYearMonth(period).minusMonths(1).toString();
    }

    /** The {@code months} periods up to and including {@code endPeriod}, oldest first. */
    public static List<String> window(String endPeriod, int months) {
        YearMonth end = toYearMonth(endPeriod);
        List<String> periods = new ArrayList<>(months);
        for (int i = months - 1; i >= 0; i--) {
            periods.add(end.minusMonths(i).toString());
        }
        return periods;
    }

    public static int year(String period) {
        return toYearMonth(period).getYear();
    }

    public static int lengthInDays(String period) {
        return toYearMonth(period).lengthOfMonth();
    }

    public static YearMonth toYearMonth(String period) {
        return YearMonth.parse(period);
    }
}
