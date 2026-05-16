package io.retit.greensoftware.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carbon_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "period"}))
public class CarbonEntry extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    public Team team;

    /**
     * Reporting period in the format YYYY-MM (e.g., "2024-03").
     */
    @Column(nullable = false, length = 7)
    public String period;

    /** Total CO2-equivalent emissions in kg for this period. */
    @Column(name = "total_co2_kg", columnDefinition = "NUMERIC(20,4)")
    public Double totalCo2Kg;

    /** CO2-equivalent emissions in kg from the previous period. */
    @Column(name = "previous_co2_kg", columnDefinition = "NUMERIC(20,4)")
    public Double previousCo2Kg;

    /**
     * Reduction percentage vs. previous period.
     * Positive = reduction (good), Negative = increase (bad).
     */
    @Column(name = "reduction_percentage", columnDefinition = "NUMERIC(10,4)")
    public Double reductionPercentage;

    @Column(name = "sync_timestamp")
    public Instant syncTimestamp;

    @Column(name = "aws_co2_kg", columnDefinition = "NUMERIC(20,4)")
    public Double awsCo2Kg;

    @Column(name = "azure_co2_kg", columnDefinition = "NUMERIC(20,4)")
    public Double azureCo2Kg;

    @Column(name = "gcp_co2_kg", columnDefinition = "NUMERIC(20,4)")
    public Double gcpCo2Kg;

    public static Optional<CarbonEntry> findByTeamAndPeriod(Team team, String period) {
        return find("team = ?1 and period = ?2", team, period).firstResultOptional();
    }

    public static List<CarbonEntry> findByPeriod(String period) {
        return list("period", period);
    }

    public static List<CarbonEntry> findByTeamOrderByPeriodDesc(Team team) {
        return list("team = ?1 order by period desc", team);
    }
}
