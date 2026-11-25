package io.retit.greensoftware.leaderboard;

import io.retit.greensoftware.achievement.AwardedAchievement;
import io.retit.greensoftware.emissions.Emissions;
import io.retit.greensoftware.team.TeamInfo;

import java.util.List;

/**
 * A team's place in the {@link Leaderboard}.
 *
 * @param emissions           {@link Emissions#NONE} without data, like the next three null
 * @param previousCo2Kg       the total of the previous period
 * @param reductionPercent    vs. the previous period, based on daily averages; positive = reduction
 * @param companySharePercent 0 to 100
 * @param achievements        latest first
 * @param history             up to and including the leaderboard's period, oldest first
 */
public record RankedTeam(
        int rank, TeamInfo team, Emissions emissions,
        Double previousCo2Kg, Double reductionPercent, Double companySharePercent,
        List<AwardedAchievement> achievements, List<HistoryPoint> history
) {
}
