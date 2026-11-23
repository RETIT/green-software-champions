package io.retit.greensoftware.dto;

import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.service.AchievementService;

public class AchievementDto {

    public Long id;
    public String title;
    public String description;

    /**
     * What achievements of this type are awarded for, the same for all of them, unlike
     * {@link #description}; null for custom achievements.
     */
    public String criteria;

    public String badgeIcon;

    /** YYYY-MM period the achievement was awarded for. */
    public String period;
    public String type;

    /** Null for types without thresholds. */
    public Integer threshold;

    /** How many of the type's thresholds were reached, e.g. 3 for a 12-month green streak. */
    public Integer tier;

    /** The value the achievement was awarded for, e.g. the reduction in percent, or null. */
    public Double measuredValue;

    public static AchievementDto from(Achievement achievement) {
        AchievementDto dto = new AchievementDto();
        dto.id = achievement.id;
        dto.title = achievement.title;
        dto.description = achievement.description;
        dto.criteria = achievement.type != null ? AchievementService.criteria(achievement.type) : null;
        dto.badgeIcon = achievement.badgeIcon;
        dto.period = achievement.period;
        dto.type = achievement.type != null ? achievement.type.name() : null;
        dto.threshold = achievement.threshold;
        dto.tier = achievement.type != null ? AchievementService.tier(achievement.type, achievement.threshold) : null;
        dto.measuredValue = achievement.measuredValue;
        return dto;
    }
}
