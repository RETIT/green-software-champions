package io.retit.greensoftware.dto;

import java.util.List;

/** Company-wide emissions in {@link #period}. */
public class CarbonSummaryDto {

    public String period;

    public Double totalCo2Kg;

    /** Only of the teams with data for {@link #period}, so the comparison is like for like. */
    public Double previousTotalCo2Kg;

    /** Vs. the previous period, based on daily averages. */
    public Double reductionPercent;

    public int teamCount;

    /** Periods up to and including {@link #period}, oldest first. */
    public List<CarbonHistoryPointDto> history;
}
