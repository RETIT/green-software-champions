package io.retit.greensoftware.service;

/**
 * CDI event that the data of the {@link Dashboard} has changed. Within a transaction, it is
 * fired before the commit, so observers should wait for {@code AFTER_SUCCESS}.
 */
public record DashboardDataChanged() {
}
