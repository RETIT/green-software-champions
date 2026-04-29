package io.retit.greensoftware;

import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.LeaderboardService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the LeaderboardService.
 * Uses an H2 in-memory database and disables OIDC + Quinoa for fast testing.
 */
@QuarkusTest
class LeaderboardServiceTest {

    @Inject
    LeaderboardService leaderboardService;

    private static final String TEAM_ALPHA = "Team Alpha";
    private static final String TEAM_BETA = "Team Beta";

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up any leftover data
        CarbonEntry.deleteAll();
    }

    @Test
    void getLeaderboard_returnsAllTeams_evenWithoutCarbonData() {
        var entries = leaderboardService.getLeaderboard("2024-01");
        // Teams are seeded by TeamSetupService on startup from application.yaml
        assertNotNull(entries);
        assertFalse(entries.isEmpty(), "Expected at least one team in the leaderboard");
    }

    @Test
    @Transactional
    void getLeaderboard_ranksTeamsByLowestCo2() {
        // Insert carbon data: Team Alpha has less CO2 than Team Beta
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha, TEAM_ALPHA + " should exist");
        assertNotNull(beta, TEAM_BETA + " should exist");

        createEntry(alpha, "2024-06", 1000.0, 1100.0);
        createEntry(beta, "2024-06", 3000.0, 3200.0);

        var leaderboard = leaderboardService.getLeaderboard("2024-06");

        // Find alpha and beta entries in the result (there may be more teams from YAML config)
        var alphaEntry = leaderboard.stream().filter(e -> TEAM_ALPHA.equals(e.teamName)).findFirst().orElse(null);
        var betaEntry = leaderboard.stream().filter(e -> TEAM_BETA.equals(e.teamName)).findFirst().orElse(null);
        assertNotNull(alphaEntry, TEAM_ALPHA + " should be in leaderboard");
        assertNotNull(betaEntry, TEAM_BETA + " should be in leaderboard");
        assertTrue(alphaEntry.rank < betaEntry.rank, TEAM_ALPHA + " should have better rank (lower CO2)");
    }

    @Test
    @Transactional
    void getLeaderboard_calculatesCompanySharePercent() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        createEntry(alpha, "2024-07", 1000.0, null);
        createEntry(beta, "2024-07", 3000.0, null);

        var leaderboard = leaderboardService.getLeaderboard("2024-07");

        var alphaEntry = leaderboard.stream().filter(e -> TEAM_ALPHA.equals(e.teamName)).findFirst().orElseThrow();
        var betaEntry = leaderboard.stream().filter(e -> TEAM_BETA.equals(e.teamName)).findFirst().orElseThrow();

        // Company total is the sum of all teams with data for this period
        // (only alpha and beta have data, so total = 4000)
        assertNotNull(alphaEntry.companySharePercent);
        assertNotNull(betaEntry.companySharePercent);
        assertEquals(25.0, alphaEntry.companySharePercent, 0.01,
                TEAM_ALPHA + " has 1000/4000 = 25% of company total");
        assertEquals(75.0, betaEntry.companySharePercent, 0.01,
                TEAM_BETA + " has 3000/4000 = 75% of company total");
    }

    @Test
    @Transactional
    void getLeaderboard_calculatesReductionPercentage() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        // 1000 kg this period, 1250 kg last period → 20% reduction
        createEntry(alpha, "2024-08", 1000.0, 1250.0);

        var leaderboard = leaderboardService.getLeaderboard("2024-08");
        var entry = leaderboard.stream()
                .filter(e -> TEAM_ALPHA.equals(e.teamName))
                .findFirst().orElseThrow();

        assertNotNull(entry.reductionPercent);
        assertEquals(20.0, entry.reductionPercent, 0.01, "Expected 20% reduction");
    }

    @Test
    @Transactional
    void getCompanySummary_returnsTotalCo2() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        createEntry(alpha, "2024-09", 800.0, null);
        createEntry(beta, "2024-09", 1200.0, null);

        var summary = leaderboardService.getCompanySummary("2024-09");
        assertEquals("2024-09", summary.period);
        assertEquals(2000.0, summary.totalCo2Kg, 0.01);
        assertTrue(summary.teamCount >= 2);
    }

    @Test
    void getAllTeams_returnsTeams() {
        var teams = leaderboardService.getAllTeams();
        assertNotNull(teams);
        assertFalse(teams.isEmpty());
        // The teams from application.yaml should be present
        assertTrue(teams.stream().anyMatch(t -> TEAM_ALPHA.equals(t.name)),
                TEAM_ALPHA + " should exist");
    }

    @Test
    void getAllTeams_includesCloudProjectsPerTeam() {
        var teams = leaderboardService.getAllTeams();
        var alpha = teams.stream()
                .filter(t -> TEAM_ALPHA.equals(t.name))
                .findFirst().orElseThrow(() -> new AssertionError(TEAM_ALPHA + " not found"));
        assertNotNull(alpha.projects);
        assertFalse(alpha.projects.isEmpty(), TEAM_ALPHA + " should have cloud projects");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createEntry(Team team, String period, Double totalCo2, Double prevCo2) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2;
        entry.previousCo2Kg = prevCo2;
        if (prevCo2 != null && prevCo2 > 0) {
            entry.reductionPercentage = ((prevCo2 - totalCo2) / prevCo2) * 100.0;
        }
        entry.syncTimestamp = Instant.now();
        entry.persist();
    }
}
