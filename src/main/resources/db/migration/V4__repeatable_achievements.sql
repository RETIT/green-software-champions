-- =============================================================================
-- V4: Achievements can be awarded more than once
-- =============================================================================

-- Each achievement is now granted at most once per team and period instead of
-- once per team. Some store the threshold reached (e.g., the length of a green
-- streak), and the value they were awarded for (e.g., the reduction in percent).
ALTER TABLE achievements ADD COLUMN achievement_threshold INTEGER;
ALTER TABLE achievements ADD COLUMN measured_value DOUBLE PRECISION;

-- Removed types: with location-based emissions, net zero is only reached without
-- any usage, the lowest total rewards the smallest team rather than a reduction,
-- and efficiency had no rule, as there is no usage data to relate emissions to.
DELETE FROM achievements
WHERE achievement_type IN ('FIRST_NET_ZERO', 'LEADERBOARD_LEADER', 'EFFICIENCY_EXPERT');

-- The two streak types are now the thresholds 3 and 6 of GREEN_STREAK
UPDATE achievements SET achievement_threshold = 6 WHERE achievement_type = 'GREEN_STREAK';
UPDATE achievements SET achievement_type = 'GREEN_STREAK', achievement_threshold = 3
WHERE achievement_type = 'CONSECUTIVE_CHAMPION';

-- Titles no longer start with the badge icon, which is shown in front of them anyway
UPDATE achievements SET title = 'Reduction Champion', badge_icon = '🏆'
WHERE achievement_type = 'HIGHEST_REDUCTION';
UPDATE achievements SET title = 'Big Saver', badge_icon = '📉'
WHERE achievement_type = 'BIG_SAVER';
UPDATE achievements SET title = 'Green Streak', badge_icon = '🌱'
WHERE achievement_type = 'GREEN_STREAK';
