package io.retit.greensoftware.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;

/**
 * Records that the achievements of a period have been evaluated, so each period is
 * evaluated only once even though every sync re-fetches several months.
 */
@Entity
@Table(name = "achievement_evaluations")
public class AchievementEvaluation extends PanacheEntityBase {

    /** YYYY-MM */
    @Id
    @Column(length = 7)
    public String period;

    @Column(name = "evaluated_at", nullable = false)
    public Instant evaluatedAt;

    /** Those of {@code periods} that have been evaluated. */
    public static List<String> findEvaluatedPeriods(List<String> periods) {
        return AchievementEvaluation.<AchievementEvaluation>list("period in ?1", periods).stream()
                .map(e -> e.period)
                .toList();
    }

    public static void markEvaluated(String period) {
        AchievementEvaluation evaluation = new AchievementEvaluation();
        evaluation.period = period;
        evaluation.evaluatedAt = Instant.now();
        evaluation.persist();
    }
}
