package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.AchievementType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDataServiceTest {

    private static final String PERIOD = "2026-01";

    private final DemoDataService demoDataService = new DemoDataService();

    @Test
    void dashboard_isOfThePeriodAndConsistent() {
        Dashboard dashboard = demoDataService.getDashboard(PERIOD);

        assertEquals(PERIOD, dashboard.period());
        assertFalse(dashboard.isEmpty());
        assertEquals(13, dashboard.summary().history().size());
        assertEquals(PERIOD, dashboard.summary().history().getLast().period());
        List<RankedTeam> teams = dashboard.leaderboard().teams();
        assertEquals(100, teams.stream().mapToDouble(RankedTeam::companySharePercent).sum(), 1e-9);
        assertTrue(teams.stream().allMatch(team -> team.reductionPercent() != null));
    }

    @Test
    void dashboard_isAlwaysTheSame() {
        assertEquals(totals(), totals());
    }

    @Test
    void achievements_areRelativeToThePeriod() {
        RankedTeam alpha = demoDataService.getDashboard(PERIOD).leaderboard().teams().stream()
                .filter(team -> team.team().name().equals("Team Alpha"))
                .findFirst().orElseThrow();

        assertEquals(List.of("2026-01", "2026-01", "2025-12", "2025-10"),
                alpha.achievements().stream().map(AwardedAchievement::period).toList());
        AwardedAchievement streak = alpha.achievements().get(1);
        assertEquals(AchievementType.GREEN_STREAK, streak.type());
        assertEquals(2, streak.tier());
    }

    private List<Double> totals() {
        return demoDataService.getDashboard(PERIOD).leaderboard().teams().stream()
                .map(team -> team.emissions().totalCo2Kg())
                .toList();
    }
}
