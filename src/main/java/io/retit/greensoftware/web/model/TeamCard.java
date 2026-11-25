package io.retit.greensoftware.web.model;

import io.retit.greensoftware.service.AwardedAchievement;
import io.retit.greensoftware.service.Emissions;
import io.retit.greensoftware.service.RankedTeam;
import io.retit.greensoftware.service.TeamInfo;
import io.retit.greensoftware.web.format.Format;
import io.retit.greensoftware.web.format.TeamColor;
import io.retit.greensoftware.web.format.Trend;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A team in the leaderboard.
 *
 * @param chart null without any data
 */
public record TeamCard(
        int rank, String name, String icon, String color, Badges badges, Sparkline sparkline,
        String total, Reduction reduction, Share share, Providers providers, Chart chart
) {

    private static final String DEFAULT_ICON = "🌍";

    /**
     * @param all  shown in the expanded card
     * @param top  the latest ones, shown in the collapsed card
     * @param more how many the collapsed card leaves out, with their labels as {@code moreLabels}
     */
    public record Badges(List<Badge> all, List<Badge> top, int more, String moreLabels) {

        private static final int TOP = 3;

        static Badges of(List<AwardedAchievement> achievements) {
            List<Badge> all = AchievementGroups.badges(achievements);
            List<Badge> top = all.subList(0, Math.min(all.size(), TOP));
            List<Badge> more = all.subList(top.size(), all.size());
            return new Badges(all, top, more.size(),
                    more.stream().map(Badge::label).collect(Collectors.joining(",\n")));
        }
    }

    public record Reduction(Trend trend, String text) {

        static Reduction of(Double percent) {
            return new Reduction(Trend.of(percent), Format.reduction(percent));
        }
    }

    /** @param barWidth 0 to 100 */
    public record Share(String text, String preciseText, double barWidth) {

        static Share of(Double percent) {
            return new Share(Format.percent(percent), Format.percent(percent, 2),
                    Format.isFinite(percent) ? Math.min(percent, 100) : 0);
        }
    }

    public record Providers(String aws, String azure, String gcp) {

        static Providers of(Emissions emissions) {
            return new Providers(
                    Format.co2(emissions.awsCo2Kg()),
                    Format.co2(emissions.azureCo2Kg()),
                    Format.co2(emissions.gcpCo2Kg()));
        }
    }

    public static TeamCard of(RankedTeam ranked) {
        TeamInfo team = ranked.team();
        return new TeamCard(
                ranked.rank(),
                team.name(),
                team.icon() == null || team.icon().isEmpty() ? DEFAULT_ICON : team.icon(),
                TeamColor.cssColor(team.color()),
                Badges.of(ranked.achievements()),
                Sparkline.of(team.name() + " CO₂e over the last " + ranked.history().size() + " months",
                        ranked.history()),
                Format.co2(ranked.emissions().totalCo2Kg()),
                Reduction.of(ranked.reductionPercent()),
                Share.of(ranked.companySharePercent()),
                Providers.of(ranked.emissions()),
                Chart.of(ranked.history()));
    }

    /** The class that highlights the first three ranks. */
    public String rankClass() {
        return rank <= 3 ? "rank-" + rank : "";
    }
}
