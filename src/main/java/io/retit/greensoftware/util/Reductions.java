package io.retit.greensoftware.util;

import io.retit.greensoftware.entity.CarbonEntry;

/**
 * Reductions between periods, based on daily averages rather than totals, so that months
 * of different length are comparable: a 28-day February with the same daily emissions as
 * a 31-day January is no reduction.
 *
 * <p>Reductions are derived when reading rather than stored, so they follow every
 * revision of the previous period's data.
 */
public final class Reductions {

    private Reductions() {
    }

    /** {@link #percent} from {@code previousCo2Kg} in the period before {@code period} to {@code co2Kg}. */
    public static Double fromPrevious(String period, Double previousCo2Kg, Double co2Kg) {
        return percent(perDay(previousCo2Kg, Periods.previous(period)), perDay(co2Kg, period));
    }

    /** Null if {@code entry} or its total is missing. */
    public static Double perDay(CarbonEntry entry) {
        return entry != null ? perDay(entry.totalCo2Kg, entry.period) : null;
    }

    private static Double perDay(Double co2Kg, String period) {
        return co2Kg != null ? co2Kg / Periods.lengthInDays(period) : null;
    }

    /**
     * Reduction from {@code previous} to {@code current} in percent: positive = reduction,
     * negative = increase. Null if either is missing or {@code previous} is not positive.
     */
    public static Double percent(Double previous, Double current) {
        if (previous == null || previous <= 0 || current == null) {
            return null;
        }
        return ((previous - current) / previous) * 100.0;
    }
}
