package io.retit.greensoftware.achievement;

import io.retit.greensoftware.emissions.CarbonEntry;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.emissions.Reductions;
import io.retit.greensoftware.event.DashboardDataChanged;
import io.retit.greensoftware.team.Team;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Month;
import java.time.YearMonth;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Awards the rule-based achievements of a period. Emissions are compared by their daily
 * averages, see {@link Reductions}. Each achievement is granted at most once per team
 * and period, so teams can earn it again in later periods.
 */
@ApplicationScoped
public class AchievementService {

    private static final Logger LOG = Logger.getLogger(AchievementService.class);

    /**
     * Reduction Champion: teams below this share of the company's emissions in the
     * previous month do not compete, as small footprints swing by large percentages.
     */
    private static final double HIGHEST_REDUCTION_MIN_SHARE_PERCENT = 1.0;

    /** Big Saver: the number of previous months the baseline averages over. */
    private static final int BIG_SAVER_BASELINE_MONTHS = 3;

    /** Big Saver: the minimum reduction vs. the baseline in percent. */
    private static final double BIG_SAVER_MIN_PERCENT = 20.0;

    /** Green Streak: the minimum reduction vs. the month before for a month to count, in percent. */
    private static final double GREEN_STREAK_MIN_PERCENT = 2.0;

    /** Green Streak: the thresholds, as streak lengths in months, ascending. */
    private static final List<Integer> GREEN_STREAK_THRESHOLDS = List.of(3, 6, 12);

    /** Personal Best: the minimum number of earlier months with data. */
    private static final int PERSONAL_BEST_MIN_EARLIER_MONTHS = 6;

    /** Personal Best: the minimum reduction vs. the previous best in percent. */
    private static final double PERSONAL_BEST_MIN_PERCENT = 5.0;

    /** Annual Improver: the thresholds, as minimum reductions in percent, ascending. */
    private static final List<Integer> ANNUAL_IMPROVER_THRESHOLDS = List.of(10, 25, 50);

    @Inject
    Event<DashboardDataChanged> dataChanged;

    @Transactional
    public void evaluateAchievements(String period) {
        List<TeamHistory> histories = findHistories(period);
        if (histories.isEmpty()) {
            return;
        }

        checkHighestReduction(histories, period);
        checkMostCo2Saved(histories, period);
        checkBigSaver(histories, period);
        checkGreenStreak(histories, period);
        checkPersonalBest(histories, period);
        if (Periods.toYearMonth(period).getMonth() == Month.DECEMBER) {
            checkAnnualImprover(histories, period);
        }
    }

    /**
     * A team's entries by period, only up to the evaluated one, so that evaluating an
     * older period ignores data it could not know about.
     */
    private record TeamHistory(Team team, NavigableMap<String, CarbonEntry> entries) {

        Double perDay(String period) {
            return Reductions.perDay(entries.get(period));
        }

        /** The daily average over all of {@code periods}, or null if any lacks data. */
        Double perDayOver(List<String> periods) {
            double co2Kg = 0;
            int days = 0;
            for (String period : periods) {
                CarbonEntry entry = entries.get(period);
                if (entry == null || entry.totalCo2Kg == null) {
                    return null;
                }
                co2Kg += entry.totalCo2Kg;
                days += Periods.lengthInDays(period);
            }
            return co2Kg / days;
        }

        /** Vs. the month before, in percent, or null. */
        Double reduction(String period) {
            return Reductions.percent(perDay(Periods.previous(period)), perDay(period));
        }
    }

    /** A team's value in a ranking, e.g., its reduction. */
    private record Ranked(Team team, double value) {}

    /** The histories up to {@code period} of all teams with an entry for {@code period}. */
    private static List<TeamHistory> findHistories(String period) {
        Map<Long, TeamHistory> byTeamId = new LinkedHashMap<>();
        for (CarbonEntry entry : CarbonEntry.findUpTo(period)) {
            byTeamId.computeIfAbsent(entry.team.id, id -> new TeamHistory(entry.team, new TreeMap<>()))
                    .entries()
                    .put(entry.period, entry);
        }
        return byTeamId.values().stream()
                .filter(history -> history.entries().containsKey(period))
                .toList();
    }

    /**
     * Reduction Champion: the largest reduction vs. the previous month in percent, among
     * the teams with at least {@value #HIGHEST_REDUCTION_MIN_SHARE_PERCENT} % of the
     * company's emissions in the previous month.
     */
    private void checkHighestReduction(List<TeamHistory> histories, String period) {
        String previous = Periods.previous(period);
        double companyPerDay = histories.stream()
                .map(history -> history.perDay(previous))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        double minPerDay = companyPerDay * HIGHEST_REDUCTION_MIN_SHARE_PERCENT / 100.0;
        histories.stream()
                .filter(history -> history.perDay(previous) != null && history.perDay(previous) >= minPerDay)
                .filter(history -> history.reduction(period) != null)
                .map(history -> new Ranked(history.team(), history.reduction(period)))
                .filter(ranked -> ranked.value() > 0)
                .max(Comparator.comparingDouble(Ranked::value))
                .ifPresent(best -> grant(best.team(), AchievementType.HIGHEST_REDUCTION, period,
                        null, best.value()));
    }

    /**
     * Most CO₂e Saved: the largest saving in kg vs. the previous month's daily rate over
     * the days of {@code period}. Favors large teams, as Reduction Champion favors small
     * ones.
     */
    private void checkMostCo2Saved(List<TeamHistory> histories, String period) {
        String previous = Periods.previous(period);
        int days = Periods.lengthInDays(period);
        histories.stream()
                .filter(history -> history.perDay(previous) != null && history.perDay(period) != null)
                .map(history -> new Ranked(history.team(),
                        (history.perDay(previous) - history.perDay(period)) * days))
                .filter(ranked -> ranked.value() > 0)
                .max(Comparator.comparingDouble(Ranked::value))
                .ifPresent(best -> grant(best.team(), AchievementType.MOST_CO2_SAVED, period,
                        null, best.value()));
    }

    /**
     * Big Saver: every team at least {@value #BIG_SAVER_MIN_PERCENT} % below its average
     * over the previous {@value #BIG_SAVER_BASELINE_MONTHS} months, which all need data.
     * The longer baseline does not reward returning to normal after a spike.
     */
    private void checkBigSaver(List<TeamHistory> histories, String period) {
        List<String> baseline = Periods.window(Periods.previous(period), BIG_SAVER_BASELINE_MONTHS);
        for (TeamHistory history : histories) {
            Double reduction = Reductions.percent(history.perDayOver(baseline), history.perDay(period));
            if (reduction != null && reduction >= BIG_SAVER_MIN_PERCENT) {
                grant(history.team(), AchievementType.BIG_SAVER, period, null, reduction);
            }
        }
    }

    /**
     * Green Streak: every team at least {@value #GREEN_STREAK_MIN_PERCENT} % below the
     * month before for {@link #GREEN_STREAK_THRESHOLDS} months in a row, at the highest
     * threshold reached. Each threshold is granted once per streak, which a gap in the
     * history or a month without enough reduction ends.
     */
    private void checkGreenStreak(List<TeamHistory> histories, String period) {
        for (TeamHistory history : histories) {
            int length = 0;
            YearMonth month = Periods.toYearMonth(period);
            while (isStreakMonth(history, month.toString())) {
                length++;
                month = month.minusMonths(1);
            }
            Optional<Integer> threshold = highestThreshold(GREEN_STREAK_THRESHOLDS, length);
            if (threshold.isEmpty()) {
                continue;
            }
            String streakStart = month.plusMonths(1).toString();
            boolean earnedInStreak = Achievement.count(
                    "team = ?1 and type = ?2 and period >= ?3 and period <= ?4 and threshold >= ?5",
                    history.team(), AchievementType.GREEN_STREAK, streakStart, period, threshold.get()) > 0;
            if (!earnedInStreak) {
                grant(history.team(), AchievementType.GREEN_STREAK, period, threshold.get(), null);
            }
        }
    }

    private static boolean isStreakMonth(TeamHistory history, String period) {
        Double reduction = history.reduction(period);
        return reduction != null && reduction >= GREEN_STREAK_MIN_PERCENT;
    }

    /**
     * Personal Best: every team at least {@value #PERSONAL_BEST_MIN_PERCENT} % below its
     * lowest earlier month, with data for at least
     * {@value #PERSONAL_BEST_MIN_EARLIER_MONTHS} earlier months, so that a short history
     * does not make every reduction a record.
     */
    private void checkPersonalBest(List<TeamHistory> histories, String period) {
        for (TeamHistory history : histories) {
            List<Double> earlier = history.entries().headMap(period, false).values().stream()
                    .map(Reductions::perDay)
                    .filter(Objects::nonNull)
                    .toList();
            if (earlier.size() < PERSONAL_BEST_MIN_EARLIER_MONTHS) {
                continue;
            }
            Double reduction = Reductions.percent(Collections.min(earlier), history.perDay(period));
            if (reduction != null && reduction >= PERSONAL_BEST_MIN_PERCENT) {
                grant(history.team(), AchievementType.PERSONAL_BEST, period, null, reduction);
            }
        }
    }

    /**
     * Annual Improver: every team whose calendar year ending with {@code december} is
     * {@link #ANNUAL_IMPROVER_THRESHOLDS} % below the previous one, at the highest
     * threshold reached. Requires data for every month of both years. Comparing whole
     * years leaves out seasonal changes, e.g. of the grid's carbon intensity.
     */
    private void checkAnnualImprover(List<TeamHistory> histories, String december) {
        List<String> year = Periods.window(december, 12);
        List<String> previousYear = Periods.window(Periods.toYearMonth(december).minusYears(1).toString(), 12);
        for (TeamHistory history : histories) {
            Double reduction = Reductions.percent(history.perDayOver(previousYear), history.perDayOver(year));
            if (reduction == null) {
                continue;
            }
            highestThreshold(ANNUAL_IMPROVER_THRESHOLDS, reduction).ifPresent(threshold ->
                    grant(history.team(), AchievementType.ANNUAL_IMPROVER, december, threshold, reduction));
        }
    }

    /** The highest of {@code thresholds} that {@code value} reaches, if any. */
    private static Optional<Integer> highestThreshold(List<Integer> thresholds, double value) {
        return thresholds.stream()
                .filter(threshold -> value >= threshold)
                .max(Integer::compare);
    }

    /** Unless the team already has it for {@code period}, e.g. from an earlier evaluation. */
    private void grant(Team team, AchievementType type, String period, Integer threshold, Double measuredValue) {
        boolean alreadyGranted = Achievement.count("team = ?1 and type = ?2 and period = ?3",
                team, type, period) > 0;
        if (alreadyGranted) {
            return;
        }
        Achievement achievement = new Achievement();
        achievement.team = team;
        achievement.type = type;
        achievement.title = type.title;
        achievement.description = describe(type, period, threshold, measuredValue);
        achievement.badgeIcon = type.badgeIcon;
        achievement.period = period;
        achievement.threshold = threshold;
        achievement.measuredValue = measuredValue;
        achievement.persist();
        LOG.infof("Granted achievement '%s' for period %s to team '%s'", type.title, period, team.name);
    }

    /** The description of an achievement of {@code type}, quoting what it was awarded for. */
    private static String describe(AchievementType type, String period, Integer threshold, Double measuredValue) {
        return switch (type) {
            case HIGHEST_REDUCTION -> String.format(Locale.ROOT,
                    "Largest reduction of all teams: %.1f%% less CO₂e per day than in the previous month",
                    measuredValue);
            case MOST_CO2_SAVED -> String.format(Locale.ROOT,
                    "Most CO₂e saved of all teams: %.1f kg less than at the previous month's daily rate",
                    measuredValue);
            case BIG_SAVER -> String.format(Locale.ROOT,
                    "%.1f%% less CO₂e per day than on average over the previous %d months",
                    measuredValue, BIG_SAVER_BASELINE_MONTHS);
            case GREEN_STREAK -> String.format(Locale.ROOT,
                    "%d months in a row with at least %.0f%% less CO₂e per day than in the month before",
                    threshold, GREEN_STREAK_MIN_PERCENT);
            case PERSONAL_BEST -> String.format(Locale.ROOT,
                    "Lowest CO₂e per day since tracking began, %.1f%% below the previous best",
                    measuredValue);
            case ANNUAL_IMPROVER -> {
                int year = Periods.toYearMonth(period).getYear();
                yield String.format(Locale.ROOT, "%.1f%% less CO₂e per day in %d than in %d",
                        measuredValue, year, year - 1);
            }
            case CUSTOM -> throw new IllegalArgumentException("Custom achievements are described by an admin");
        };
    }

    /** What achievements of {@code type} are awarded for; null for {@link AchievementType#CUSTOM}. */
    public static String criteria(AchievementType type) {
        return switch (type) {
            case HIGHEST_REDUCTION -> String.format(Locale.ROOT,
                    "The largest reduction of all teams in CO₂e per day vs. the previous month. "
                            + "Teams below %.0f%% of the company's emissions do not compete.",
                    HIGHEST_REDUCTION_MIN_SHARE_PERCENT);
            case MOST_CO2_SAVED ->
                    "The most CO₂e saved of all teams vs. the previous month's daily rate.";
            case BIG_SAVER -> String.format(Locale.ROOT,
                    "At least %.0f%% less CO₂e per day than on average over the previous %d months.",
                    BIG_SAVER_MIN_PERCENT, BIG_SAVER_BASELINE_MONTHS);
            case GREEN_STREAK -> String.format(Locale.ROOT,
                    "%s months in a row with at least %.0f%% less CO₂e per day than in the month before.",
                    joinThresholds(GREEN_STREAK_THRESHOLDS), GREEN_STREAK_MIN_PERCENT);
            case PERSONAL_BEST -> String.format(Locale.ROOT,
                    "The lowest CO₂e per day since tracking began, at least %.0f%% below the previous "
                            + "best, with data for at least %d earlier months.",
                    PERSONAL_BEST_MIN_PERCENT, PERSONAL_BEST_MIN_EARLIER_MONTHS);
            case ANNUAL_IMPROVER -> String.format(Locale.ROOT,
                    "At least %s%% less CO₂e per day over a calendar year than over the year before.",
                    joinThresholds(ANNUAL_IMPROVER_THRESHOLDS));
            case CUSTOM -> null;
        };
    }

    /**
     * How many of {@code type}'s current thresholds {@code threshold} reaches, e.g. 3 for
     * a 12-month green streak; null if none.
     */
    public static Integer tier(AchievementType type, Integer threshold) {
        if (threshold == null) {
            return null;
        }
        long reached = thresholds(type).stream().filter(t -> threshold >= t).count();
        return reached > 0 ? (int) reached : null;
    }

    private static List<Integer> thresholds(AchievementType type) {
        return switch (type) {
            case GREEN_STREAK -> GREEN_STREAK_THRESHOLDS;
            case ANNUAL_IMPROVER -> ANNUAL_IMPROVER_THRESHOLDS;
            default -> List.of();
        };
    }

    /** {@code thresholds} as text, e.g., "3, 6 or 12". */
    private static String joinThresholds(List<Integer> thresholds) {
        List<String> names = thresholds.stream().map(String::valueOf).toList();
        return names.size() == 1
                ? names.getFirst()
                : String.join(", ", names.subList(0, names.size() - 1)) + " or " + names.getLast();
    }

    /**
     * For the current period. Unlike the rule-based achievements, any number per period.
     * Fires {@link DashboardDataChanged}.
     *
     * @return empty if there is no such team
     */
    @Transactional
    public Optional<AwardedAchievement> grantCustomAchievement(Long teamId, String title, String description,
                                                               String badgeIcon) {
        Team team = Team.findById(teamId);
        if (team == null) {
            return Optional.empty();
        }
        Achievement achievement = new Achievement();
        achievement.team = team;
        achievement.type = AchievementType.CUSTOM;
        achievement.title = title;
        achievement.description = description;
        achievement.badgeIcon = badgeIcon;
        achievement.period = Periods.current();
        achievement.persist();
        dataChanged.fire(new DashboardDataChanged());
        return Optional.of(AwardedAchievement.of(achievement));
    }
}
