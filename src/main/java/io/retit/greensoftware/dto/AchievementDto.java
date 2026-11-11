package io.retit.greensoftware.dto;

import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.service.AchievementService;

public class AchievementDto {

    public Long id;
    public String title;
    public String description;

    /**
     * What achievements of this type are awarded for, as of the current rules, or null
     * for custom achievements. Unlike {@link #description}, it is the same for all
     * achievements of a type and does not quote the value this one was awarded for.
     */
    public String criteria;

    public String badgeIcon;

    /** Period (YYYY-MM) the achievement was awarded for. */
    public String period;
    public String type;

    /** The threshold reached, for the types with thresholds, otherwise null. */
    public Integer threshold;

    /**
     * The tier the threshold stands for, counting from 1, e.g., 3 for the third of a
     * type's thresholds, or null for types without thresholds.
     */
    public Integer tier;

    /** The value the achievement was awarded for, e.g., the reduction in percent, or null. */
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
