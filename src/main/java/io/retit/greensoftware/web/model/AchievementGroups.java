package io.retit.greensoftware.web.model;

import io.retit.greensoftware.achievement.AchievementType;
import io.retit.greensoftware.achievement.AwardedAchievement;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.web.format.Format;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Groups a team's achievements of the same kind into {@link Badge}s. */
final class AchievementGroups {

    private static final String DEFAULT_ICON = "🏅";

    /** The most achievements a tooltip lists, so it stays readable. */
    private static final int MAX_TOOLTIP_ACHIEVEMENTS = 12;

    private static final List<String> NUMERALS =
            List.of("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X");

    private AchievementGroups() {
    }

    /** @param achievements latest first, as the badges then are too */
    static List<Badge> badges(List<AwardedAchievement> achievements) {
        Map<Kind, List<AwardedAchievement>> groups = new LinkedHashMap<>();
        for (AwardedAchievement achievement : achievements) {
            groups.computeIfAbsent(Kind.of(achievement), kind -> new ArrayList<>()).add(achievement);
        }
        return groups.values().stream().map(AchievementGroups::badge).toList();
    }

    /** The type; custom achievements of the same title and icon are also of the same kind. */
    private record Kind(AchievementType type, String title, String badgeIcon) {

        static Kind of(AwardedAchievement achievement) {
            return achievement.isCustom()
                    ? new Kind(AchievementType.CUSTOM, achievement.title(), achievement.badgeIcon())
                    : new Kind(achievement.type(), null, null);
        }
    }

    private static Badge badge(List<AwardedAchievement> group) {
        AwardedAchievement latest = group.getFirst();
        int highestTier = group.stream().mapToInt(AchievementGroups::tier).max().orElse(0);
        // The lower tiers are earned on the way to the highest one, so they are not counted
        int count = (int) group.stream().filter(a -> tier(a) == highestTier).count();
        String tier = highestTier > 0 ? numeral(highestTier) : null;

        String label = latest.title();
        if (tier != null) {
            label += " " + tier;
        }
        if (count > 1) {
            label += " ×" + count;
        }
        String icon = latest.badgeIcon();
        return new Badge(icon == null || icon.isEmpty() ? DEFAULT_ICON : icon, latest.title(), tier, count, label, tooltip(label, group));
    }

    private static int tier(AwardedAchievement achievement) {
        return achievement.tier() != null ? achievement.tier() : 0;
    }

    /** A tier as a Roman numeral, e.g. "III", or as a number beyond ten. */
    private static String numeral(int tier) {
        return tier <= NUMERALS.size() ? NUMERALS.get(tier - 1) : String.valueOf(tier);
    }

    /**
     * The group's label and criteria, then each award's period and value. Custom
     * achievements have no criteria, so their description is shown once if shared,
     * otherwise per award.
     */
    private static String tooltip(String label, List<AwardedAchievement> group) {
        String criteria = group.getFirst().criteria();
        String description = group.getFirst().description();
        boolean sharesDescription = group.stream().allMatch(a -> Objects.equals(a.description(), description));
        String summary = criteria == null && sharesDescription ? description : criteria;

        List<String> lines = new ArrayList<>();
        lines.add(label);
        if (summary != null && !summary.isEmpty()) {
            lines.add(summary);
            lines.add("");
        }
        for (AwardedAchievement achievement : group.subList(0, Math.min(group.size(), MAX_TOOLTIP_ACHIEVEMENTS))) {
            String detail;
            if (criteria != null) {
                detail = detail(achievement);
            } else {
                detail = sharesDescription ? null : achievement.description();
            }
            lines.add(detail != null && !detail.isEmpty() ? period(achievement) + ": " + detail : period(achievement));
        }
        if (group.size() > MAX_TOOLTIP_ACHIEVEMENTS) {
            lines.add("… and " + (group.size() - MAX_TOOLTIP_ACHIEVEMENTS) + " earlier");
        }
        return String.join("\n", lines);
    }

    /** When an achievement was awarded: the year for yearly ones, otherwise the month. */
    private static String period(AwardedAchievement achievement) {
        return achievement.type() == AchievementType.ANNUAL_IMPROVER
                ? String.valueOf(Periods.year(achievement.period()))
                : Format.period(achievement.period());
    }

    /** The tier, if any, and the value, or the description without a value. */
    private static String detail(AwardedAchievement achievement) {
        String value = value(achievement);
        if (value == null) {
            value = achievement.description();
        }
        if (tier(achievement) == 0) {
            return value;
        }
        String numeral = numeral(achievement.tier());
        return value != null && !value.isEmpty() ? numeral + " (" + value + ")" : numeral;
    }

    /** The value awarded for, in short, as the criteria explain it; null if none. */
    private static String value(AwardedAchievement achievement) {
        if (achievement.isCustom()) {
            return null;
        }
        if (achievement.type() == AchievementType.GREEN_STREAK) {
            return achievement.threshold() != null ? achievement.threshold() + " months" : null;
        }
        Double value = achievement.measuredValue();
        if (!Format.isFinite(value)) {
            return null;
        }
        return switch (achievement.type()) {
            case HIGHEST_REDUCTION, BIG_SAVER -> Format.percent(value) + " less";
            case MOST_CO2_SAVED -> Format.co2(value) + " saved";
            case PERSONAL_BEST -> Format.percent(value) + " below the previous best";
            case ANNUAL_IMPROVER -> Format.percent(value) + " less than " + (Periods.year(achievement.period()) - 1);
            case GREEN_STREAK, CUSTOM -> null;
        };
    }
}
