package io.retit.greensoftware;

import io.retit.greensoftware.dto.LeaderboardEntryDto;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.LeaderboardService;
import io.retit.greensoftware.util.Periods;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

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
    private static final String TEAM_GAMMA = "Team Gamma";

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

        createEntry(alpha, "2024-06", 1000.0);
        createEntry(beta, "2024-06", 3000.0);

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

        createEntry(alpha, "2024-07", 1000.0);
        createEntry(beta, "2024-07", 3000.0);

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
        createEntry(alpha, "2024-07", 1250.0);
        createEntry(alpha, "2024-08", 1000.0);

        var entry = findEntry(leaderboardService.getLeaderboard("2024-08"), TEAM_ALPHA);

        assertNotNull(entry.previousCo2Kg);
        assertEquals(1250.0, entry.previousCo2Kg, 0.01);
        assertNotNull(entry.reductionPercent);
        assertEquals(20.0, entry.reductionPercent, 0.01, "Expected 20% reduction");
        assertEquals(1250.0, entry.history.get(entry.history.size() - 2).totalCo2Kg, 0.01,
                "History and previous total should agree");
    }

    @Test
    @Transactional
    void getLeaderboard_comparesDailyAveragesOfMonthsWithDifferentLength() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        // 100 kg per day in both the 31-day January and the 28-day February
        createEntry(alpha, "2025-01", 3100.0);
        createEntry(alpha, "2025-02", 2800.0);

        var entry = findEntry(leaderboardService.getLeaderboard("2025-02"), TEAM_ALPHA);

        assertEquals(3100.0, entry.previousCo2Kg, 0.01, "The previous total should not be normalized");
        assertEquals(2800.0, entry.totalCo2Kg, 0.01, "The total should not be normalized");
        assertEquals(0.0, entry.reductionPercent, 0.01, "Same daily emissions are no reduction");
    }

    @Test
    @Transactional
    void getCompanySummary_comparesDailyAveragesOfMonthsWithDifferentLength() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        // 2000 kg in the 31-day March, 2100 kg in the 30-day April
        createEntry(alpha, "2025-03", 1000.0);
        createEntry(alpha, "2025-04", 1050.0);
        createEntry(beta, "2025-03", 1000.0);
        createEntry(beta, "2025-04", 1050.0);

        var summary = leaderboardService.getCompanySummary("2025-04");

        assertEquals(2000.0, summary.previousTotalCo2Kg, 0.01);
        assertEquals(2100.0, summary.totalCo2Kg, 0.01);
        assertEquals((2000.0 / 31 - 2100.0 / 30) / (2000.0 / 31) * 100, summary.reductionPercent, 0.01,
                "The reduction should compare daily averages");
    }

    @Test
    @Transactional
    void getLeaderboard_withoutPreviousPeriod_hasNoReduction() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        // Not the previous period, so there is nothing to compare against
        createEntry(alpha, "2024-06", 1250.0);
        createEntry(alpha, "2024-08", 1000.0);

        var entry = findEntry(leaderboardService.getLeaderboard("2024-08"), TEAM_ALPHA);

        assertNull(entry.previousCo2Kg);
        assertNull(entry.reductionPercent);
    }

    @Test
    @Transactional
    void getCompanySummary_comparesWithPreviousPeriodOfTeamsWithData() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        // Both months have 31 days
        createEntry(alpha, "2024-07", 1000.0);
        createEntry(alpha, "2024-08", 800.0);
        createEntry(beta, "2024-07", 1500.0);
        createEntry(beta, "2024-08", 1200.0);
        // Not counted, as Gamma has no data for 2024-08
        Team gamma = Team.findByName(TEAM_GAMMA);
        assertNotNull(gamma);
        createEntry(gamma, "2024-07", 5000.0);

        var summary = leaderboardService.getCompanySummary("2024-08");

        assertEquals(2500.0, summary.previousTotalCo2Kg, 0.01, "Alpha 1000 + Beta 1500");
        assertEquals(20.0, summary.reductionPercent, 0.01, "2500 → 2000 is a 20% reduction");
    }

    @Test
    @Transactional
    void getCompanySummary_returnsTotalCo2() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        createEntry(alpha, "2024-09", 800.0);
        createEntry(beta, "2024-09", 1200.0);

        var summary = leaderboardService.getCompanySummary("2024-09");
        assertEquals("2024-09", summary.period);
        assertEquals(2000.0, summary.totalCo2Kg, 0.01);
        assertTrue(summary.teamCount >= 2);
    }

    @Test
    @Transactional
    void getLeaderboard_historyStartsAtEarliestSyncedPeriod() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        createEntry(alpha, "2024-04", 900.0);
        createEntry(alpha, "2024-06", 800.0);
        // After the selected period
        createEntry(alpha, "2024-07", 700.0);

        var entry = findEntry(leaderboardService.getLeaderboard("2024-06"), TEAM_ALPHA);

        assertEquals(List.of("2024-04", "2024-05", "2024-06"),
                entry.history.stream().map(p -> p.period).toList(),
                "History should be ordered oldest first and start at the earliest synced period");
        assertEquals(900.0, entry.history.get(0).totalCo2Kg, 0.01);
        assertNull(entry.history.get(1).totalCo2Kg, "Period without data should be null, not 0");
        assertEquals(800.0, entry.history.get(2).totalCo2Kg, 0.01);
    }

    @Test
    @Transactional
    void getLeaderboard_historyIsLimitedToConfiguredMonths() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        Periods.window("2024-08", 20).forEach(p -> createEntry(alpha, p, 100.0));

        var entry = findEntry(leaderboardService.getLeaderboard("2024-08"), TEAM_ALPHA);

        // The default of carbon.history.months
        assertEquals(13, entry.history.size());
        assertEquals("2023-08", entry.history.getFirst().period,
                "History should include the same month of the previous year");
        assertEquals("2024-08", entry.history.getLast().period);
    }

    @Test
    void getLeaderboard_historyIsEmptyWithoutSyncedData() {
        assertTrue(leaderboardService.getLeaderboard("2024-01").stream()
                .allMatch(e -> e.history != null && e.history.isEmpty()));
        assertTrue(leaderboardService.getCompanySummary("2024-01").history.isEmpty());
    }

    @Test
    void withoutPeriodAndData_defaultsToPreviousMonth() {
        // The current month is never synced, as its data is still incomplete
        assertEquals(Periods.previous(), leaderboardService.getCompanySummary(null).period);
        assertTrue(leaderboardService.getLeaderboard("").stream()
                .allMatch(e -> Periods.previous().equals(e.period)));
    }

    @Test
    @Transactional
    void withoutPeriod_defaultsToLatestPeriodEveryTeamHasDataFor() {
        String previous = Periods.previous();
        String beforePrevious = Periods.previous(previous);
        Team alpha = Team.findByName(TEAM_ALPHA);
        for (Team team : Team.<Team>listAll()) {
            createEntry(team, beforePrevious, 100.0);
            // Not a candidate, as the current month cannot be complete yet
            createEntry(team, Periods.current(), 100.0);
        }
        // Not every provider has published the previous month yet
        createEntry(alpha, previous, 100.0);

        assertEquals(beforePrevious, leaderboardService.getCompanySummary(null).period);
        assertTrue(leaderboardService.getLeaderboard("").stream()
                .allMatch(e -> beforePrevious.equals(e.period)));
    }

    @Test
    @Transactional
    void getLeaderboard_includesHistoryForTeamsWithoutCurrentData() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);
        // Only Beta has data for the selected period
        createEntry(alpha, "2023-12", 100.0);
        createEntry(beta, "2024-01", 200.0);

        var leaderboard = leaderboardService.getLeaderboard("2024-01");

        assertTrue(leaderboard.stream().allMatch(e -> e.history != null && e.history.size() == 2),
                "Every team should get the same history window");
        var alphaEntry = findEntry(leaderboard, TEAM_ALPHA);
        assertNull(alphaEntry.totalCo2Kg);
        assertEquals(100.0, alphaEntry.history.get(0).totalCo2Kg, 0.01);
        assertNull(alphaEntry.history.get(1).totalCo2Kg);
    }

    @Test
    @Transactional
    void getCompanySummary_includesCompanyHistory() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha);
        assertNotNull(beta);

        createEntry(alpha, "2024-07", 500.0);
        createEntry(alpha, "2024-09", 800.0);
        createEntry(beta, "2024-09", 1200.0);

        var summary = leaderboardService.getCompanySummary("2024-09");

        assertEquals(3, summary.history.size(), "History should start at the earliest synced period");
        assertEquals(500.0, summary.history.get(0).totalCo2Kg, 0.01);
        assertNull(summary.history.get(1).totalCo2Kg, "Period without data should be null, not 0");
        assertEquals("2024-09", summary.history.get(2).period);
        assertEquals(2000.0, summary.history.get(2).totalCo2Kg, 0.01, "Sum of all teams");
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

    private static LeaderboardEntryDto findEntry(List<LeaderboardEntryDto> leaderboard, String teamName) {
        return leaderboard.stream()
                .filter(e -> teamName.equals(e.teamName))
                .findFirst().orElseThrow();
    }

    private void createEntry(Team team, String period, Double totalCo2) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2;
        entry.syncTimestamp = Instant.now();
        entry.persist();
    }
}
