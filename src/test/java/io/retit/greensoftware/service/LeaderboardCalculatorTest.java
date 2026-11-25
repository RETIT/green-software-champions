package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.service.LeaderboardCalculator.TeamEmissions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LeaderboardCalculatorTest {

    /** 31 days, like the month before. */
    private static final String PERIOD = "2026-08";
    private static final String PREVIOUS = "2026-07";

    @Test
    void leaderboard_ranksTheLowestEmissionsFirst() {
        List<RankedTeam> leaderboard = calculator(
                team(1, Map.of(PREVIOUS, total(50))),
                team(2, Map.of(PERIOD, total(null))),
                team(3, Map.of(PERIOD, total(300))),
                team(4, Map.of(PERIOD, total(100))),
                team(5, Map.of(PERIOD, total(0))))
                .leaderboard(Map.of()).teams();

        assertEquals(List.of(5L, 4L, 3L, 2L, 1L), leaderboard.stream().map(e -> e.team().id()).toList(),
                "Teams without emissions should be at the bottom, those without an entry last");
        assertEquals(List.of(1, 2, 3, 4, 5), leaderboard.stream().map(RankedTeam::rank).toList());
    }

    @Test
    void leaderboard_comparesWithTheCompanyAndThePreviousPeriod() {
        RankedTeam entry = calculator(
                team(1, Map.of(PREVIOUS, total(100), PERIOD, total(75))),
                team(2, Map.of(PERIOD, total(225))))
                .leaderboard(Map.of()).teams().getFirst();

        assertEquals(75, entry.emissions().totalCo2Kg());
        assertEquals(100, entry.previousCo2Kg());
        assertEquals(25, entry.reductionPercent(), 1e-9);
        assertEquals(25, entry.companySharePercent(), 1e-9);
    }

    @Test
    void leaderboard_withoutDataForThePeriod_hasOnlyTheHistory() {
        RankedTeam entry = calculator(team(1, Map.of(PREVIOUS, total(100))))
                .leaderboard(Map.of()).teams().getFirst();

        assertNull(entry.emissions().totalCo2Kg());
        assertNull(entry.previousCo2Kg());
        assertNull(entry.reductionPercent());
        assertNull(entry.companySharePercent());
        assertEquals(List.of(PREVIOUS, PERIOD), entry.history().stream().map(HistoryPoint::period).toList());
        assertEquals(100, entry.history().getFirst().emissions().totalCo2Kg());
        assertNull(entry.history().getLast().emissions().totalCo2Kg());
    }

    @Test
    void leaderboard_withoutEmissions_hasNoShares() {
        assertNull(calculator(team(1, Map.of(PERIOD, total(0)))).leaderboard(Map.of())
                .teams().getFirst().companySharePercent());
    }

    @Test
    void leaderboard_assignsTheAchievements() {
        List<AwardedAchievement> achievements = List.of(new AwardedAchievement(
                1L, AchievementType.CUSTOM, "Hero", null, null, null, PERIOD, null, null, null));
        List<RankedTeam> leaderboard = calculator(
                team(1, Map.of(PERIOD, total(1))), team(2, Map.of(PERIOD, total(2))))
                .leaderboard(Map.of(2L, achievements)).teams();

        assertEquals(List.of(), leaderboard.getFirst().achievements());
        assertSame(achievements, leaderboard.getLast().achievements());
    }

    @Test
    void summary_comparesOnlyTeamsWithDataForThePeriod() {
        CompanySummary summary = calculator(
                team(1, Map.of(PREVIOUS, total(100), PERIOD, total(90))),
                team(2, Map.of(PREVIOUS, total(500))),
                team(3, Map.of(PERIOD, total(10))))
                .summary();

        assertEquals(PERIOD, summary.period());
        assertEquals(100, summary.totalCo2Kg());
        assertEquals(100, summary.previousTotalCo2Kg());
        assertEquals(0, summary.reductionPercent(), 1e-9);
        assertEquals(3, summary.teamCount());
    }

    @Test
    void summary_addsUpTheHistoryPerProvider() {
        CompanySummary summary = calculator(
                team(1, Map.of(PERIOD, Emissions.ofProviders(1.0, null, 2.0))),
                team(2, Map.of(PERIOD, Emissions.ofProviders(10.0, null, null))))
                .summary();

        Emissions current = summary.history().getLast().emissions();
        assertEquals(13, current.totalCo2Kg());
        assertEquals(11, current.awsCo2Kg());
        assertNull(current.azureCo2Kg());
        assertEquals(2, current.gcpCo2Kg());
        assertNull(summary.history().getFirst().emissions().totalCo2Kg(), "No data rather than zero emissions");
    }

    @Test
    void summary_withoutPreviousData_hasNoReduction() {
        CompanySummary summary = calculator(team(1, Map.of(PERIOD, total(10)))).summary();
        assertNull(summary.previousTotalCo2Kg());
        assertNull(summary.reductionPercent());
    }

    private static LeaderboardCalculator calculator(TeamEmissions... teams) {
        return new LeaderboardCalculator(PERIOD, List.of(PREVIOUS, PERIOD), List.of(teams));
    }

    private static TeamEmissions team(long id, Map<String, Emissions> byPeriod) {
        return new TeamEmissions(new TeamInfo(id, "Team " + id, "#123456", "🌱"), byPeriod);
    }

    private static Emissions total(Integer co2Kg) {
        return Emissions.ofProviders(co2Kg != null ? co2Kg.doubleValue() : null, null, null);
    }
}
