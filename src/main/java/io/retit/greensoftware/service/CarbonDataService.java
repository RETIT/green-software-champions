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
import io.retit.greensoftware.util.PeriodRange;
import io.retit.greensoftware.util.Periods;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.LinkedHashMap;
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
     * @return the synced periods
     */
    public PeriodRange syncRecentPeriods() {
        PeriodRange range = PeriodRange.ending(Periods.toYearMonth(Periods.previous()), syncConfig.months());
        syncRange(range);
        return range;
    }

    /**
     * Syncs carbon data for all teams for the given period (YYYY-MM).
     */
    public void syncForPeriod(String period) {
        syncRange(PeriodRange.of(Periods.toYearMonth(period)));
    }

    /**
     * Syncs carbon data for all teams for the given periods, fetching each team's data
     * for the whole range at once, then evaluates the achievements of the periods that
     * have not been evaluated yet, see {@link #evaluateNewCompletePeriods(List)}.
     * Skipped when no cloud provider is enabled, see {@link #anyProviderEnabled()}.
     *
     * <p>Runs outside of any transaction, even if the caller has one, so that the
     * provider APIs are never called while a transaction holds a database connection
     * and counts towards the transaction timeout. Only reading the teams and storing
     * each team's data use (short) transactions.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void syncRange(PeriodRange range) {
        if (!anyProviderEnabled()) {
            LOG.infof("No cloud provider enabled, skipping carbon data sync for periods %s.", range);
            return;
        }
        LOG.infof("Starting carbon data sync for periods %s", range);
        for (TeamProjects team : findTeamProjects()) {
            Map<String, ProviderCo2Kg> co2KgByPeriod = fetchTeamData(team, range);
            if (!co2KgByPeriod.isEmpty()) {
                storeTeamData(team.teamId(), co2KgByPeriod);
            }
        }
        LOG.infof("Carbon data sync complete for periods %s", range);
        evaluateNewCompletePeriods(range.periods());
    }

    /** A team and its cloud projects by provider, read at the start of a sync. */
    record TeamProjects(Long teamId, String teamName, Map<CloudProvider, List<CloudProject>> projectsByProvider) {

        List<CloudProject> projectsOf(CloudProvider provider) {
            return projectsByProvider.getOrDefault(provider, List.of());
        }
    }

    /** A team's emissions in one period by provider, null for each provider it has no data from. */
    record ProviderCo2Kg(Double awsCo2Kg, Double azureCo2Kg, Double gcpCo2Kg) {}

    /**
     * Reads every team with its cloud projects. The projects are detached once the
     * transaction ends, which is fine as the providers only read their IDs.
     */
    @Transactional
    List<TeamProjects> findTeamProjects() {
        return Team.<Team>listAll().stream()
                .map(team -> new TeamProjects(team.id, team.name, CloudProject.findByTeam(team)
                        .stream()
                        .collect(Collectors.groupingBy(p -> p.provider))))
                .toList();
    }

    /**
     * Fetches the team's emissions in each period of {@code range} from the providers
     * it has projects with. A period is left out if not every provider's data for it
     * could be fetched, so that its stored entry is kept rather than overwritten with
     * incomplete data.
     *
     * @return the emissions by period, oldest first
     */
    private Map<String, ProviderCo2Kg> fetchTeamData(TeamProjects team, PeriodRange range) {
        Map<String, Double> awsCo2Kg;
        Map<String, Double> azureCo2Kg;
        Map<String, Double> gcpCo2Kg;
        try {
            awsCo2Kg = fetchProviderData(awsProvider, team.projectsOf(CloudProvider.AWS), range);
            azureCo2Kg = fetchProviderData(azureProvider, team.projectsOf(CloudProvider.AZURE), range);
            gcpCo2Kg = fetchProviderData(gcpProvider, team.projectsOf(CloudProvider.GCP), range);
        } catch (CarbonDataUnavailableException e) {
            LOG.warnf("Skipping team '%s' for periods %s, keeping its existing data: %s",
                    team.teamName(), range, e.getMessage());
            return Map.of();
        }

        Map<String, ProviderCo2Kg> co2KgByPeriod = new LinkedHashMap<>();
        for (String period : range.periods()) {
            if (isMissing(awsCo2Kg, period) || isMissing(azureCo2Kg, period) || isMissing(gcpCo2Kg, period)) {
                LOG.warnf("Skipping team '%s' for period %s, keeping its existing data: "
                        + "not every provider's data could be fetched", team.teamName(), period);
                continue;
            }
            co2KgByPeriod.put(period, new ProviderCo2Kg(
                    co2KgIn(awsCo2Kg, period), co2KgIn(azureCo2Kg, period), co2KgIn(gcpCo2Kg, period)));
        }
        return co2KgByPeriod;
    }

    /**
     * Stores the emissions of the team with {@code teamId} for each period of
     * {@code co2KgByPeriod}. One transaction per team, so that the teams already
     * stored are kept if a later one fails.
     */
    @Transactional
    void storeTeamData(Long teamId, Map<String, ProviderCo2Kg> co2KgByPeriod) {
        Team team = Team.findById(teamId);
        if (team == null) {
            // Deleted since the sync started
            return;
        }
        co2KgByPeriod.forEach((period, co2Kg) -> storeCarbonEntry(team, period, co2Kg));
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
     * revision of their data does not award e.g. a second Reduction Champion.
     */
    @Transactional
    public void evaluateNewCompletePeriods(List<String> periods) {
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

    /**
     * Upserts the team's {@link CarbonEntry} for {@code period}, with null for each
     * provider the team has no data from.
     */
    private void storeCarbonEntry(Team team, String period, ProviderCo2Kg co2Kg) {
        Double awsCo2Kg = co2Kg.awsCo2Kg();
        Double azureCo2Kg = co2Kg.azureCo2Kg();
        Double gcpCo2Kg = co2Kg.gcpCo2Kg();
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
     * Sums the provider's emissions over {@code projects} for each period of
     * {@code range}, or returns null if the team has no data from this provider (no
     * projects there, or the provider is disabled). Periods whose data could not be
     * fetched completely are missing.
     *
     * @throws CarbonDataUnavailableException if the data could not be fetched at all
     */
    private Map<String, Double> fetchProviderData(CarbonProvider provider, List<CloudProject> projects,
                                                  PeriodRange range) {
        if (!provider.isEnabled() || projects.isEmpty()) {
            return null;
        }
        return provider.fetchCarbonData(projects, range)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()
                        .stream()
                        .mapToDouble(CarbonProvider.CarbonData::co2Kg)
                        .sum()));
    }

    /**
     * Whether {@code co2KgByPeriod}, a result of {@link #fetchProviderData}, lacks
     * {@code period} although the team has data from the provider.
     */
    private static boolean isMissing(Map<String, Double> co2KgByPeriod, String period) {
        return co2KgByPeriod != null && !co2KgByPeriod.containsKey(period);
    }

    /**
     * The emissions in {@code period} from {@code co2KgByPeriod}, a result of
     * {@link #fetchProviderData}, or null if the team has no data from the provider.
     */
    private static Double co2KgIn(Map<String, Double> co2KgByPeriod, String period) {
        return co2KgByPeriod == null ? null : co2KgByPeriod.get(period);
    }
}
