package io.retit.greensoftware.service;

/**
 * A month of a history.
 *
 * @param emissions {@link Emissions#NONE} without data
 */
public record HistoryPoint(String period, Emissions emissions) {
}
