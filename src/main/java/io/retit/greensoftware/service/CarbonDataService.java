package io.retit.greensoftware.service;

import io.retit.greensoftware.client.AwsCarbonProvider;
import io.retit.greensoftware.client.AzureCarbonProvider;
import io.retit.greensoftware.client.CarbonDataUnavailableException;
import io.retit.greensoftware.client.CarbonProvider;
import io.retit.greensoftware.client.GcpCarbonProvider;
import io.retit.greensoftware.config.CarbonSyncConfig;
import io.retit.greensoftware.entity.AchievementEvaluation;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.CloudProvider;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.util.Periods;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Orchestrates the periodic fetch of carbon emission data from all enabled cloud
 * providers and persists the results into the database.
 */
@ApplicationScoped
public class CarbonDataService {

    private static final Logger LOG = Logger.getLogger(CarbonDataService.class);

    @Inject
    AwsCarbonProvider awsProvider;

    @Inject
    AzureCarbonProvider azureProvider;

    @Inject
    GcpCarbonProvider gcpProvider;

    @Inject
    AchievementService achievementService;

    @Inject
    CarbonSyncConfig syncConfig;

    /**
     * Syncs carbon data for all teams for the last {@code carbon.sync.months} months,
     * ending at the previous calendar month, the latest month the providers can have
     * published data for. Older months are re-fetched because the providers may have
     * revised their data since the last sync.
     *
     * @return the synced periods, oldest first
     */
    public List<String> syncRecentPeriods() {
        List<String> periods = Periods.window(Periods.previous(), syncConfig.months());
        syncPeriods(periods);
        return periods;
    }

    /**
     * Syncs carbon data for all teams for the given period (YYYY-MM).
     */
    public void syncForPeriod(String period) {
        syncPeriods(List.of(period));
    }

    /**
     * Syncs carbon data for all teams for the given periods, then evaluates the
     * achievements of those that have not been evaluated yet, see
     * {@link #evaluateNewCompletePeriods(List)}. Skipped when no cloud provider is
     * enabled, see {@link #anyProviderEnabled()}.
     */
    public void syncPeriods(List<String> periods) {
        if (!anyProviderEnabled()) {
            LOG.infof("No cloud provider enabled, skipping carbon data sync for periods %s.", periods);
            return;
        }
        // One transaction per period, so a long sync neither runs into the transaction
        // timeout nor loses the periods already synced if a later one fails
        for (String period : periods) {
            syncPeriodData(period);
        }
        evaluateNewCompletePeriods(periods);
    }

    /**
     * Fetches and stores the carbon data of all teams for {@code period}. A team whose
     * data cannot be fetched completely keeps its stored entry.
     */
    @Transactional
    void syncPeriodData(String period) {
        LOG.infof("Starting carbon data sync for period: %s", period);

        List<Team> teams = Team.listAll();
        for (Team team : teams) {
            try {
                syncTeamCarbonData(team, period);
            } catch (CarbonDataUnavailableException e) {
                // Keep the stored entry rather than overwriting it with incomplete data
                LOG.warnf("Skipping team '%s' for period %s, keeping its existing data: %s",
                        team.name, period, e.getMessage());
            }
        }

        LOG.infof("Carbon data sync complete for period: %s", period);
    }

    /**
     * Without any enabled provider a sync has nothing to fetch and would only replace
     * the stored data (e.g., the dev sample data) with entries without emissions.
     */
    private boolean anyProviderEnabled() {
        return awsProvider.isEnabled() || azureProvider.isEnabled() || gcpProvider.isEnabled();
    }

    /**
     * Evaluates the achievements of each of {@code periods} that every team has data
     * for and that has not been evaluated before, oldest first. Incomplete periods are
     * skipped, as rankings would only cover the teams whose providers have already
     * published, and awards are never revoked. A later sync re-fetching the period
     * evaluates it once the data is complete. Periods are evaluated only once, so a
     * revision of their data does not award e.g. a second leaderboard leader.
     */
    @Transactional
    void evaluateNewCompletePeriods(List<String> periods) {
        List<String> evaluated = AchievementEvaluation.findEvaluatedPeriods(periods);
        List<String> complete = CarbonEntry.findCompletePeriods(periods).reversed();
        for (String period : complete) {
            if (!evaluated.contains(period)) {
                achievementService.evaluateAchievements(period);
                AchievementEvaluation.markEvaluated(period);
            }
        }
        periods.stream()
                .filter(p -> !complete.contains(p))
                .forEach(p -> LOG.infof("Data for period %s is incomplete, skipping achievement evaluation.", p));
    }

    private void syncTeamCarbonData(Team team, String period) {
        // Group projects by cloud provider
        Map<CloudProvider, List<CloudProject>> projectsByProvider = CloudProject.findByTeam(team)
                .stream()
                .collect(Collectors.groupingBy(p -> p.provider));

        Double awsCo2Kg = fetchProviderData(awsProvider, projectsByProvider.getOrDefault(CloudProvider.AWS, List.of()), period);
        Double azureCo2Kg = fetchProviderData(azureProvider, projectsByProvider.getOrDefault(CloudProvider.AZURE, List.of()), period);
        Double gcpCo2Kg = fetchProviderData(gcpProvider, projectsByProvider.getOrDefault(CloudProvider.GCP, List.of()), period);

        // Null rather than 0 if no provider has data for this team, so a team without
        // (enabled) projects is not mistaken for one with zero emissions
        Double totalCo2Kg = Stream.of(awsCo2Kg, azureCo2Kg, gcpCo2Kg)
                .filter(Objects::nonNull)
                .reduce(Double::sum)
                .orElse(null);

        // Upsert the CarbonEntry for this team and period
        CarbonEntry entry = CarbonEntry.findByTeamAndPeriod(team, period)
                .orElse(new CarbonEntry());
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2Kg;
        entry.awsCo2Kg = awsCo2Kg;
        entry.azureCo2Kg = azureCo2Kg;
        entry.gcpCo2Kg = gcpCo2Kg;
        entry.syncTimestamp = Instant.now();
        entry.persist();

        LOG.infof("Synced team '%s' for period %s: %.2f kg CO2e (AWS=%.2f, Azure=%.2f, GCP=%.2f)",
                team.name, period, totalCo2Kg, awsCo2Kg, azureCo2Kg, gcpCo2Kg);
    }

    /**
     * Sums the provider's emissions over {@code projects}, or returns null if the team
     * has no data from this provider (no projects there, or the provider is disabled).
     *
     * @throws CarbonDataUnavailableException if the data could not be fetched completely
     */
    private Double fetchProviderData(CarbonProvider provider, List<CloudProject> projects, String period) {
        if (!provider.isEnabled() || projects.isEmpty()) {
            return null;
        }
        return provider.fetchCarbonData(projects, period)
                .stream()
                .mapToDouble(CarbonProvider.CarbonData::co2Kg)
                .sum();
    }
}
