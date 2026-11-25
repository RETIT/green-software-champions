package io.retit.greensoftware.web.model;

import io.retit.greensoftware.leaderboard.HistoryPoint;
import io.retit.greensoftware.web.format.Format;
import io.retit.greensoftware.web.format.Json;

import java.util.List;

/**
 * The history chart of a team, which a script draws.
 *
 * @param points all months as JSON, see {@link Point}
 * @param label  which months it shows
 */
public record Chart(String points, String label) {

    /** A month, like {@code HistoryPoint} in types.ts. Values are left out without data. */
    public record Point(String period, Double totalCo2Kg, Double awsCo2Kg, Double azureCo2Kg, Double gcpCo2Kg) {

        static Point of(HistoryPoint point) {
            return new Point(point.period(), point.emissions().totalCo2Kg(),
                    point.emissions().awsCo2Kg(), point.emissions().azureCo2Kg(), point.emissions().gcpCo2Kg());
        }
    }

    /** Null without any data. */
    static Chart of(List<HistoryPoint> history) {
        if (history.stream().allMatch(point -> point.emissions().totalCo2Kg() == null)) {
            return null;
        }
        return new Chart(
                Json.write(history.stream().map(Point::of).toList()),
                history.size() == 1
                        ? Format.period(history.getFirst().period())
                        : "Last " + history.size() + " Months");
    }
}
