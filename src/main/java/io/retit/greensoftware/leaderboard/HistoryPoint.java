package io.retit.greensoftware.leaderboard;

import io.retit.greensoftware.emissions.Emissions;

/**
 * A month of a history.
 *
 * @param emissions {@link Emissions#NONE} without data
 */
public record HistoryPoint(String period, Emissions emissions) {
}
