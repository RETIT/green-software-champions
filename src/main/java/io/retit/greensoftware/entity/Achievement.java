package io.retit.greensoftware.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;
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

    @Column(name = "earned_at", nullable = false)
    public Instant earnedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "achievement_type")
    public AchievementType type;

    public static List<Achievement> findByTeam(Team team) {
        return list("team = ?1 order by earnedAt desc", team);
    }

    public boolean existsForTeamAndType(Team team, AchievementType type) {
        return count("team = ?1 and type = ?2", team, type) > 0;
    }
}
