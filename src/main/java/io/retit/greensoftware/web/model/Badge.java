package io.retit.greensoftware.web.model;

/**
 * The badge of a group of achievements of the same kind, see {@link AchievementGroups}.
 *
 * @param title   of the achievements
 * @param tier    the highest tier reached as a Roman numeral, or null without tiers
 * @param count   how often the tier was reached, or without tiers, the achievement awarded
 * @param label   the title with the tier and count, e.g. "Green Streak II ×2"
 * @param tooltip the label, the criteria and each award, on lines of their own
 */
public record Badge(String icon, String title, String tier, int count, String label, String tooltip) {
}
