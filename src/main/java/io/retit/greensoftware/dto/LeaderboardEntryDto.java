package io.retit.greensoftware.dto;

import java.util.List;

public class LeaderboardEntryDto {

    public Long teamId;
    public String teamName;
    public String teamColor;
    public String teamIcon;
    public int rank;

    public Double totalCo2Kg;

    /** 0–100 */
    public Double companySharePercent;

    /** Vs. the previous period, based on daily averages; positive = reduction. */
    public Double reductionPercent;

    public Double previousCo2Kg;

    public Double awsCo2Kg;
    public Double azureCo2Kg;
    public Double gcpCo2Kg;

    public List<AchievementDto> achievements;

    /** Periods up to and including {@link #period}, oldest first. */
    public List<CarbonHistoryPointDto> history;

    public String period;
}
