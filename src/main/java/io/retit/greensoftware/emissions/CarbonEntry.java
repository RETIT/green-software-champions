package io.retit.greensoftware.emissions;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.retit.greensoftware.team.Team;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carbon_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"team_id", "period"}))
public class CarbonEntry extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    public Team team;

    /** YYYY-MM */
    @Column(nullable = false, length = 7)
    public String period;

    /** Null rather than 0 if no provider has data for the team. */
    @Column(name = "total_co2_kg", columnDefinition = "DECIMAL(20,4)")
    public Double totalCo2Kg;

    @Column(name = "sync_timestamp")
    public Instant syncTimestamp;

    @Column(name = "aws_co2_kg", columnDefinition = "DECIMAL(20,4)")
    public Double awsCo2Kg;

    @Column(name = "azure_co2_kg", columnDefinition = "DECIMAL(20,4)")
    public Double azureCo2Kg;

    @Column(name = "gcp_co2_kg", columnDefinition = "DECIMAL(20,4)")
    public Double gcpCo2Kg;

    public static Optional<CarbonEntry> findByTeamAndPeriod(Team team, String period) {
        return find("team = ?1 and period = ?2", team, period).firstResultOptional();
    }

    public static List<CarbonEntry> findByPeriods(Collection<String> periods) {
        return list("period in ?1", periods);
    }

    public static List<CarbonEntry> findUpTo(String period) {
        // YYYY-MM strings sort chronologically
        return list("period <= ?1", period);
    }

    /**
     * The periods of {@code periods} that have an entry for every team, latest first.
     * Entries are unique per team and period and are deleted with their team, so
     * counting suffices.
     */
    public static List<String> findCompletePeriods(List<String> periods) {
        return getEntityManager()
                .createQuery("select e.period from CarbonEntry e where e.period in :periods"
                        + " group by e.period having count(e) >= :teamCount"
                        + " order by e.period desc", String.class)
                .setParameter("periods", periods)
                .setParameter("teamCount", Team.count())
                .getResultList();
    }

    /** The periods with entries, latest first. */
    public static List<String> findPeriods() {
        return getEntityManager()
                .createQuery("select distinct e.period from CarbonEntry e order by e.period desc", String.class)
                .getResultList();
    }

    public static Optional<String> findEarliestPeriod() {
        return Optional.ofNullable(getEntityManager()
                .createQuery("select min(e.period) from CarbonEntry e", String.class)
                .getSingleResult());
    }
}
