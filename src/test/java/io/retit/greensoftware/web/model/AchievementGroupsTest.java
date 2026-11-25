package io.retit.greensoftware.web.model;

import io.retit.greensoftware.achievement.AchievementType;
import io.retit.greensoftware.achievement.AwardedAchievement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementGroupsTest {

    @Test
    void withoutAchievements_hasNoBadges() {
        assertTrue(AchievementGroups.badges(List.of()).isEmpty());
    }

    @Test
    void groupsByType_inTheOrderOfTheLatestAchievements() {
        List<Badge> badges = AchievementGroups.badges(List.of(
                achievement(AchievementType.BIG_SAVER, "2026-03"),
                achievement(AchievementType.HIGHEST_REDUCTION, "2026-02"),
                achievement(AchievementType.BIG_SAVER, "2026-01")));

        assertEquals(List.of("Big Saver ×2", "Reduction Champion"), badges.stream().map(Badge::label).toList());
        assertEquals("📉", badges.getFirst().icon());
        assertEquals(2, badges.getFirst().count());
        assertNull(badges.getFirst().tier());
    }

    @Test
    void groupsCustomAchievementsByTitleAndIcon() {
        List<Badge> badges = AchievementGroups.badges(List.of(
                custom("Hero", "🧹", null),
                custom("Hero", "🧹", null),
                custom("Hero", "🎉", null),
                custom("Star", "🧹", null)));

        assertEquals(List.of("Hero ×2", "Hero", "Star"), badges.stream().map(Badge::label).toList());
    }

    @Test
    void withoutType_isCustom() {
        AwardedAchievement old = new AwardedAchievement(
                1L, null, "Hero", null, null, "🧹", "2026-01", null, null, null);
        assertEquals(List.of("Hero ×2"), AchievementGroups.badges(List.of(old, custom("Hero", "🧹", null)))
                .stream().map(Badge::label).toList());
    }

    @Test
    void withoutIcon_showsADefaultOne() {
        assertEquals("🏅", AchievementGroups.badges(List.of(custom("Hero", null, null))).getFirst().icon());
        assertEquals("🏅", AchievementGroups.badges(List.of(custom("Hero", "", null))).getFirst().icon());
    }

    @Test
    void withTiers_countsOnlyTheHighestOne() {
        Badge badge = AchievementGroups.badges(List.of(
                streak("2026-06", 6, 2),
                streak("2026-03", 3, 1),
                streak("2025-12", 6, 2),
                streak("2025-09", 3, 1))).getFirst();

        assertEquals("II", badge.tier());
        assertEquals(2, badge.count());
        assertEquals("Green Streak II ×2", badge.label());
    }

    @ParameterizedTest
    @CsvSource({"1, I", "4, IV", "10, X", "11, 11"})
    void tiersAreRomanNumeralsUpToTen(int tier, String expected) {
        assertEquals(expected, AchievementGroups.badges(List.of(streak("2026-06", 99, tier))).getFirst().tier());
    }

    @Test
    void tooltip_listsTheCriteriaAndEachAwardsTierAndValue() {
        assertEquals("""
                Green Streak II
                The criteria

                June 2026: II (6 months)
                March 2026: I (3 months)""", tooltip(streak("2026-06", 6, 2), streak("2026-03", 3, 1)));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            HIGHEST_REDUCTION, January 2026: 12.3% less
            BIG_SAVER,         January 2026: 12.3% less
            MOST_CO2_SAVED,    January 2026: 12 kg saved
            PERSONAL_BEST,     January 2026: 12.3% below the previous best
            ANNUAL_IMPROVER,   2026: 12.3% less than 2025
            """)
    void tooltip_describesTheMeasuredValue(AchievementType type, String expected) {
        assertEquals(expected, tooltip(achievement(type, "2026-01", null, 12.3)).lines().toList().getLast());
    }

    @Test
    void tooltip_withoutMeasuredValue_showsTheDescription() {
        assertEquals("January 2026",
                tooltip(achievement(AchievementType.BIG_SAVER, "2026-01")).lines().toList().getLast());
        assertEquals("January 2026: A lot less",
                tooltip(achievement(AchievementType.BIG_SAVER, "2026-01", "A lot less", null))
                        .lines().toList().getLast());
    }

    @Test
    void tooltip_ofCustomAchievements_showsASharedDescriptionOnce() {
        assertEquals("""
                Hero ×2
                Deleted VMs

                January 2026
                January 2026""", tooltip(custom("Hero", "🧹", "Deleted VMs"), custom("Hero", "🧹", "Deleted VMs")));
        assertEquals("""
                Hero ×2
                January 2026: Deleted VMs
                January 2026: Deleted disks""",
                tooltip(custom("Hero", "🧹", "Deleted VMs"), custom("Hero", "🧹", "Deleted disks")));
        assertEquals("""
                Hero
                January 2026""", tooltip(custom("Hero", "🧹", null)));
    }

    @Test
    void tooltip_listsAtMostTwelveAwards() {
        List<AwardedAchievement> achievements = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            achievements.add(custom("Hero", "🧹", null));
        }

        List<String> lines = AchievementGroups.badges(achievements).getFirst().tooltip().lines().toList();

        assertEquals(1 + 12 + 1, lines.size());
        assertEquals("… and 2 earlier", lines.getLast());
    }

    private static String tooltip(AwardedAchievement... achievements) {
        return AchievementGroups.badges(List.of(achievements)).getFirst().tooltip();
    }

    private static AwardedAchievement streak(String period, int threshold, int tier) {
        AchievementType type = AchievementType.GREEN_STREAK;
        return new AwardedAchievement(1L, type, type.title, null, "The criteria", type.badgeIcon,
                period, threshold, tier, null);
    }

    private static AwardedAchievement achievement(AchievementType type, String period) {
        return achievement(type, period, null, null);
    }

    private static AwardedAchievement achievement(AchievementType type, String period,
                                                  String description, Double measuredValue) {
        return new AwardedAchievement(1L, type, type.title, description, "The criteria", type.badgeIcon,
                period, null, null, measuredValue);
    }

    private static AwardedAchievement custom(String title, String badgeIcon, String description) {
        return new AwardedAchievement(1L, AchievementType.CUSTOM, title, description, null, badgeIcon,
                "2026-01", null, null, null);
    }
}
