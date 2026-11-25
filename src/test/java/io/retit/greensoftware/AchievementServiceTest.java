package io.retit.greensoftware;

import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.AchievementService;
import io.retit.greensoftware.service.AwardedAchievement;
import io.retit.greensoftware.util.Periods;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AchievementServiceTest {

    @Inject
    AchievementService achievementService;

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
    @Transactional
    void highestReduction_grantedToTheTeamWithTheLargestReductionInPercent() {
        Team alpha = team(TEAM_ALPHA);
        Team beta = team(TEAM_BETA);
        createMonths(alpha, "2025-01", 10, 8);
        createMonths(beta, "2025-01", 10, 9);

        achievementService.evaluateAchievements("2025-02");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.HIGHEST_REDUCTION);
        assertEquals(1, awards.size(), TEAM_ALPHA + " (20% less) should be the Reduction Champion");
        assertEquals("Reduction Champion", awards.getFirst().title,
                "The title should not repeat the badge icon");
        assertEquals("🏆", awards.getFirst().badgeIcon);
        assertEquals(20.0, awards.getFirst().measuredValue, 0.001);
        assertTrue(achievementsOf(beta, AchievementType.HIGHEST_REDUCTION).isEmpty());
    }

    @Test
    @Transactional
    void highestReduction_canBeEarnedAgainInLaterPeriods() {
        Team alpha = team(TEAM_ALPHA);
        Team beta = team(TEAM_BETA);
        createMonths(alpha, "2025-01", 10, 8, 6);
        createMonths(beta, "2025-01", 10, 9, 8.5);

        evaluate("2025-02", "2025-03");

        assertEquals(List.of("2025-03", "2025-02"), periodsOf(alpha, AchievementType.HIGHEST_REDUCTION),
                TEAM_ALPHA + " should be the Reduction Champion in both months");
    }

    @Test
    @Transactional
    void highestReduction_ignoresTeamsWithATinyShareOfTheCompanyEmissions() {
        Team alpha = team(TEAM_ALPHA);
        Team beta = team(TEAM_BETA);
        // 90% less, but only 0.1% of the company's emissions in the previous month
        createMonths(alpha, "2025-01", 0.1, 0.01);
        createMonths(beta, "2025-01", 100, 90);

        achievementService.evaluateAchievements("2025-02");

        assertTrue(achievementsOf(alpha, AchievementType.HIGHEST_REDUCTION).isEmpty(),
                "A tiny footprint swinging by a large percentage should not win");
        assertEquals(1, achievementsOf(beta, AchievementType.HIGHEST_REDUCTION).size());
    }

    @Test
    @Transactional
    void mostCo2Saved_grantedToTheTeamThatSavedTheMostInKg() {
        Team alpha = team(TEAM_ALPHA);
        Team beta = team(TEAM_BETA);
        // Alpha: 20% less, saves 2 kg per day; Beta: 10% less, saves 10 kg per day
        createMonths(alpha, "2025-01", 10, 8);
        createMonths(beta, "2025-01", 100, 90);

        achievementService.evaluateAchievements("2025-02");

        List<Achievement> awards = achievementsOf(beta, AchievementType.MOST_CO2_SAVED);
        assertEquals(1, awards.size(), TEAM_BETA + " should have saved the most CO2e");
        assertEquals(10.0 * 28, awards.getFirst().measuredValue, 0.001,
                "The saving should be the daily saving over the 28 days of February");
        assertTrue(achievementsOf(alpha, AchievementType.MOST_CO2_SAVED).isEmpty());
        assertEquals(1, achievementsOf(alpha, AchievementType.HIGHEST_REDUCTION).size(),
                TEAM_ALPHA + " should still be the Reduction Champion");
    }

    @Test
    @Transactional
    void bigSaver_grantedForAReductionVsTheAverageOfThePreviousThreeMonths() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2024-11", 10, 10, 10, 7.5);

        achievementService.evaluateAchievements("2025-02");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.BIG_SAVER);
        assertEquals(1, awards.size(), TEAM_ALPHA + " (25% less) should be a Big Saver");
        assertEquals(25.0, awards.getFirst().measuredValue, 0.001);
    }

    @Test
    @Transactional
    void bigSaver_notGrantedForReturningToNormalAfterASpike() {
        Team alpha = team(TEAM_ALPHA);
        // 33% less than in the previous month, but only 14% less than on average
        createMonths(alpha, "2024-11", 10, 10, 15, 10);

        achievementService.evaluateAchievements("2025-02");

        assertTrue(achievementsOf(alpha, AchievementType.BIG_SAVER).isEmpty(),
                "Returning to normal after a spike should not count");
    }

    @Test
    @Transactional
    void bigSaver_requiresDataForEveryBaselineMonth() {
        Team alpha = team(TEAM_ALPHA);
        // 2024-11 is missing
        createMonths(alpha, "2024-12", 10, 10, 5);

        achievementService.evaluateAchievements("2025-02");

        assertTrue(achievementsOf(alpha, AchievementType.BIG_SAVER).isEmpty(),
                "Without data for each of the previous 3 months there is no baseline");
    }

    @Test
    @Transactional
    void bigSaver_comparesDailyAverages() {
        Team alpha = team(TEAM_ALPHA);
        // 24% less than the average total, but February has fewer days: only 17% less per day
        createEntry(alpha, "2024-11", 1000.0);
        createEntry(alpha, "2024-12", 1000.0);
        createEntry(alpha, "2025-01", 1000.0);
        createEntry(alpha, "2025-02", 760.0);

        achievementService.evaluateAchievements("2025-02");

        assertTrue(achievementsOf(alpha, AchievementType.BIG_SAVER).isEmpty(),
                "A reduction due to the shorter month should not count");
    }

    @Test
    @Transactional
    void greenStreak_grantsThresholdThreeForThreeMonthsOfReduction() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2025-02", 100, 95, 90, 85);

        achievementService.evaluateAchievements("2025-05");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.GREEN_STREAK);
        assertEquals(1, awards.size(), TEAM_ALPHA + " (3 reductions in a row) should get a Green Streak");
        assertEquals(3, awards.getFirst().threshold);
    }

    @Test
    @Transactional
    void greenStreak_grantsEachThresholdOncePerStreak() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2025-01", 100, 95, 90, 85, 80, 75, 70, 65);

        evaluate("2025-02", "2025-08");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.GREEN_STREAK);
        assertEquals(List.of("2025-07", "2025-04"), awards.stream().map(a -> a.period).toList(),
                "Each threshold should be granted once, when it is reached");
        assertEquals(List.of(6, 3), awards.stream().map(a -> a.threshold).toList());
    }

    @Test
    @Transactional
    void greenStreak_canBeEarnedAgainAfterTheStreakEnded() {
        Team alpha = team(TEAM_ALPHA);
        // The increase in 2025-05 ends the first streak
        createMonths(alpha, "2025-01", 100, 95, 90, 85, 90, 85, 80, 75);

        evaluate("2025-02", "2025-08");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.GREEN_STREAK);
        assertEquals(List.of("2025-08", "2025-04"), awards.stream().map(a -> a.period).toList(),
                "A new streak should earn the threshold again");
        assertEquals(List.of(3, 3), awards.stream().map(a -> a.threshold).toList());
    }

    @Test
    @Transactional
    void greenStreak_reductionsBelowTheMinimumDoNotCount() {
        Team alpha = team(TEAM_ALPHA);
        // About 1% less per month
        createMonths(alpha, "2025-02", 100, 99, 98, 97);

        achievementService.evaluateAchievements("2025-05");

        assertTrue(achievementsOf(alpha, AchievementType.GREEN_STREAK).isEmpty(),
                "Reductions within the noise should not count");
    }

    @Test
    @Transactional
    void greenStreak_gapInHistoryEndsStreak() {
        Team alpha = team(TEAM_ALPHA);
        // 2025-03 is missing, so only the reduction in 2025-05 counts
        createMonths(alpha, "2025-01", 100, 95);
        createMonths(alpha, "2025-04", 90, 85);

        achievementService.evaluateAchievements("2025-05");

        assertTrue(achievementsOf(alpha, AchievementType.GREEN_STREAK).isEmpty(),
                "Reductions across a gap should not count as consecutive");
    }

    @Test
    @Transactional
    void greenStreak_ignoresLaterPeriods() {
        Team alpha = team(TEAM_ALPHA);
        // Only 2 reductions up to 2025-04, the third one follows in 2025-05
        createMonths(alpha, "2025-02", 100, 95, 90, 85);

        achievementService.evaluateAchievements("2025-04");

        assertTrue(achievementsOf(alpha, AchievementType.GREEN_STREAK).isEmpty(),
                "Reductions after the evaluated period should not count");
    }

    @Test
    @Transactional
    void personalBest_grantedForTheLowestDailyAverageSinceTrackingBegan() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2025-01", 100, 110, 105, 100, 108, 102, 90);

        achievementService.evaluateAchievements("2025-07");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.PERSONAL_BEST);
        assertEquals(1, awards.size(), TEAM_ALPHA + " (10% below its best) should get a Personal Best");
        assertEquals(10.0, awards.getFirst().measuredValue, 0.001);
    }

    @Test
    @Transactional
    void personalBest_requiresSixEarlierMonths() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2025-01", 100, 110, 105, 100, 108, 90);

        achievementService.evaluateAchievements("2025-06");

        assertTrue(achievementsOf(alpha, AchievementType.PERSONAL_BEST).isEmpty(),
                "A short history should not make every reduction a record");
    }

    @Test
    @Transactional
    void personalBest_requiresAClearImprovement() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2025-01", 100, 110, 105, 100, 108, 102, 97);

        achievementService.evaluateAchievements("2025-07");

        assertTrue(achievementsOf(alpha, AchievementType.PERSONAL_BEST).isEmpty(),
                "3% below the previous best should not count");
    }

    @Test
    @Transactional
    void annualImprover_comparesCalendarYears() {
        Team alpha = team(TEAM_ALPHA);
        createYear(alpha, 2023, 100);
        createYear(alpha, 2024, 70);

        achievementService.evaluateAchievements("2024-12");

        List<Achievement> awards = achievementsOf(alpha, AchievementType.ANNUAL_IMPROVER);
        assertEquals(1, awards.size(), TEAM_ALPHA + " (30% less) should be an Annual Improver");
        assertEquals(25, awards.getFirst().threshold, "The highest threshold reached should be granted");
        assertEquals(30.0, awards.getFirst().measuredValue, 0.001);
    }

    @Test
    @Transactional
    void annualImprover_requiresEveryMonthOfBothYears() {
        Team alpha = team(TEAM_ALPHA);
        // 2023-06 is missing
        createMonths(alpha, "2023-01", 100, 100, 100, 100, 100);
        createMonths(alpha, "2023-07", 100, 100, 100, 100, 100, 100);
        createYear(alpha, 2024, 70);

        achievementService.evaluateAchievements("2024-12");

        assertTrue(achievementsOf(alpha, AchievementType.ANNUAL_IMPROVER).isEmpty(),
                "Incomplete years should not be compared");
    }

    @Test
    @Transactional
    void annualImprover_onlyGrantedForDecember() {
        Team alpha = team(TEAM_ALPHA);
        createYear(alpha, 2023, 100);
        createYear(alpha, 2024, 70);
        createMonths(alpha, "2025-01", 70);

        achievementService.evaluateAchievements("2025-01");

        assertTrue(achievementsOf(alpha, AchievementType.ANNUAL_IMPROVER).isEmpty(),
                "Years should only be compared once they ended");
    }

    @Test
    @Transactional
    void evaluateAchievements_doesNotDuplicateAchievements() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2025-01", 10, 8);

        achievementService.evaluateAchievements("2025-02");
        achievementService.evaluateAchievements("2025-02");

        assertEquals(1, achievementsOf(alpha, AchievementType.HIGHEST_REDUCTION).size(),
                "Achievement should not be granted twice for the same period");
    }

    @Test
    @Transactional
    void evaluateAchievements_awardsForTheEvaluatedPeriod() {
        Team alpha = team(TEAM_ALPHA);
        createMonths(alpha, "2024-09", 10, 8);

        achievementService.evaluateAchievements("2024-10");

        assertEquals(List.of("2024-10"), periodsOf(alpha, AchievementType.HIGHEST_REDUCTION),
                "The achievement should carry the evaluated period, not the time of evaluation");
    }

    @Test
    @Transactional
    void evaluateAchievements_teamWithoutTotalIsNotAwarded() {
        Team alpha = team(TEAM_ALPHA);
        Team gamma = team(TEAM_GAMMA);
        createEntry(gamma, "2025-01", 1000.0);
        createEntry(gamma, "2025-02", null);
        createMonths(alpha, "2025-01", 10, 8);

        achievementService.evaluateAchievements("2025-02");

        assertTrue(Achievement.findByTeam(gamma).isEmpty(), "Missing data should not count as a reduction");
        assertFalse(Achievement.findByTeam(alpha).isEmpty(), "The other teams should still be evaluated");
    }

    @Test
    void criteria_describesEveryRuleBasedTypeWithItsThresholds() {
        for (AchievementType type : AchievementType.values()) {
            if (type != AchievementType.CUSTOM) {
                String criteria = AchievementService.criteria(type);
                assertNotNull(criteria, type + " should have criteria");
                assertFalse(criteria.isBlank(), type + " should have criteria");
            }
        }
        assertEquals("At least 20% less CO₂e per day than on average over the previous 3 months.",
                AchievementService.criteria(AchievementType.BIG_SAVER));
        assertEquals("3, 6 or 12 months in a row with at least 2% less CO₂e per day than in the month before.",
                AchievementService.criteria(AchievementType.GREEN_STREAK));
        assertEquals("At least 10, 25 or 50% less CO₂e per day over a calendar year than over the year before.",
                AchievementService.criteria(AchievementType.ANNUAL_IMPROVER));
    }

    @Test
    void tier_countsTheThresholdsReached() {
        assertEquals(1, AchievementService.tier(AchievementType.GREEN_STREAK, 3));
        assertEquals(2, AchievementService.tier(AchievementType.GREEN_STREAK, 6));
        assertEquals(3, AchievementService.tier(AchievementType.GREEN_STREAK, 12));
        assertEquals(2, AchievementService.tier(AchievementType.ANNUAL_IMPROVER, 25));
        assertEquals(2, AchievementService.tier(AchievementType.GREEN_STREAK, 9),
                "A streak between two of the current thresholds should count the ones it reaches");
    }

    @Test
    void tier_isNullWithoutThresholds() {
        assertNull(AchievementService.tier(AchievementType.GREEN_STREAK, null));
        assertNull(AchievementService.tier(AchievementType.GREEN_STREAK, 2),
                "A streak below the lowest threshold reaches no tier");
        assertNull(AchievementService.tier(AchievementType.BIG_SAVER, null));
        assertNull(AchievementService.tier(AchievementType.CUSTOM, null));
    }

    @Test
    void criteria_isNullForCustomAchievements() {
        assertNull(AchievementService.criteria(AchievementType.CUSTOM),
                "Custom achievements are described per award by an admin");
    }

    @Test
    @Transactional
    void grantCustomAchievement_persistsAchievement() {
        Team alpha = team(TEAM_ALPHA);
        AwardedAchievement achievement = achievementService.grantCustomAchievement(
                alpha.id, "Special Award", "Great teamwork!", "🎉").orElseThrow();

        assertNotNull(achievement.id());
        assertEquals("Special Award", achievement.title());
        assertEquals(AchievementType.CUSTOM, achievement.type());
        assertEquals(Periods.current(), achievement.period(),
                "A custom achievement should be awarded in the current period");
    }

    @Test
    void grantCustomAchievement_isEmptyForAnUnknownTeam() {
        assertTrue(achievementService.grantCustomAchievement(-1L, "Special Award", null, null).isEmpty());
    }

    /** Evaluates every period from {@code first} to {@code last}, oldest first, like a sync. */
    private void evaluate(String first, String last) {
        for (YearMonth month = Periods.toYearMonth(first);
             !month.isAfter(Periods.toYearMonth(last));
             month = month.plusMonths(1)) {
            achievementService.evaluateAchievements(month.toString());
        }
    }

    private static Team team(String name) {
        Team team = Team.findByName(name);
        assertNotNull(team, name + " should be configured");
        return team;
    }

    private static List<Achievement> achievementsOf(Team team, AchievementType type) {
        return Achievement.findByTeam(team).stream()
                .filter(a -> a.type == type)
                .toList();
    }

    /** The periods {@code team} has been granted {@code type} for, latest first. */
    private static List<String> periodsOf(Team team, AchievementType type) {
        return achievementsOf(team, type).stream()
                .map(a -> a.period)
                .toList();
    }

    /**
     * Creates entries for consecutive months from {@code firstPeriod} on, with the given
     * daily averages, so that the expected reductions do not depend on month lengths.
     */
    private void createMonths(Team team, String firstPeriod, double... co2KgPerDay) {
        YearMonth month = Periods.toYearMonth(firstPeriod);
        for (double perDay : co2KgPerDay) {
            createEntry(team, month.toString(), perDay * month.lengthOfMonth());
            month = month.plusMonths(1);
        }
    }

    private void createYear(Team team, int year, double co2KgPerDay) {
        double[] months = new double[12];
        Arrays.fill(months, co2KgPerDay);
        createMonths(team, year + "-01", months);
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
