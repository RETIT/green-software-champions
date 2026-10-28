package io.retit.greensoftware.service;

import io.retit.greensoftware.client.AwsCarbonProvider;
import io.retit.greensoftware.client.AzureCarbonProvider;
import io.retit.greensoftware.client.CarbonDataUnavailableException;
import io.retit.greensoftware.client.CarbonProvider;
import io.retit.greensoftware.client.GcpCarbonProvider;
import io.retit.greensoftware.entity.*;
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

    /**
     * Syncs carbon data for all teams for the previous calendar month, the latest
     * month the providers can have published data for.
     */
    public void syncPreviousPeriod() {
        syncForPeriod(Periods.previous());
    }

    /**
     * Syncs carbon data for all teams for the given period (YYYY-MM). Skipped when no
     * cloud provider is enabled, see {@link #anyProviderEnabled()}.
     */
    @Transactional
    public void syncForPeriod(String period) {
        if (!anyProviderEnabled()) {
            LOG.infof("No cloud provider enabled, skipping carbon data sync for period %s.", period);
            return;
        }
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

        evaluateAchievementsIfComplete(period);
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
     * Evaluates the achievements of {@code period} only if every team has data for it.
     * Otherwise rankings would only cover the teams whose providers have already
     * published, and awards are never revoked. A later sync of the period (e.g., a
     * manual one) evaluates them once the data is complete.
     */
    private void evaluateAchievementsIfComplete(String period) {
        if (!CarbonEntry.findCompletePeriods(List.of(period)).isEmpty()) {
            achievementService.evaluateAchievements(period);
        } else {
            LOG.infof("Data for period %s is incomplete, skipping achievement evaluation.", period);
        }
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
