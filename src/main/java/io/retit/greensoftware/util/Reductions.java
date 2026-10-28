package io.retit.greensoftware.util;

import io.retit.greensoftware.entity.CarbonEntry;

/**
 * Helpers for comparing a period's emissions with those of the previous period.
 *
 * <p>Reductions are always derived when reading rather than stored, so they follow
 * every revision of the previous period's data.
 */
public final class Reductions {

    private Reductions() {
    }

    /**
     * Reduction from {@code previousCo2Kg} to {@code co2Kg} in percent.
     * Positive = reduction (good), negative = increase (bad).
     * Null if either value is missing or there were no previous emissions to reduce.
     */
    public static Double percent(Double previousCo2Kg, Double co2Kg) {
        if (previousCo2Kg == null || previousCo2Kg <= 0 || co2Kg == null) {
            return null;
        }
        return ((previousCo2Kg - co2Kg) / previousCo2Kg) * 100.0;
    }

    /** {@link #percent(Double, Double)} between two entries; {@code previous} may be null. */
    public static Double between(CarbonEntry previous, CarbonEntry entry) {
        return percent(previous != null ? previous.totalCo2Kg : null, entry.totalCo2Kg);
    }
}
