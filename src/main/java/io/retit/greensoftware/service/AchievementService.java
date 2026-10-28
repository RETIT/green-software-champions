package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.util.Reductions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Evaluates rule-based achievement criteria after each carbon data sync and
 * awards achievements to qualifying teams.
 */
@ApplicationScoped
public class AchievementService {

    private static final Logger LOG = Logger.getLogger(AchievementService.class);

    /**
     * Evaluates all achievement rules for the given period and persists new awards.
     * Must be called within a transaction.
     */
    @Transactional
    public void evaluateAchievements(String period) {
        List<CarbonEntry> entries = CarbonEntry.findByPeriod(period);
        if (entries.isEmpty()) {
            return;
        }

        Map<Long, CarbonEntry> previousByTeam = CarbonEntry.findByPeriodByTeamId(Periods.previous(period));
        List<Reduction> reductions = new ArrayList<>();
        for (CarbonEntry entry : entries) {
            Double percent = Reductions.between(previousByTeam.get(entry.team.id), entry);
            if (percent != null) {
                reductions.add(new Reduction(entry.team, percent));
            }
        }

        checkFirstNetZero(entries, period);
        checkBigSaver(reductions, period);
        checkHighestReduction(reductions, period);
        checkLeaderboardLeader(entries, period);
        checkGreenStreak(period);
    }

    /** A team's reduction vs. the previous period in percent. */
    private record Reduction(Team team, double percent) {}

    /** Award "First Net Zero" to any team that achieved zero (or negative) emissions this period. */
    private void checkFirstNetZero(List<CarbonEntry> entries, String period) {
        for (CarbonEntry entry : entries) {
            if (entry.totalCo2Kg != null && entry.totalCo2Kg <= 0) {
                grantIfNew(entry.team, AchievementType.FIRST_NET_ZERO,
                        "🌍 Carbon Neutral Pioneer",
                        "Achieved net-zero cloud carbon emissions for period " + period,
                        "🌍");
            }
        }
    }

    /** Award "Big Saver" to any team that reduced emissions by 20% or more vs. last period. */
    private void checkBigSaver(List<Reduction> reductions, String period) {
        for (Reduction reduction : reductions) {
            if (reduction.percent() >= 20.0) {
                grantIfNew(reduction.team(), AchievementType.BIG_SAVER,
                        "📉 Big Saver",
                        String.format("Reduced cloud carbon emissions by %.1f%% in period %s",
                                reduction.percent(), period),
                        "📉");
            }
        }
    }

    /** Award "Highest Reduction" to the team with the largest percentage reduction this period. */
    private void checkHighestReduction(List<Reduction> reductions, String period) {
        reductions.stream()
                .max(Comparator.comparingDouble(Reduction::percent))
                .ifPresent(best -> {
                    if (best.percent() > 0) {
                        grantIfNew(best.team(), AchievementType.HIGHEST_REDUCTION,
                                "🏆 Reduction Champion",
                                String.format("Achieved the highest carbon reduction (%.1f%%) in period %s",
                                        best.percent(), period),
                                "🏆");
                    }
                });
    }

    /** Award "Leaderboard Leader" to the team with the lowest total carbon this period. */
    private void checkLeaderboardLeader(List<CarbonEntry> entries, String period) {
        entries.stream()
                .filter(e -> e.totalCo2Kg != null)
                .min(Comparator.comparingDouble(e -> e.totalCo2Kg))
                .ifPresent(leader -> grantIfNew(leader.team, AchievementType.LEADERBOARD_LEADER,
                        "🥇 Leaderboard Leader",
                        "Lowest cloud carbon footprint in period " + period,
                        "🥇"));
    }

    /** Award "Green Streak" to any team with 6+ consecutive months of reduction. */
    private void checkGreenStreak(String period) {
        List<Team> teams = Team.listAll();
        for (Team team : teams) {
            List<CarbonEntry> history = CarbonEntry.findByTeamOrderByPeriodDesc(team);
            int consecutiveReductions = 0;
            for (int i = 0; i + 1 < history.size(); i++) {
                CarbonEntry entry = history.get(i);
                CarbonEntry previous = history.get(i + 1);
                // A gap in the history ends the streak
                Double reduction = previous.period.equals(Periods.previous(entry.period))
                        ? Reductions.between(previous, entry)
                        : null;
                if (reduction == null || reduction <= 0) {
                    break;
                }
                consecutiveReductions++;
            }
            if (consecutiveReductions >= 6) {
                grantIfNew(team, AchievementType.GREEN_STREAK,
                        "🌱 Green Streak",
                        "6 or more consecutive months of carbon reduction",
                        "🌱");
            } else if (consecutiveReductions >= 3) {
                grantIfNew(team, AchievementType.CONSECUTIVE_CHAMPION,
                        "⚡ Consistent Champion",
                        "3 or more consecutive months of carbon reduction",
                        "⚡");
            }
        }
    }

    private void grantIfNew(Team team, AchievementType type, String title,
                             String description, String badgeIcon) {
        boolean alreadyGranted = Achievement.count("team = ?1 and type = ?2", team, type) > 0;
        if (!alreadyGranted) {
            Achievement achievement = new Achievement();
            achievement.team = team;
            achievement.type = type;
            achievement.title = title;
            achievement.description = description;
            achievement.badgeIcon = badgeIcon;
            achievement.earnedAt = Instant.now();
            achievement.persist();
            LOG.infof("Granted achievement '%s' to team '%s'", title, team.name);
        }
    }

    /**
     * Manually grants a custom achievement to a team (e.g., via admin UI shoutout).
     */
    @Transactional
    public Achievement grantCustomAchievement(Team team, String title, String description,
                                               String badgeIcon) {
        Achievement achievement = new Achievement();
        achievement.team = team;
        achievement.type = AchievementType.CUSTOM;
        achievement.title = title;
        achievement.description = description;
        achievement.badgeIcon = badgeIcon;
        achievement.earnedAt = Instant.now();
        achievement.persist();
        return achievement;
    }
}
