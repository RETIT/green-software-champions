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

    /** YYYY-MM period awarded for, or for custom achievements, granted in. */
    @Column(nullable = false, length = 7)
    public String period;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type")
    public AchievementType type;

    /** Null for types without thresholds, see {@link AchievementType}. */
    @Column(name = "achievement_threshold")
    public Integer threshold;

    /** The value awarded for, e.g. the reduction in percent, see {@link AchievementType}. */
    @Column(name = "measured_value")
    public Double measuredValue;

    /** Latest period first. */
    public static List<Achievement> findByTeam(Team team) {
        return list("team = ?1 order by period desc, id desc", team);
    }

    public static List<Achievement> findAllLatestFirst() {
        return list("order by period desc, id desc");
    }

    /** Awarded for {@code period} or earlier, latest period first. */
    public static List<Achievement> findUpTo(String period) {
        // YYYY-MM strings sort chronologically
        return list("period <= ?1 order by period desc, id desc", period);
    }
}
