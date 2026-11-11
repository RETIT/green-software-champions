package io.retit.greensoftware.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /** The entries of {@code period}, keyed by team id. */
    public static Map<Long, CarbonEntry> findByPeriodByTeamId(String period) {
        return findByPeriod(period).stream()
                .collect(Collectors.toMap(e -> e.team.id, Function.identity()));
    }

    public static List<CarbonEntry> findByPeriods(List<String> periods) {
        return list("period in ?1", periods);
    }

    /** The entries of all teams for {@code period} and every earlier period. */
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

    /** The earliest period any team has an entry for, if there are any entries. */
    public static Optional<String> findEarliestPeriod() {
        return Optional.ofNullable(getEntityManager()
                .createQuery("select min(e.period) from CarbonEntry e", String.class)
                .getSingleResult());
    }
}
