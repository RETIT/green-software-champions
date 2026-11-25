package io.retit.greensoftware.achievement;

/**
 * An achievement with what follows from its type.
 *
 * @param type          null in old data, which then counts as {@link AchievementType#CUSTOM}
 * @param criteria      what achievements of the type are awarded for, the same for all of them,
 *                      unlike {@code description}; null for custom ones
 * @param period        YYYY-MM the achievement was awarded for
 * @param threshold     null for types without thresholds
 * @param tier          how many of the type's thresholds were reached, e.g. 3 for a 12-month
 *                      green streak, or null
 * @param measuredValue the value awarded for, e.g. the reduction in percent, or null
 */
public record AwardedAchievement(
        Long id, AchievementType type, String title, String description, String criteria, String badgeIcon,
        String period, Integer threshold, Integer tier, Double measuredValue
) {

    public static AwardedAchievement of(Achievement achievement) {
        AchievementType type = achievement.type;
        return new AwardedAchievement(
                achievement.id, type, achievement.title, achievement.description,
                type != null ? AchievementService.criteria(type) : null,
                achievement.badgeIcon, achievement.period, achievement.threshold,
                type != null ? AchievementService.tier(type, achievement.threshold) : null,
                achievement.measuredValue);
    }

    public boolean isCustom() {
        return type == null || type == AchievementType.CUSTOM;
    }
}
