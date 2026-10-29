package io.retit.greensoftware.util;

import io.retit.greensoftware.entity.CarbonEntry;

/**
 * Helpers for comparing a period's emissions with those of the previous period.
 *
 * <p>Comparisons use the daily average of each period rather than its total, so that
 * months of different length are comparable: a 28-day February with the same daily
 * emissions as a 31-day January is no reduction.
 *
 * <p>Reductions are always derived when reading rather than stored, so they follow
 * every revision of the previous period's data.
 */
public final class Reductions {

    private Reductions() {
    }

    /**
     * Reduction from {@code previousCo2Kg} in the period before {@code period} to
     * {@code co2Kg} in {@code period} in percent, based on daily averages.
     * Positive = reduction (good), negative = increase (bad).
     * Null if either value is missing or there were no previous emissions to reduce.
     */
    public static Double fromPrevious(String period, Double previousCo2Kg, Double co2Kg) {
        return percent(perDay(previousCo2Kg, Periods.previous(period)), perDay(co2Kg, period));
    }

    /** {@link #fromPrevious} between two entries; {@code previous} may be null. */
    public static Double between(CarbonEntry previous, CarbonEntry entry) {
        if (previous == null) {
            return null;
        }
        return percent(perDay(previous.totalCo2Kg, previous.period), perDay(entry.totalCo2Kg, entry.period));
    }

    /** The daily average of {@code co2Kg} emitted in {@code period}, or null if it is missing. */
    private static Double perDay(Double co2Kg, String period) {
        return co2Kg != null ? co2Kg / Periods.lengthInDays(period) : null;
    }

    /** Reduction from {@code previous} to {@code current} in percent. */
    private static Double percent(Double previous, Double current) {
        if (previous == null || previous <= 0 || current == null) {
            return null;
        }
        return ((previous - current) / previous) * 100.0;
    }
}
