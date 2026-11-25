package io.retit.greensoftware.web.model;

import io.retit.greensoftware.service.RankedTeam;
import io.retit.greensoftware.service.TeamInfo;
import io.retit.greensoftware.web.format.Format;
import io.retit.greensoftware.web.format.TeamColor;
import io.retit.greensoftware.web.format.Trend;

import java.util.List;

/**
 * A team on the world map.
 *
 * @param rank   links the bubble to its tooltip
 * @param r      the radius, which grows with the team's share of the emissions
 * @param labelY where the team's name is, below the bubble
 * @param icon   in the bubble
 * @param pulse  whether the bubble is animated, only the best team's is
 */
public record MapBubble(
        int rank, int x, int y, double r, double labelY,
        String name, String icon, String color, boolean pulse,
        String total, String share, Trend trend, String reduction, List<Badge> badges
) {

    private record Position(int x, int y) {}

    /** In the map's viewBox of 960 × 500, assigned by rank. */
    private static final List<Position> REGIONS = List.of(
            new Position(760, 200),
            new Position(680, 165),
            new Position(460, 170),
            new Position(530, 175),
            new Position(780, 290),
            new Position(680, 350),
            new Position(820, 150),
            new Position(120, 200),
            new Position(200, 250),
            new Position(480, 330));

    private static final double MIN_RADIUS = 12;
    private static final double MAX_RADIUS = 50;
    private static final double LABEL_DISTANCE = 13;

    public static MapBubble of(RankedTeam ranked) {
        TeamInfo team = ranked.team();
        int index = ranked.rank() - 1;
        Position position = REGIONS.get(index % REGIONS.size());
        double r = radius(ranked.companySharePercent());
        return new MapBubble(
                ranked.rank(), position.x(), position.y(), r, position.y() + r + LABEL_DISTANCE,
                team.name(),
                team.icon() == null || team.icon().isEmpty() ? String.valueOf(ranked.rank()) : team.icon(),
                TeamColor.cssColor(team.color()),
                index == 0,
                Format.co2(ranked.emissions().totalCo2Kg()),
                Format.percent(ranked.companySharePercent()),
                Trend.of(ranked.reductionPercent()),
                Format.reduction(ranked.reductionPercent()),
                AchievementGroups.badges(ranked.achievements()));
    }

    private static double radius(Double sharePercent) {
        double share = Format.isFinite(sharePercent) ? sharePercent / 100 : 0;
        return Format.round(MIN_RADIUS + share * (MAX_RADIUS - MIN_RADIUS), 2).doubleValue();
    }
}
