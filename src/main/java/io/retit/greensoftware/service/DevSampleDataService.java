package io.retit.greensoftware.service;

import io.retit.greensoftware.config.CarbonHistoryConfig;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.CloudProvider;
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
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
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
     * Seeds sample carbon entries for every team in the database and evaluates their
     * achievements like a sync does, so the sample achievements follow the rules.
     * Must be called after {@link TeamSetupService#initialiseTeams()} so that the
     * configured {@link Team} records already exist in the database.
     */
    @Transactional
    public void seedSampleData() {
        // The history window the charts show, plus one period more so the
        // oldest charted period still has a previous period to compare against.
        List<String> periods = Periods.window(Periods.previous(), historyConfig.months() + 1);

        int inserted = 0;
        for (Team team : Team.<Team>listAll()) {
            SampleTeam s = SampleTeam.of(team);

            for (int i = 0; i < periods.size(); i++) {
                int monthsAgo = periods.size() - 1 - i;
                double factor = Math.pow(1 + s.monthlyReduction, monthsAgo)
                        * (1 + 0.08 * Math.sin(monthsAgo * s.wiggle));

                Double aws   = scale(s.awsLatest, factor);
                Double azure = scale(s.azureLatest, factor);
                Double gcp   = scale(s.gcpLatest, factor);
                // Null rather than 0 for a team without projects, like a sync stores it
                Double total = Stream.of(aws, azure, gcp).filter(Objects::nonNull)
                        .reduce(Double::sum).orElse(null);

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

    /**
     * A team's sample data, given as the latest-period values per provider. Older
     * periods are derived from them: monthlyReduction is the average relative
     * reduction per month (negative = growing footprint), wiggle the frequency of
     * the month-to-month noise on top of it. null = provider not used by the team.
     */
    private record SampleTeam(
            // latest period
            Double awsLatest, Double azureLatest, Double gcpLatest,
            // derivation of older periods
            double monthlyReduction, double wiggle
    ) {

        /**
         * Generates the sample data of {@code team}, with values for exactly the
         * providers it has projects with. The values are random, but seeded with
         * the team name, so a team gets the same values at every restart and the
         * periods added in later months continue its existing curve.
         */
        static SampleTeam of(Team team) {
            Set<CloudProvider> providers = CloudProject.findByTeam(team).stream()
                    .map(p -> p.provider)
                    .collect(Collectors.toSet());
            Random random = new Random(team.name.hashCode());
            // Every value is drawn even for unused providers, so that adding or
            // removing a project leaves the team's other values unchanged
            Double aws   = latestCo2Kg(random, providers.contains(CloudProvider.AWS));
            Double azure = latestCo2Kg(random, providers.contains(CloudProvider.AZURE));
            Double gcp   = latestCo2Kg(random, providers.contains(CloudProvider.GCP));
            double monthlyReduction = -0.015 + random.nextDouble() * 0.065; // -1.5 % to 5 %
            double wiggle = 0.8 + random.nextDouble() * 1.7;                // 0.8 to 2.5
            return new SampleTeam(aws, azure, gcp, monthlyReduction, wiggle);
        }

        /** A provider's emissions in the latest period, 40 to 220 kg, or null if unused. */
        private static Double latestCo2Kg(Random random, boolean used) {
            double co2Kg = Math.round((40 + random.nextDouble() * 180) * 10) / 10.0;
            return used ? co2Kg : null;
        }
    }
}
