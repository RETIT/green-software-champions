package io.retit.greensoftware.service;

import io.retit.greensoftware.config.CarbonHistoryConfig;
import io.retit.greensoftware.dto.AchievementDto;
import io.retit.greensoftware.dto.CarbonHistoryPointDto;
import io.retit.greensoftware.dto.CarbonSummaryDto;
import io.retit.greensoftware.dto.LeaderboardEntryDto;
import io.retit.greensoftware.dto.TeamDto;
import io.retit.greensoftware.entity.*;
import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.util.Reductions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class LeaderboardService {

    @Inject
    CarbonHistoryConfig historyConfig;

    @Transactional
    public List<LeaderboardEntryDto> getLeaderboard(String period) {
        String resolvedPeriod = resolvePeriod(period);

        List<CarbonEntry> entries = CarbonEntry.findByPeriod(resolvedPeriod);
        Map<Long, CarbonEntry> previousByTeam = CarbonEntry.findByPeriodByTeamId(Periods.previous(resolvedPeriod));
        List<String> window = historyWindow(resolvedPeriod);
        Map<Long, Map<String, CarbonEntry>> historyByTeam = CarbonEntry.findByPeriods(window)
                .stream()
                .collect(Collectors.groupingBy(e -> e.team.id,
                        Collectors.toMap(e -> e.period, e -> e)));
        Map<Long, List<AchievementDto>> achievementsByTeam = findAchievementsByTeam(resolvedPeriod);
        double companyTotal = entries.stream()
                .filter(e -> e.totalCo2Kg != null)
                .mapToDouble(e -> e.totalCo2Kg)
                .sum();

        // Lowest emissions rank first
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
            CarbonEntry previous = previousByTeam.get(entry.team.id);
            dto.previousCo2Kg = previous != null ? previous.totalCo2Kg : null;
            dto.reductionPercent = Reductions.between(previous, entry);
            dto.awsCo2Kg = entry.awsCo2Kg;
            dto.azureCo2Kg = entry.azureCo2Kg;
            dto.gcpCo2Kg = entry.gcpCo2Kg;
            dto.companySharePercent = (companyTotal > 0 && entry.totalCo2Kg != null)
                    ? (entry.totalCo2Kg / companyTotal) * 100.0
                    : null;
            dto.achievements = achievementsByTeam.getOrDefault(entry.team.id, List.of());
            dto.history = toTeamHistory(window, historyByTeam.getOrDefault(entry.team.id, Map.of()));
            result.add(dto);
        }

        // Teams without data for this period, at the bottom
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
                dto.achievements = achievementsByTeam.getOrDefault(team.id, List.of());
                dto.history = toTeamHistory(window, historyByTeam.getOrDefault(team.id, Map.of()));
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional
    public CarbonSummaryDto getCompanySummary(String period) {
        String resolvedPeriod = resolvePeriod(period);

        List<CarbonEntry> entries = CarbonEntry.findByPeriod(resolvedPeriod);
        Map<Long, CarbonEntry> previousByTeam = CarbonEntry.findByPeriodByTeamId(Periods.previous(resolvedPeriod));
        double total = entries.stream().filter(e -> e.totalCo2Kg != null)
                .mapToDouble(e -> e.totalCo2Kg).sum();
        // Only the teams with data for this period, so the comparison is like for like
        double prevTotal = entries.stream()
                .map(e -> previousByTeam.get(e.team.id))
                .filter(p -> p != null && p.totalCo2Kg != null)
                .mapToDouble(p -> p.totalCo2Kg).sum();

        CarbonSummaryDto dto = new CarbonSummaryDto();
        dto.period = resolvedPeriod;
        dto.totalCo2Kg = total;
        dto.previousTotalCo2Kg = prevTotal > 0 ? prevTotal : null;
        dto.reductionPercent = Reductions.fromPrevious(resolvedPeriod, dto.previousTotalCo2Kg, total);
        dto.teamCount = (int) Team.count();

        List<String> window = historyWindow(resolvedPeriod);
        Map<String, List<CarbonEntry>> entriesByPeriod = CarbonEntry.findByPeriods(window)
                .stream()
                .collect(Collectors.groupingBy(e -> e.period));
        dto.history = window.stream()
                .map(p -> toCompanyHistoryPoint(p, entriesByPeriod.getOrDefault(p, List.of())))
                .collect(Collectors.toList());
        return dto;
    }

    @Transactional
    public List<TeamDto> getAllTeams() {
        return Team.<Team>listAll().stream()
                .map(this::toTeamDto)
                .collect(Collectors.toList());
    }

    /**
     * {@code period}, or if blank, the latest period of the history window every team has
     * data for, as the providers publish a month only weeks after it ended. Falls back to
     * the previous month.
     */
    private String resolvePeriod(String period) {
        if (period != null && !period.isBlank()) {
            return period;
        }
        return CarbonEntry.findCompletePeriods(Periods.window(Periods.previous(), historyConfig.months()))
                .stream()
                .findFirst()
                .orElseGet(Periods::previous);
    }

    /**
     * The periods of the history charts, oldest first: up to {@code carbon.history.months}
     * ending with {@code endPeriod}, but none before the earliest synced period. The same
     * for all teams, so their charts line up.
     */
    private List<String> historyWindow(String endPeriod) {
        return CarbonEntry.findEarliestPeriod()
                .map(earliest -> Periods.window(endPeriod, historyConfig.months())
                        .stream()
                        // YYYY-MM strings sort chronologically
                        .filter(p -> p.compareTo(earliest) >= 0)
                        .collect(Collectors.toList()))
                .orElse(List.of());
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

    private List<CarbonHistoryPointDto> toTeamHistory(List<String> window,
                                                      Map<String, CarbonEntry> entriesByPeriod) {
        return window.stream()
                .map(period -> {
                    CarbonHistoryPointDto point = new CarbonHistoryPointDto();
                    point.period = period;
                    CarbonEntry entry = entriesByPeriod.get(period);
                    if (entry != null) {
                        point.totalCo2Kg = entry.totalCo2Kg;
                        point.awsCo2Kg = entry.awsCo2Kg;
                        point.azureCo2Kg = entry.azureCo2Kg;
                        point.gcpCo2Kg = entry.gcpCo2Kg;
                    }
                    return point;
                })
                .collect(Collectors.toList());
    }

    private CarbonHistoryPointDto toCompanyHistoryPoint(String period, List<CarbonEntry> entries) {
        CarbonHistoryPointDto point = new CarbonHistoryPointDto();
        point.period = period;
        point.totalCo2Kg = sumOrNull(entries, e -> e.totalCo2Kg);
        point.awsCo2Kg = sumOrNull(entries, e -> e.awsCo2Kg);
        point.azureCo2Kg = sumOrNull(entries, e -> e.azureCo2Kg);
        point.gcpCo2Kg = sumOrNull(entries, e -> e.gcpCo2Kg);
        return point;
    }

    /** Sums the non-null values of {@code field}, or returns null if there are none. */
    private static Double sumOrNull(List<CarbonEntry> entries, Function<CarbonEntry, Double> field) {
        return entries.stream()
                .map(field)
                .filter(Objects::nonNull)
                .reduce(Double::sum)
                .orElse(null);
    }

    /**
     * Each team's achievements awarded up to {@code period}, latest first. From the
     * default period on, all are included, so that e.g. a custom achievement of the
     * current month is shown too.
     */
    private Map<Long, List<AchievementDto>> findAchievementsByTeam(String period) {
        // YYYY-MM strings sort chronologically
        List<Achievement> achievements = period.compareTo(resolvePeriod(null)) >= 0
                ? Achievement.findAllLatestFirst()
                : Achievement.findUpTo(period);
        return achievements.stream()
                .collect(Collectors.groupingBy(a -> a.team.id,
                        Collectors.mapping(AchievementDto::from, Collectors.toList())));
    }
}
