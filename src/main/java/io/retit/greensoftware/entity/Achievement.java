package io.retit.greensoftware.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "achievements")
public class Achievement extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    public Team team;

    @Column(nullable = false)
    public String title;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(name = "badge_icon")
    public String badgeIcon;

    /**
     * Reporting period in the format YYYY-MM (e.g., "2024-03") the achievement was
     * awarded for, or for a custom achievement the one it was granted in.
     */
    @Column(nullable = false, length = 7)
    public String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type")
    public AchievementType type;

    /**
     * The threshold reached, for the types with thresholds (see
     * {@link AchievementType}), otherwise null.
     */
    @Column(name = "achievement_threshold")
    public Integer threshold;

    /**
     * The value the achievement was awarded for, e.g., the reduction in percent (see
     * {@link AchievementType}), or null if its type has none.
     */
    @Column(name = "measured_value")
    public Double measuredValue;

    /** The achievements of {@code team}, latest period first. */
    public static List<Achievement> findByTeam(Team team) {
        return list("team = ?1 order by period desc, id desc", team);
    }

    /** The achievements of all teams, latest period first. */
    public static List<Achievement> findAllLatestFirst() {
        return list("order by period desc, id desc");
    }

    /**
     * The achievements of all teams awarded for {@code period} or earlier, latest
     * period first.
     */
    public static List<Achievement> findUpTo(String period) {
        // YYYY-MM strings sort chronologically
        return list("period <= ?1 order by period desc, id desc", period);
    }
}
