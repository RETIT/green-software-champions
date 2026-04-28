package com.retit.greensoftware.service;

import com.retit.greensoftware.dto.AchievementDto;
import com.retit.greensoftware.dto.CarbonSummaryDto;
import com.retit.greensoftware.dto.LeaderboardEntryDto;
import com.retit.greensoftware.dto.TeamDto;
import com.retit.greensoftware.entity.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles leaderboard data by combining team, carbon entry, and achievement
 * records and computing rankings and relative metrics.
 */
@ApplicationScoped
public class LeaderboardService {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Returns the leaderboard for the given period. If {@code period} is null or
     * empty, the current calendar month is used.
     */
    @Transactional
    public List<LeaderboardEntryDto> getLeaderboard(String period) {
        String resolvedPeriod = (period != null && !period.isBlank())
                ? period
                : LocalDate.now().format(PERIOD_FMT);

        List<CarbonEntry> entries = CarbonEntry.findByPeriod(resolvedPeriod);
        double companyTotal = entries.stream()
                .filter(e -> e.totalCo2Kg != null)
                .mapToDouble(e -> e.totalCo2Kg)
                .sum();

        // Sort by total CO2 ascending (lower is better)
        entries.sort(Comparator.comparingDouble(e -> e.totalCo2Kg != null ? e.totalCo2Kg : Double.MAX_VALUE));

        List<LeaderboardEntryDto> result = new ArrayList<>();
        int rank = 1;
        for (CarbonEntry entry : entries) {
            LeaderboardEntryDto dto = new LeaderboardEntryDto();
            dto.teamId = entry.team.id;
            dto.teamName = entry.team.name;
            dto.teamColor = entry.team.color;
            dto.teamIcon = entry.team.icon;
            dto.rank = rank++;
            dto.period = resolvedPeriod;
            dto.totalCo2Kg = entry.totalCo2Kg;
            dto.previousCo2Kg = entry.previousCo2Kg;
            dto.reductionPercent = entry.reductionPercentage;
            dto.awsCo2Kg = entry.awsCo2Kg;
            dto.azureCo2Kg = entry.azureCo2Kg;
            dto.gcpCo2Kg = entry.gcpCo2Kg;
            dto.companySharePercent = (companyTotal > 0 && entry.totalCo2Kg != null)
                    ? (entry.totalCo2Kg / companyTotal) * 100.0
                    : null;
            dto.achievements = Achievement.findByTeam(entry.team)
                    .stream()
                    .map(this::toAchievementDto)
                    .collect(Collectors.toList());
            result.add(dto);
        }

        // Teams with no data for this period (still show them at the bottom)
        Set<Long> rankedIds = entries.stream().map(e -> e.team.id).collect(Collectors.toSet());
        List<Team> allTeams = Team.listAll();
        for (Team team : allTeams) {
            if (!rankedIds.contains(team.id)) {
                LeaderboardEntryDto dto = new LeaderboardEntryDto();
                dto.teamId = team.id;
                dto.teamName = team.name;
                dto.teamColor = team.color;
                dto.teamIcon = team.icon;
                dto.rank = rank++;
                dto.period = resolvedPeriod;
                dto.achievements = Achievement.findByTeam(team)
                        .stream()
                        .map(this::toAchievementDto)
                        .collect(Collectors.toList());
                result.add(dto);
            }
        }
        return result;
    }

    /** Returns a company-wide carbon summary for the given period. */
    @Transactional
    public CarbonSummaryDto getCompanySummary(String period) {
        String resolvedPeriod = (period != null && !period.isBlank())
                ? period
                : LocalDate.now().format(PERIOD_FMT);

        List<CarbonEntry> entries = CarbonEntry.findByPeriod(resolvedPeriod);
        double total = entries.stream().filter(e -> e.totalCo2Kg != null)
                .mapToDouble(e -> e.totalCo2Kg).sum();
        double prevTotal = entries.stream().filter(e -> e.previousCo2Kg != null)
                .mapToDouble(e -> e.previousCo2Kg).sum();

        CarbonSummaryDto dto = new CarbonSummaryDto();
        dto.period = resolvedPeriod;
        dto.totalCo2Kg = total;
        dto.previousTotalCo2Kg = prevTotal > 0 ? prevTotal : null;
        dto.reductionPercent = (prevTotal > 0)
                ? ((prevTotal - total) / prevTotal) * 100.0
                : null;
        dto.teamCount = (int) Team.count();
        return dto;
    }

    /** Returns a list of all teams with their cloud project assignments. */
    @Transactional
    public List<TeamDto> getAllTeams() {
        return Team.<Team>listAll().stream()
                .map(this::toTeamDto)
                .collect(Collectors.toList());
    }

    private TeamDto toTeamDto(Team team) {
        TeamDto dto = new TeamDto();
        dto.id = team.id;
        dto.name = team.name;
        dto.description = team.description;
        dto.color = team.color;
        dto.icon = team.icon;
        dto.projects = CloudProject.findByTeam(team).stream()
                .map(p -> {
                    TeamDto.CloudProjectDto pd = new TeamDto.CloudProjectDto();
                    pd.id = p.id;
                    pd.provider = p.provider.name();
                    pd.projectId = p.projectId;
                    pd.projectName = p.projectName;
                    return pd;
                })
                .collect(Collectors.toList());
        return dto;
    }

    private AchievementDto toAchievementDto(Achievement a) {
        AchievementDto dto = new AchievementDto();
        dto.id = a.id;
        dto.title = a.title;
        dto.description = a.description;
        dto.badgeIcon = a.badgeIcon;
        dto.earnedAt = a.earnedAt;
        dto.type = a.type != null ? a.type.name() : null;
        return dto;
    }
}
