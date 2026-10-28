package io.retit.greensoftware.dto;

/**
 * CO2 emissions of a single period within a history window.
 * All values are null when no data exists for that period.
 */
public class CarbonHistoryPointDto {

    /** Reporting period in the format YYYY-MM. */
    public String period;

    /** Total CO2 in kg for this period. */
    public Double totalCo2Kg;

    /** Breakdown by cloud provider. */
    public Double awsCo2Kg;
    public Double azureCo2Kg;
    public Double gcpCo2Kg;
}
