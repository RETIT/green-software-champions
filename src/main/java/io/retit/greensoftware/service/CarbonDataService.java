package io.retit.greensoftware.service;

import io.retit.greensoftware.client.AwsCarbonProvider;
import io.retit.greensoftware.client.AzureCarbonProvider;
import io.retit.greensoftware.client.CarbonProvider;
import io.retit.greensoftware.client.GcpCarbonProvider;
import io.retit.greensoftware.entity.*;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.CloudProvider;
import io.retit.greensoftware.entity.Team;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the periodic fetch of carbon emission data from all enabled cloud
 * providers and persists the results into the database.
 */
@ApplicationScoped
public class CarbonDataService {

    private static final Logger LOG = Logger.getLogger(CarbonDataService.class);
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Inject
    AwsCarbonProvider awsProvider;

    @Inject
    AzureCarbonProvider azureProvider;

    @Inject
    GcpCarbonProvider gcpProvider;

    @Inject
    AchievementService achievementService;

    /**
     * Syncs carbon data for all teams for the current calendar month.
     */
    public void syncCurrentPeriod() {
        String period = LocalDate.now().format(PERIOD_FMT);
        syncForPeriod(period);
    }

    /**
     * Syncs carbon data for all teams for the given period (YYYY-MM).
     */
    @Transactional
    public void syncForPeriod(String period) {
        LOG.infof("Starting carbon data sync for period: %s", period);

        List<Team> teams = Team.listAll();
        for (Team team : teams) {
            syncTeamCarbonData(team, period);
        }

        // After syncing all teams, evaluate achievements
        achievementService.evaluateAchievements(period);
        LOG.infof("Carbon data sync complete for period: %s", period);
    }

    private void syncTeamCarbonData(Team team, String period) {
        // Group projects by cloud provider
        Map<CloudProvider, List<CloudProject>> projectsByProvider = CloudProject.findByTeam(team)
                .stream()
                .collect(Collectors.groupingBy(p -> p.provider));

        double awsCo2Kg = fetchProviderData(awsProvider, projectsByProvider.getOrDefault(CloudProvider.AWS, List.of()), period);
        double azureCo2Kg = fetchProviderData(azureProvider, projectsByProvider.getOrDefault(CloudProvider.AZURE, List.of()), period);
        double gcpCo2Kg = fetchProviderData(gcpProvider, projectsByProvider.getOrDefault(CloudProvider.GCP, List.of()), period);

        double totalCo2Kg = awsCo2Kg + azureCo2Kg + gcpCo2Kg;

        // Look up previous period's entry
        String previousPeriod = getPreviousPeriod(period);
        Double previousCo2Kg = CarbonEntry.findByTeamAndPeriod(team, previousPeriod)
                .map(e -> e.totalCo2Kg)
                .orElse(null);

        Double reductionPct = null;
        if (previousCo2Kg != null && previousCo2Kg > 0) {
            reductionPct = ((previousCo2Kg - totalCo2Kg) / previousCo2Kg) * 100.0;
        }

        // Upsert the CarbonEntry for this team and period
        CarbonEntry entry = CarbonEntry.findByTeamAndPeriod(team, period)
                .orElse(new CarbonEntry());
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2Kg;
        entry.previousCo2Kg = previousCo2Kg;
        entry.reductionPercentage = reductionPct;
        entry.awsCo2Kg = awsCo2Kg;
        entry.azureCo2Kg = azureCo2Kg;
        entry.gcpCo2Kg = gcpCo2Kg;
        entry.syncTimestamp = Instant.now();
        entry.persist();

        LOG.infof("Synced team '%s' for period %s: %.2f kg CO2e (AWS=%.2f, Azure=%.2f, GCP=%.2f)",
                team.name, period, totalCo2Kg, awsCo2Kg, azureCo2Kg, gcpCo2Kg);
    }

    private double fetchProviderData(CarbonProvider provider, List<CloudProject> projects, String period) {
        if (!provider.isEnabled() || projects.isEmpty()) {
            return 0.0;
        }
        return provider.fetchCarbonData(projects, period)
                .stream()
                .mapToDouble(CarbonProvider.CarbonData::co2Kg)
                .sum();
    }

    private String getPreviousPeriod(String period) {
        LocalDate date = LocalDate.parse(period + "-01");
        return date.minusMonths(1).format(PERIOD_FMT);
    }
}
