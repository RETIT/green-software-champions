package io.retit.greensoftware.dto;

import java.util.List;

/**
 * Aggregated carbon summary for the entire company in a given period.
 */
public class CarbonSummaryDto {

    public String period;

    /** Total company-wide CO2 in kg for this period. */
    public Double totalCo2Kg;

    /** Total company-wide CO2 in kg from the previous period. */
    public Double previousTotalCo2Kg;

    /** Company-wide reduction percentage vs. previous period. */
    public Double reductionPercent;

    public int teamCount;

    /** Company-wide emissions for the periods up to and including {@link #period}, oldest first. */
    public List<CarbonHistoryPointDto> history;
}
