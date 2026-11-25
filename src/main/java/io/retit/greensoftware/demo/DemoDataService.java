package io.retit.greensoftware.demo;

import io.retit.greensoftware.achievement.Achievement;
import io.retit.greensoftware.achievement.AchievementType;
import io.retit.greensoftware.achievement.AwardedAchievement;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.leaderboard.Dashboard;
import io.retit.greensoftware.leaderboard.LeaderboardCalculator.TeamEmissions;
import io.retit.greensoftware.leaderboard.LeaderboardCalculator;
import io.retit.greensoftware.team.CloudProvider;
import io.retit.greensoftware.team.TeamInfo;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The data of the demo pages: made-up teams with {@link SampleEmissions}. Always the same
 * for a period, and independent of the database.
 */
@ApplicationScoped
public class DemoDataService {

    private static final int HISTORY_MONTHS = 13;

    /** @param achievements latest first, not derived from the emissions */
    private record DemoTeam(TeamInfo info, List<DemoAchievement> achievements) {}

    /** @param threshold null for types without thresholds */
    private record DemoAchievement(AchievementType type, Integer threshold, int monthsAgo) {}

    private static final List<DemoTeam> TEAMS = List.of(
            new DemoTeam(new TeamInfo(1L, "Team Alpha", "#75b729", "🌱"), List.of(
                    new DemoAchievement(AchievementType.HIGHEST_REDUCTION, null, 0),
                    new DemoAchievement(AchievementType.GREEN_STREAK, 6, 0),
                    new DemoAchievement(AchievementType.HIGHEST_REDUCTION, null, 1),
                    new DemoAchievement(AchievementType.GREEN_STREAK, 3, 3))),
            new DemoTeam(new TeamInfo(2L, "Team Beta", "#3b8ecf", "💧"), List.of()),
            new DemoTeam(new TeamInfo(3L, "Team Gamma", "#e09c1a", "⚡"), List.of()),
            new DemoTeam(new TeamInfo(4L, "Team Delta", "#8b5cf6", "🚀"), List.of(
                    new DemoAchievement(AchievementType.MOST_CO2_SAVED, null, 0))));

    public Dashboard getDashboard(String period) {
        List<String> periods = Periods.window(period, HISTORY_MONTHS);
        LeaderboardCalculator calculator = new LeaderboardCalculator(period, periods, TEAMS.stream()
                .map(team -> toTeamEmissions(team, periods))
                .toList());
        Map<Long, List<AwardedAchievement>> achievementsByTeam = TEAMS.stream()
                .collect(Collectors.toMap(team -> team.info().id(), team -> toAchievements(team, period)));
        return new Dashboard(calculator.summary(), calculator.leaderboard(achievementsByTeam));
    }

    private static TeamEmissions toTeamEmissions(DemoTeam team, List<String> periods) {
        return new TeamEmissions(team.info(),
                SampleEmissions.of(team.info().name(), EnumSet.allOf(CloudProvider.class)).in(periods));
    }

    private static List<AwardedAchievement> toAchievements(DemoTeam team, String period) {
        return team.achievements().stream()
                .map(achievement -> award(achievement, period))
                .toList();
    }

    private static AwardedAchievement award(DemoAchievement demo, String period) {
        Achievement achievement = new Achievement();
        achievement.type = demo.type();
        achievement.title = demo.type().title;
        achievement.badgeIcon = demo.type().badgeIcon;
        achievement.period = Periods.toYearMonth(period).minusMonths(demo.monthsAgo()).toString();
        achievement.threshold = demo.threshold();
        return AwardedAchievement.of(achievement);
    }
}
