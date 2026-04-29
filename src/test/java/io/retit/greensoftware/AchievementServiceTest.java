package io.retit.greensoftware;

import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.AchievementService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AchievementService rule engine.
 */
@QuarkusTest
class AchievementServiceTest {

    @Inject
    AchievementService achievementService;

    private static final String TEAM_ALPHA = "Team Alpha";
    private static final String TEAM_BETA = "Team Beta";

    @BeforeEach
    @Transactional
    void setUp() {
        Achievement.deleteAll();
        CarbonEntry.deleteAll();
    }

    @Test
    @Transactional
    void evaluateAchievements_grantsLeaderboardLeaderToLowestCo2Team() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        createEntry(alpha, "2024-10", 500.0, null, null);
        createEntry(beta, "2024-10", 2000.0, null, null);

        achievementService.evaluateAchievements("2024-10");

        List<Achievement> achievements = Achievement.findByTeam(alpha);
        assertTrue(achievements.stream()
                .anyMatch(a -> a.type == AchievementType.LEADERBOARD_LEADER),
                TEAM_ALPHA + " (lowest CO2) should receive LEADERBOARD_LEADER");
    }

    @Test
    @Transactional
    void evaluateAchievements_grantsBigSaverForOver20PctReduction() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        // 25% reduction
        createEntry(alpha, "2024-11", 750.0, 1000.0, 25.0);

        achievementService.evaluateAchievements("2024-11");

        List<Achievement> achievements = Achievement.findByTeam(alpha);
        assertTrue(achievements.stream()
                .anyMatch(a -> a.type == AchievementType.BIG_SAVER),
                TEAM_ALPHA + " (25% reduction) should receive BIG_SAVER");
    }

    @Test
    @Transactional
    void evaluateAchievements_grantsFirstNetZeroForZeroEmissions() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        createEntry(alpha, "2024-12", 0.0, 500.0, 100.0);

        achievementService.evaluateAchievements("2024-12");

        List<Achievement> achievements = Achievement.findByTeam(alpha);
        assertTrue(achievements.stream()
                .anyMatch(a -> a.type == AchievementType.FIRST_NET_ZERO),
                "Team with zero emissions should receive FIRST_NET_ZERO");
    }

    @Test
    @Transactional
    void evaluateAchievements_doesNotDuplicateAchievements() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        createEntry(alpha, "2025-01", 500.0, null, null);
        achievementService.evaluateAchievements("2025-01");

        // Evaluate again for same period
        achievementService.evaluateAchievements("2025-01");

        long leaderCount = Achievement.count("team = ?1 and type = ?2",
                alpha, AchievementType.LEADERBOARD_LEADER);
        assertEquals(1, leaderCount, "Achievement should not be granted twice");
    }

    @Test
    @Transactional
    void grantCustomAchievement_persistsAchievement() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        Achievement achievement = achievementService.grantCustomAchievement(
                alpha, "🎉 Special Award", "Great teamwork!", "🎉");

        assertNotNull(achievement.id);
        assertEquals("🎉 Special Award", achievement.title);
        assertEquals(AchievementType.CUSTOM, achievement.type);
        assertNotNull(achievement.earnedAt);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createEntry(Team team, String period, Double totalCo2,
                              Double prevCo2, Double reductionPct) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2;
        entry.previousCo2Kg = prevCo2;
        entry.reductionPercentage = reductionPct;
        entry.syncTimestamp = Instant.now();
        entry.persist();
    }
}
