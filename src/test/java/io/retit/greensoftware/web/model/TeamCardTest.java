package io.retit.greensoftware.web.model;

import io.retit.greensoftware.achievement.AchievementType;
import io.retit.greensoftware.achievement.AwardedAchievement;
import io.retit.greensoftware.emissions.Emissions;
import io.retit.greensoftware.leaderboard.HistoryPoint;
import io.retit.greensoftware.leaderboard.RankedTeam;
import io.retit.greensoftware.team.TeamInfo;
import io.retit.greensoftware.web.format.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeamCardTest {

    @Test
    void showsTheTeamAndItsEmissions() {
        TeamCard card = TeamCard.of(new RankedTeam(2, new TeamInfo(1L, "Alpha", "#4CAF50", "🌱"),
                Emissions.ofProviders(1.0, 20.0, 3000.0), 4000.0, 24.5, 12.345,
                List.of(), List.of(new HistoryPoint("2026-03", Emissions.ofProviders(1.0, null, 2.5)))));

        assertEquals("rank-2", card.rankClass());
        assertEquals("Alpha", card.name());
        assertEquals("3.02 t", card.total());
        assertEquals(new TeamCard.Reduction(Trend.POSITIVE, "↓ 24.5%"), card.reduction());
        assertEquals(new TeamCard.Share("12.3%", "12.35%", 12.345), card.share());
        assertEquals(new TeamCard.Providers("1.0 kg", "20 kg", "3.00 t"), card.providers());
        assertEquals(new Chart("""
                [{"period":"2026-03","totalCo2Kg":3.5,"awsCo2Kg":1.0,"gcpCo2Kg":2.5}]""", "March 2026"), card.chart());
    }

    @Test
    void withoutData_showsThatItIsMissing() {
        TeamCard card = TeamCard.of(new RankedTeam(4, new TeamInfo(1L, "Alpha", "#4CAF50", null),
                Emissions.NONE, null, null, null,
                List.of(), List.of(new HistoryPoint("2026-02", Emissions.NONE), new HistoryPoint("2026-03", Emissions.NONE))));

        assertEquals("", card.rankClass());
        assertEquals("🌍", card.icon());
        assertEquals("—", card.total());
        assertEquals(new TeamCard.Reduction(Trend.NEUTRAL, "—"), card.reduction());
        assertEquals(new TeamCard.Share("—", "—", 0), card.share());
        assertEquals(new TeamCard.Providers("—", "—", "—"), card.providers());
        assertNull(card.chart());
    }

    @Test
    void chart_ofSeveralMonths_leavesOutMissingValues() {
        Chart chart = Chart.of(List.of(
                new HistoryPoint("2026-02", Emissions.NONE),
                new HistoryPoint("2026-03", Emissions.ofProviders(null, 2.0, null))));

        assertEquals("Last 2 Months", chart.label());
        assertEquals("""
                [{"period":"2026-02"},{"period":"2026-03","totalCo2Kg":2.0,"azureCo2Kg":2.0}]""", chart.points());
    }

    @Test
    void collapsedCard_showsTheLatestThreeBadges() {
        TeamCard.Badges badges = TeamCard.Badges.of(Stream.of("A", "B", "C", "D", "E")
                .map(title -> new AwardedAchievement(1L, AchievementType.CUSTOM, title, null, null, "🧹",
                        "2026-03", null, null, null))
                .toList());

        assertEquals(5, badges.all().size());
        assertEquals(List.of("A", "B", "C"), badges.top().stream().map(Badge::label).toList());
        assertEquals(2, badges.more());
        assertEquals("D,\nE", badges.moreLabels());
    }
}
