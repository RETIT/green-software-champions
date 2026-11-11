package io.retit.greensoftware.service;

import io.retit.greensoftware.config.CarbonHistoryConfig;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.util.Periods;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Inserts realistic sample carbon data in dev mode so the UI is immediately useful
 * without requiring live cloud-provider API credentials.
 *
 * Only active when the "dev" build profile is selected ({@code mvn quarkus:dev}).
 * Idempotent: already-existing entries are skipped on every restart.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevSampleDataService {

    private static final Logger LOG = Logger.getLogger(DevSampleDataService.class);

    @Inject
    CarbonHistoryConfig historyConfig;

    @Inject
    CarbonDataService carbonDataService;

    /**
     * Seeds sample carbon entries for all configured teams and evaluates their
     * achievements like a sync does, so the sample achievements follow the rules.
     * Must be called after {@link TeamSetupService#initialiseTeams()} so that
     * {@link Team} records already exist in the database.
     */
    @Transactional
    public void seedSampleData() {
        // The history window the charts show, plus one period more so the
        // oldest charted period still has a previous period to compare against.
        List<String> periods = Periods.window(Periods.previous(), historyConfig.months() + 1);

        // -----------------------------------------------------------------------
        // Carbon entries per team, given as the latest-period values per provider.
        // Older periods are derived from them: monthlyReduction is the average
        // relative reduction per month (negative = growing footprint), wiggle the
        // frequency of the month-to-month noise on top of it.
        // null = provider not used by that team
        // -----------------------------------------------------------------------
        List<SampleTeam> teams = List.of(
            //             teamName      aws    azure  gcp    monthlyReduction  wiggle
            new SampleTeam("Team Alpha", 180.2, null,  65.3,  0.025,            1.1),
            new SampleTeam("Team Beta",   98.4, 90.8,  null, -0.012,            1.7),
            new SampleTeam("Team Gamma",  null, 42.1,  86.3,  0.045,            2.3),
            new SampleTeam("Team Delta", 201.3, 112.8, 98.6,  0.008,            0.9)
        );

        int inserted = 0;
        for (SampleTeam s : teams) {
            Team team = Team.findByName(s.teamName);
            if (team == null) {
                LOG.warnf("Dev sample data: team '%s' not found – run TeamSetupService first.", s.teamName);
                continue;
            }

            for (int i = 0; i < periods.size(); i++) {
                int monthsAgo = periods.size() - 1 - i;
                double factor = Math.pow(1 + s.monthlyReduction, monthsAgo)
                        * (1 + 0.08 * Math.sin(monthsAgo * s.wiggle));

                Double aws   = scale(s.awsLatest, factor);
                Double azure = scale(s.azureLatest, factor);
                Double gcp   = scale(s.gcpLatest, factor);
                double total = Stream.of(aws, azure, gcp).filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).sum();

                if (CarbonEntry.findByTeamAndPeriod(team, periods.get(i)).isEmpty()) {
                    CarbonEntry entry = new CarbonEntry();
                    entry.team          = team;
                    entry.period        = periods.get(i);
                    entry.totalCo2Kg    = total;
                    entry.awsCo2Kg      = aws;
                    entry.azureCo2Kg    = azure;
                    entry.gcpCo2Kg      = gcp;
                    entry.syncTimestamp = Instant.now();
                    entry.persist();
                    inserted++;
                }
            }
        }

        carbonDataService.evaluateNewCompletePeriods(periods);
        LOG.infof("Dev sample data seeding complete – %d carbon entr%s inserted.",
                inserted, inserted == 1 ? "y" : "ies");
    }

    /** Scales a provider value by {@code factor}, rounded to one decimal. */
    private static Double scale(Double value, double factor) {
        return value == null ? null : Math.round(value * factor * 10) / 10.0;
    }

    // -------------------------------------------------------------------------
    // Internal data holder
    // -------------------------------------------------------------------------

    private record SampleTeam(
            String teamName,
            // latest period
            Double awsLatest, Double azureLatest, Double gcpLatest,
            // derivation of older periods
            double monthlyReduction, double wiggle
    ) {}
}

