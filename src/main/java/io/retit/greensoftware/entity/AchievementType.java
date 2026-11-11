package io.retit.greensoftware.entity;

/**
 * The kinds of achievements, with the title and badge icon each one is shown with.
 *
 * <p>Except for {@link #CUSTOM}, achievements are granted by {@code AchievementService}
 * when a period is evaluated, each at most once per team and period, so that a team
 * can earn the same achievement again in later periods. The rules are documented
 * there.
 */
public enum AchievementType {

    /**
     * Monthly: the largest reduction of all teams vs. the previous month.
     * Measured value: the reduction in percent.
     */
    HIGHEST_REDUCTION("Reduction Champion", "🏆"),

    /**
     * Monthly: the most CO2e saved of all teams vs. the previous month.
     * Measured value: the saving in kg.
     */
    MOST_CO2_SAVED("Most CO₂e Saved", "💪"),

    /**
     * A large reduction vs. the average of the previous months.
     * Measured value: the reduction in percent.
     */
    BIG_SAVER("Big Saver", "📉"),

    /**
     * Months in a row with a reduction vs. the month before.
     * Threshold: the length of the streak in months.
     */
    GREEN_STREAK("Green Streak", "🌱"),

    /**
     * The lowest emissions since tracking began.
     * Measured value: the reduction vs. the previous best in percent.
     */
    PERSONAL_BEST("Personal Best", "⭐"),

    /**
     * Yearly, for December: a reduction vs. the previous calendar year.
     * Threshold: the minimum reduction reached in percent. Measured value: the reduction
     * in percent.
     */
    ANNUAL_IMPROVER("Annual Improver", "📅"),

    /** Granted by an admin, with a title and badge icon of their choice. */
    CUSTOM(null, null);

    /** The title shown for this kind of achievement, null for {@link #CUSTOM}. */
    public final String title;

    /** The badge icon shown for this kind of achievement, null for {@link #CUSTOM}. */
    public final String badgeIcon;

    AchievementType(String title, String badgeIcon) {
        this.title = title;
        this.badgeIcon = badgeIcon;
    }
}
