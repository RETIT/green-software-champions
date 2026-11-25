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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Seeds sample carbon data in dev mode, so the UI is useful without cloud credentials. */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevSampleDataService {

    private static final Logger LOG = Logger.getLogger(DevSampleDataService.class);

    @Inject
    CarbonHistoryConfig historyConfig;

    @Inject
    CarbonDataService carbonDataService;

    /**
     * Seeds the missing entries of every team and evaluates their achievements like a
     * sync. Requires the teams to exist, see {@link TeamSetupService#initialiseTeams()}.
     */
    @Transactional
    public void seedSampleData() {
        // One more than charted, so the oldest charted period has a predecessor
        List<String> periods = Periods.window(Periods.previous(), historyConfig.months() + 1);

        int inserted = 0;
        for (Team team : Team.<Team>listAll()) {
            Map<String, Emissions> emissionsByPeriod = SampleEmissions.of(team.name, providersOf(team)).in(periods);
            for (String period : periods) {
                if (CarbonEntry.findByTeamAndPeriod(team, period).isEmpty()) {
                    CarbonEntry entry = new CarbonEntry();
                    entry.team = team;
                    entry.period = period;
                    emissionsByPeriod.get(period).copyTo(entry);
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

    private static Set<CloudProvider> providersOf(Team team) {
        return CloudProject.findByTeam(team).stream()
                .map(p -> p.provider)
                .collect(Collectors.toSet());
    }
}
