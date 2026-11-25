package io.retit.greensoftware.leaderboard;

import io.retit.greensoftware.achievement.Achievement;
import io.retit.greensoftware.achievement.AwardedAchievement;
import io.retit.greensoftware.emissions.CarbonEntry;
import io.retit.greensoftware.emissions.Emissions;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.leaderboard.LeaderboardCalculator.TeamEmissions;
import io.retit.greensoftware.team.Team;
import io.retit.greensoftware.team.TeamInfo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class LeaderboardService {

    @Inject
    CarbonHistoryConfig historyConfig;

    /** Like {@link #getLeaderboard} and {@link #getCompanySummary}, read together. */
    @Transactional
    public Dashboard getDashboard(String period) {
        LeaderboardCalculator calculator = calculatorFor(resolvePeriod(period));
        return new Dashboard(calculator.summary(), toLeaderboard(calculator));
    }

    /** @param period YYYY-MM, or blank for the {@link #defaultPeriod()} */
    @Transactional
    public Leaderboard getLeaderboard(String period) {
        return toLeaderboard(calculatorFor(resolvePeriod(period)));
    }

    @Transactional
    public CompanySummary getCompanySummary(String period) {
        return calculatorFor(resolvePeriod(period)).summary();
    }

    private String resolvePeriod(String period) {
        return period != null && !period.isBlank() ? period : defaultPeriod();
    }

    /**
     * The latest period of the history window every team has data for, as the providers
     * publish a month only weeks after it ended. Falls back to the previous month.
     */
    @Transactional
    public String defaultPeriod() {
        return CarbonEntry.findCompletePeriods(Periods.window(Periods.previous(), historyConfig.months()))
                .stream()
                .findFirst()
                .orElseGet(Periods::previous);
    }

    /** Latest first. */
    @Transactional
    public List<String> periodsWithData() {
        return CarbonEntry.findPeriods();
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

    private LeaderboardCalculator calculatorFor(String period) {
        List<String> historyPeriods = historyWindow(period);
        // The history leaves out the previous period if only a single month is shown
        Set<String> periods = new HashSet<>(historyPeriods);
        periods.add(period);
        periods.add(Periods.previous(period));
        Map<Long, Map<String, Emissions>> emissionsByTeam = CarbonEntry.findByPeriods(periods)
                .stream()
                .collect(Collectors.groupingBy(e -> e.team.id,
                        Collectors.toMap(e -> e.period, Emissions::of)));
        return new LeaderboardCalculator(period, historyPeriods, Team.<Team>listAll()
                .stream()
                .map(team -> new TeamEmissions(TeamInfo.of(team), emissionsByTeam.getOrDefault(team.id, Map.of())))
                .toList());
    }

    private Leaderboard toLeaderboard(LeaderboardCalculator calculator) {
        return calculator.leaderboard(findAchievementsByTeam(calculator.period()));
    }

    /**
     * Each team's achievements awarded up to {@code period}, latest first. From the
     * default period on, all are included, so that e.g. a custom achievement of the
     * current month is shown too.
     */
    private Map<Long, List<AwardedAchievement>> findAchievementsByTeam(String period) {
        // YYYY-MM strings sort chronologically
        List<Achievement> achievements = period.compareTo(defaultPeriod()) >= 0
                ? Achievement.findAllLatestFirst()
                : Achievement.findUpTo(period);
        return achievements.stream()
                .collect(Collectors.groupingBy(a -> a.team.id,
                        Collectors.mapping(AwardedAchievement::of, Collectors.toList())));
    }
}
