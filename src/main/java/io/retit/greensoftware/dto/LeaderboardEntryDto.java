package io.retit.greensoftware.dto;

import java.util.List;

public class LeaderboardEntryDto {

    public Long teamId;
    public String teamName;
    public String teamColor;
    public String teamIcon;
    public int rank;

    /** Total CO2 in kg for the current period. */
    public Double totalCo2Kg;

    /** This team's share of the company's total cloud carbon footprint (0–100). */
    public Double companySharePercent;

    /**
     * CO2 reduction percentage vs. the previous period, based on daily averages so that
     * months of different length are comparable. Positive = improvement.
     */
    public Double reductionPercent;

    /** CO2 from previous period for reference. */
    public Double previousCo2Kg;

    /** Breakdown by cloud provider. */
    public Double awsCo2Kg;
    public Double azureCo2Kg;
    public Double gcpCo2Kg;

    public List<AchievementDto> achievements;

    /** This team's emissions for the periods up to and including {@link #period}, oldest first. */
    public List<CarbonHistoryPointDto> history;

    public String period;
}
