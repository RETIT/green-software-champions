package io.retit.greensoftware.api;

import io.retit.greensoftware.leaderboard.Leaderboard;
import io.retit.greensoftware.leaderboard.RankedTeam;

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

    public static List<LeaderboardEntryDto> from(Leaderboard leaderboard) {
        return leaderboard.teams().stream()
                .map(team -> from(leaderboard.period(), team))
                .toList();
    }

    private static LeaderboardEntryDto from(String period, RankedTeam ranked) {
        LeaderboardEntryDto dto = new LeaderboardEntryDto();
        dto.teamId = ranked.team().id();
        dto.teamName = ranked.team().name();
        dto.teamColor = ranked.team().color();
        dto.teamIcon = ranked.team().icon();
        dto.rank = ranked.rank();
        dto.totalCo2Kg = ranked.emissions().totalCo2Kg();
        dto.companySharePercent = ranked.companySharePercent();
        dto.reductionPercent = ranked.reductionPercent();
        dto.previousCo2Kg = ranked.previousCo2Kg();
        dto.awsCo2Kg = ranked.emissions().awsCo2Kg();
        dto.azureCo2Kg = ranked.emissions().azureCo2Kg();
        dto.gcpCo2Kg = ranked.emissions().gcpCo2Kg();
        dto.achievements = ranked.achievements().stream().map(AchievementDto::from).toList();
        dto.history = ranked.history().stream().map(CarbonHistoryPointDto::from).toList();
        dto.period = period;
        return dto;
    }
}
