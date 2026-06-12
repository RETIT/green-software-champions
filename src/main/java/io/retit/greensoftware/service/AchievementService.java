package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

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

        checkFirstNetZero(entries, period);
        checkBigSaver(entries, period);
        checkHighestReduction(entries, period);
        checkLeaderboardLeader(entries, period);
        checkGreenStreak(period);
    }

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
    private void checkBigSaver(List<CarbonEntry> entries, String period) {
        for (CarbonEntry entry : entries) {
            if (entry.reductionPercentage != null && entry.reductionPercentage >= 20.0) {
                grantIfNew(entry.team, AchievementType.BIG_SAVER,
                        "📉 Big Saver",
                        String.format("Reduced cloud carbon emissions by %.1f%% in period %s",
                                entry.reductionPercentage, period),
                        "📉");
            }
        }
    }

    /** Award "Highest Reduction" to the team with the largest percentage reduction this period. */
    private void checkHighestReduction(List<CarbonEntry> entries, String period) {
        entries.stream()
                .filter(e -> e.reductionPercentage != null)
                .max(Comparator.comparingDouble(e -> e.reductionPercentage))
                .ifPresent(best -> {
                    if (best.reductionPercentage > 0) {
                        grantIfNew(best.team, AchievementType.HIGHEST_REDUCTION,
                                "🏆 Reduction Champion",
                                String.format("Achieved the highest carbon reduction (%.1f%%) in period %s",
                                        best.reductionPercentage, period),
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
            for (CarbonEntry entry : history) {
                if (entry.reductionPercentage != null && entry.reductionPercentage > 0) {
                    consecutiveReductions++;
                } else {
                    break;
                }
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
