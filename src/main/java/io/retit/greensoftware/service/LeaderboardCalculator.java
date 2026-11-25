package io.retit.greensoftware.service;

import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.util.Reductions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Ranks the teams in {@code period} and summarizes their emissions, the same way for the
 * stored data and the demo data.
 *
 * @param historyPeriods the periods of the charts, oldest first
 * @param teams          all teams, also those without data for {@code period}
 */
record LeaderboardCalculator(String period, List<String> historyPeriods, List<TeamEmissions> teams) {

    /** @param byPeriod at least of the history, the calculator's period and the one before */
    record TeamEmissions(TeamInfo team, Map<String, Emissions> byPeriod) {

        boolean hasDataIn(String period) {
            return byPeriod.containsKey(period);
        }

        Emissions in(String period) {
            return byPeriod.getOrDefault(period, Emissions.NONE);
        }

        /** Null without data. */
        Double totalIn(String period) {
            return in(period).totalCo2Kg();
        }
    }

    /** Lowest emissions rank first, teams without data for the period at the bottom. */
    Leaderboard leaderboard(Map<Long, List<AwardedAchievement>> achievementsByTeam) {
        double companyTotal = companyTotal();
        List<TeamEmissions> ranked = teams.stream()
                .sorted(Comparator.comparing((TeamEmissions team) -> !team.hasDataIn(period))
                        .thenComparing(team -> team.totalIn(period), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<RankedTeam> result = new ArrayList<>();
        for (TeamEmissions team : ranked) {
            result.add(rank(result.size() + 1, team, companyTotal,
                    achievementsByTeam.getOrDefault(team.team().id(), List.of())));
        }
        return new Leaderboard(period, result);
    }

    CompanySummary summary() {
        double total = companyTotal();
        // Only the teams with data for this period, so the comparison is like for like
        double previousTotal = teams.stream()
                .filter(team -> team.hasDataIn(period))
                .map(team -> team.totalIn(Periods.previous(period)))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        return new CompanySummary(period, total,
                previousTotal > 0 ? previousTotal : null,
                Reductions.fromPrevious(period, previousTotal, total),
                teams.size(),
                historyPeriods.stream()
                        .map(p -> new HistoryPoint(p, companyEmissions(p)))
                        .toList());
    }

    private double companyTotal() {
        return teams.stream()
                .map(team -> team.totalIn(period))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private RankedTeam rank(int rank, TeamEmissions team, double companyTotal,
                            List<AwardedAchievement> achievements) {
        Double total = team.totalIn(period);
        Double previous = team.hasDataIn(period) ? team.totalIn(Periods.previous(period)) : null;
        return new RankedTeam(rank, team.team(), team.in(period),
                previous,
                Reductions.fromPrevious(period, previous, total),
                companyTotal > 0 && total != null ? total / companyTotal * 100.0 : null,
                achievements,
                historyPeriods.stream()
                        .map(p -> new HistoryPoint(p, team.in(p)))
                        .toList());
    }

    private Emissions companyEmissions(String historyPeriod) {
        List<Emissions> emissions = teams.stream()
                .map(team -> team.in(historyPeriod))
                .toList();
        return new Emissions(
                sumOrNull(emissions, Emissions::totalCo2Kg),
                sumOrNull(emissions, Emissions::awsCo2Kg),
                sumOrNull(emissions, Emissions::azureCo2Kg),
                sumOrNull(emissions, Emissions::gcpCo2Kg));
    }

    /** Sums the non-null values of {@code field}, or returns null if there are none. */
    private static Double sumOrNull(List<Emissions> emissions, Function<Emissions, Double> field) {
        return emissions.stream()
                .map(field)
                .filter(Objects::nonNull)
                .reduce(Double::sum)
                .orElse(null);
    }
}
