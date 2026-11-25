package io.retit.greensoftware.leaderboard;

import io.retit.greensoftware.achievement.Achievement;
import io.retit.greensoftware.achievement.AchievementType;
import io.retit.greensoftware.achievement.AwardedAchievement;
import io.retit.greensoftware.emissions.CarbonEntry;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.team.Team;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        Achievement.deleteAll();
        CarbonEntry.deleteAll();
    }

    @Test
    void getLeaderboard_returnsAllTeams_evenWithoutCarbonData() {
        var entries = leaderboardService.getLeaderboard("2024-01").teams();
        assertNotNull(entries);
        assertFalse(entries.isEmpty(), "Expected at least one team in the leaderboard");
    }

    @Test
    @Transactional
    void getLeaderboard_ranksTeamsByLowestCo2() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        Team beta = Team.findByName(TEAM_BETA);
        assertNotNull(alpha, TEAM_ALPHA + " should exist");
        assertNotNull(beta, TEAM_BETA + " should exist");

        createEntry(alpha, "2024-06", 1000.0);
        createEntry(beta, "2024-06", 3000.0);

        var leaderboard = leaderboardService.getLeaderboard("2024-06");

        var alphaEntry = leaderboard.teams().stream().filter(e -> TEAM_ALPHA.equals(e.team().name())).findFirst().orElse(null);
        var betaEntry = leaderboard.teams().stream().filter(e -> TEAM_BETA.equals(e.team().name())).findFirst().orElse(null);
        assertNotNull(alphaEntry, TEAM_ALPHA + " should be in leaderboard");
        assertNotNull(betaEntry, TEAM_BETA + " should be in leaderboard");
        assertTrue(alphaEntry.rank() < betaEntry.rank(), TEAM_ALPHA + " should have better rank (lower CO2)");
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

        var alphaEntry = leaderboard.teams().stream().filter(e -> TEAM_ALPHA.equals(e.team().name())).findFirst().orElseThrow();
        var betaEntry = leaderboard.teams().stream().filter(e -> TEAM_BETA.equals(e.team().name())).findFirst().orElseThrow();

        // Only Alpha and Beta have data
        assertNotNull(alphaEntry.companySharePercent());
        assertNotNull(betaEntry.companySharePercent());
        assertEquals(25.0, alphaEntry.companySharePercent(), 0.01,
                TEAM_ALPHA + " has 1000/4000 = 25% of company total");
        assertEquals(75.0, betaEntry.companySharePercent(), 0.01,
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

        assertNotNull(entry.previousCo2Kg());
        assertEquals(1250.0, entry.previousCo2Kg(), 0.01);
        assertNotNull(entry.reductionPercent());
        assertEquals(20.0, entry.reductionPercent(), 0.01, "Expected 20% reduction");
        assertEquals(1250.0, entry.history().get(entry.history().size() - 2).emissions().totalCo2Kg(), 0.01,
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

        assertEquals(3100.0, entry.previousCo2Kg(), 0.01, "The previous total should not be normalized");
        assertEquals(2800.0, entry.emissions().totalCo2Kg(), 0.01, "The total should not be normalized");
        assertEquals(0.0, entry.reductionPercent(), 0.01, "Same daily emissions are no reduction");
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

        assertEquals(2000.0, summary.previousTotalCo2Kg(), 0.01);
        assertEquals(2100.0, summary.totalCo2Kg(), 0.01);
        assertEquals((2000.0 / 31 - 2100.0 / 30) / (2000.0 / 31) * 100, summary.reductionPercent(), 0.01,
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

        assertNull(entry.previousCo2Kg());
        assertNull(entry.reductionPercent());
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

        assertEquals(2500.0, summary.previousTotalCo2Kg(), 0.01, "Alpha 1000 + Beta 1500");
        assertEquals(20.0, summary.reductionPercent(), 0.01, "2500 → 2000 is a 20% reduction");
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
        assertEquals("2024-09", summary.period());
        assertEquals(2000.0, summary.totalCo2Kg(), 0.01);
        assertTrue(summary.teamCount() >= 2);
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
                entry.history().stream().map(p -> p.period()).toList(),
                "History should be ordered oldest first and start at the earliest synced period");
        assertEquals(900.0, entry.history().get(0).emissions().totalCo2Kg(), 0.01);
        assertNull(entry.history().get(1).emissions().totalCo2Kg(), "Period without data should be null, not 0");
        assertEquals(800.0, entry.history().get(2).emissions().totalCo2Kg(), 0.01);
    }

    @Test
    @Transactional
    void getLeaderboard_historyIsLimitedToConfiguredMonths() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        assertNotNull(alpha);

        Periods.window("2024-08", 20).forEach(p -> createEntry(alpha, p, 100.0));

        var entry = findEntry(leaderboardService.getLeaderboard("2024-08"), TEAM_ALPHA);

        // The default of carbon.history.months
        assertEquals(13, entry.history().size());
        assertEquals("2023-08", entry.history().getFirst().period(),
                "History should include the same month of the previous year");
        assertEquals("2024-08", entry.history().getLast().period());
    }

    @Test
    void getLeaderboard_historyIsEmptyWithoutSyncedData() {
        assertTrue(leaderboardService.getLeaderboard("2024-01").teams().stream()
                .allMatch(e -> e.history().isEmpty()));
        assertTrue(leaderboardService.getCompanySummary("2024-01").history().isEmpty());
    }

    @Test
    void withoutPeriodAndData_defaultsToPreviousMonth() {
        // The current month is never synced, as its data is still incomplete
        assertEquals(Periods.previous(), leaderboardService.getCompanySummary(null).period());
        assertEquals(Periods.previous(), leaderboardService.getLeaderboard("").period());
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

        assertEquals(beforePrevious, leaderboardService.getCompanySummary(null).period());
        assertEquals(beforePrevious, leaderboardService.getLeaderboard("").period());
    }

    @Test
    @Transactional
    void getDashboard_isOfTheDefaultOrTheGivenPeriod() {
        Team alpha = Team.findByName(TEAM_ALPHA);
        createEntry(alpha, "2024-04", 900.0);
        createEntry(alpha, "2024-06", 800.0);

        var dashboard = leaderboardService.getDashboard(null);
        assertEquals(leaderboardService.defaultPeriod(), dashboard.period());
        assertEquals(dashboard.period(), dashboard.leaderboard().period());
        assertEquals(dashboard.period(), dashboard.summary().period());

        assertEquals("2024-04", leaderboardService.getDashboard("2024-04").period());
    }

    @Test
    @Transactional
    void getDashboard_isEmptyWithoutDataUpToThePeriod() {
        assertTrue(leaderboardService.getDashboard("2024-06").isEmpty());

        createEntry(Team.findByName(TEAM_ALPHA), "2024-06", 900.0);

        assertFalse(leaderboardService.getDashboard("2024-06").isEmpty());
        assertFalse(leaderboardService.getDashboard("2024-07").isEmpty(), "The charts still show data");
        assertTrue(leaderboardService.getDashboard("2024-05").isEmpty());
    }

    @Test
    @Transactional
    void periodsWithData_areLatestFirst() {
        createEntry(Team.findByName(TEAM_ALPHA), "2024-04", 900.0);
        createEntry(Team.findByName(TEAM_BETA), "2024-04", 900.0);
        createEntry(Team.findByName(TEAM_ALPHA), "2024-06", 800.0);

        assertEquals(List.of("2024-06", "2024-04"), leaderboardService.periodsWithData(), "Each only once");
    }

    @Test
    @Transactional
    void getLeaderboard_showsOnlyAchievementsUpToTheSelectedPeriod() {
        String previous = Periods.previous();
        String beforePrevious = Periods.previous(previous);
        for (Team team : Team.<Team>listAll()) {
            createEntry(team, beforePrevious, 100.0);
            createEntry(team, previous, 100.0);
        }
        Team alpha = Team.findByName(TEAM_ALPHA);
        createAchievement(alpha, AchievementType.HIGHEST_REDUCTION, beforePrevious);
        createAchievement(alpha, AchievementType.HIGHEST_REDUCTION, previous);

        assertEquals(List.of(beforePrevious),
                achievementPeriods(leaderboardService.getLeaderboard(beforePrevious), TEAM_ALPHA),
                "Achievements awarded after the selected period should not be shown");
        assertEquals(List.of(previous, beforePrevious),
                achievementPeriods(leaderboardService.getLeaderboard(previous), TEAM_ALPHA),
                "Achievements should be shown latest first");
    }

    @Test
    @Transactional
    void getLeaderboard_showsAchievementsOfTheCurrentMonthForTheLatestPeriod() {
        String previous = Periods.previous();
        for (Team team : Team.<Team>listAll()) {
            createEntry(team, previous, 100.0);
        }
        // Granted after the latest period every team has data for
        createAchievement(Team.findByName(TEAM_ALPHA), AchievementType.CUSTOM, Periods.current());

        assertEquals(List.of(Periods.current()),
                achievementPeriods(leaderboardService.getLeaderboard(null), TEAM_ALPHA),
                "A recent custom achievement should be shown for the latest period");
        assertEquals(List.of(Periods.current()),
                achievementPeriods(leaderboardService.getLeaderboard(previous), TEAM_ALPHA),
                "Selecting the latest period explicitly should be the same as the default");
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

        assertTrue(leaderboard.teams().stream().allMatch(e -> e.history().size() == 2),
                "Every team should get the same history window");
        var alphaEntry = findEntry(leaderboard, TEAM_ALPHA);
        assertNull(alphaEntry.emissions().totalCo2Kg());
        assertEquals(100.0, alphaEntry.history().get(0).emissions().totalCo2Kg(), 0.01);
        assertNull(alphaEntry.history().get(1).emissions().totalCo2Kg());
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

        assertEquals(3, summary.history().size(), "History should start at the earliest synced period");
        assertEquals(500.0, summary.history().get(0).emissions().totalCo2Kg(), 0.01);
        assertNull(summary.history().get(1).emissions().totalCo2Kg(), "Period without data should be null, not 0");
        assertEquals("2024-09", summary.history().get(2).period());
        assertEquals(2000.0, summary.history().get(2).emissions().totalCo2Kg(), 0.01, "Sum of all teams");
    }

    private static RankedTeam findEntry(Leaderboard leaderboard, String teamName) {
        return leaderboard.teams().stream()
                .filter(e -> teamName.equals(e.team().name()))
                .findFirst().orElseThrow();
    }

    private static List<String> achievementPeriods(Leaderboard leaderboard, String teamName) {
        return findEntry(leaderboard, teamName).achievements().stream()
                .map(AwardedAchievement::period)
                .toList();
    }

    private void createAchievement(Team team, AchievementType type, String period) {
        Achievement achievement = new Achievement();
        achievement.team = team;
        achievement.type = type;
        achievement.title = type == AchievementType.CUSTOM ? "Shoutout" : type.title;
        achievement.period = period;
        achievement.persist();
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
