package io.retit.greensoftware.leaderboard;

import java.util.List;

/**
 * Company-wide emissions in {@code period}.
 *
 * @param previousTotalCo2Kg only of the teams with data for {@code period}, so the comparison
 *                           is like for like; null without any
 * @param reductionPercent   vs. the previous period, based on daily averages; positive = reduction
 * @param history            up to and including {@code period}, oldest first
 */
public record CompanySummary(
        String period, double totalCo2Kg, Double previousTotalCo2Kg, Double reductionPercent,
        int teamCount, List<HistoryPoint> history
) {
}
