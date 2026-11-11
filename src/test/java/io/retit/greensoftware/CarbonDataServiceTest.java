package io.retit.greensoftware;

import io.retit.greensoftware.client.AwsCarbonProvider;
import io.retit.greensoftware.client.CarbonDataUnavailableException;
import io.retit.greensoftware.config.CarbonSyncConfig;
import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementEvaluation;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.CloudProvider;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.CarbonDataService;
import io.retit.greensoftware.util.PeriodRange;
import io.retit.greensoftware.util.Periods;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the carbon data sync of the CarbonDataService.
 * AWS is replaced by a fake provider; Azure and GCP stay disabled.
 */
@QuarkusTest
class CarbonDataServiceTest {

    @Inject
    CarbonDataService carbonDataService;

    @Inject
    CarbonSyncConfig syncConfig;

    private static final String TEAM_ALPHA = "Team Alpha";
    private static final String TEAM_BETA = "Team Beta";
    private static final String TEAM_GAMMA = "Team Gamma";
    /** Team Beta's AWS account from application.yaml. */
    private static final String BETA_AWS_PROJECT = "234567890123";

    private FakeAwsProvider aws;
    /** The latest period the scheduled sync fetches. */
    private String period;

    @BeforeEach
    @Transactional
    void setUp() {
        Achievement.deleteAll();
        AchievementEvaluation.deleteAll();
        CarbonEntry.deleteAll();

        aws = new FakeAwsProvider();
        QuarkusMock.installMockForType(aws, AwsCarbonProvider.class);
        period = Periods.previous();
    }

    @Test
    void syncRecentPeriods_storesTheConfiguredNumberOfMonths() {
        aws.co2KgByPeriod.put(period, 100.0);

        PeriodRange synced = carbonDataService.syncRecentPeriods();

        assertEquals(Periods.window(period, syncConfig.months()), synced.periods());
        QuarkusTransaction.requiringNew().run(() -> {
            CarbonEntry entry = CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period).orElseThrow();
            assertEquals(100.0, entry.totalCo2Kg, 0.001);
            assertEquals(100.0, entry.awsCo2Kg, 0.001);
            assertNull(entry.azureCo2Kg, "Disabled provider should have no data, not 0");
            assertNull(entry.gcpCo2Kg, "Disabled provider should have no data, not 0");
            assertEquals(Team.count() * synced.months(), CarbonEntry.count("period in ?1", synced.periods()),
                    "Every team should be synced for every period");
            assertEquals(CarbonEntry.count(), CarbonEntry.count("period in ?1", synced.periods()),
                    "No other period should be synced");
        });
    }

    @Test
    void syncRecentPeriods_fetchesTheWholeRangeOfEachTeamAtOnce() {
        PeriodRange synced = carbonDataService.syncRecentPeriods();

        long teamsWithAwsProjects = QuarkusTransaction.requiringNew().call(() ->
                CloudProject.<CloudProject>list("provider", CloudProvider.AWS).stream()
                        .map(p -> p.team.id)
                        .distinct()
                        .count());
        assertEquals(List.of(synced), aws.fetchedRanges.stream().distinct().toList(),
                "Every fetch should cover the whole range");
        assertEquals(teamsWithAwsProjects, aws.fetchedRanges.size(),
                "Each team's AWS data should be fetched with one call");
    }

    @Test
    void syncRecentPeriods_fetchesOutsideOfTransactions() {
        aws.co2KgByPeriod.put(period, 100.0);

        QuarkusTransaction.requiringNew().run(() -> carbonDataService.syncRecentPeriods());

        assertFalse(aws.fetchedRanges.isEmpty(), "The AWS data should have been fetched");
        assertFalse(aws.fetchedInTransaction,
                "Provider APIs should not be called while a transaction holds a database connection");
        QuarkusTransaction.requiringNew().run(() -> assertEquals(100.0, CarbonEntry.findByTeamAndPeriod(
                        Team.findByName(TEAM_ALPHA), period).orElseThrow().totalCo2Kg, 0.001,
                "The data should be stored in transactions of its own"));
    }

    @Test
    void syncRecentPeriods_overwritesOlderPeriodsWithRevisedData() {
        String oldest = Periods.window(period, syncConfig.months()).getFirst();
        QuarkusTransaction.requiringNew().run(() ->
                createEntry(Team.findByName(TEAM_ALPHA), oldest, 999.0));
        aws.co2KgByPeriod.put(oldest, 100.0);

        carbonDataService.syncRecentPeriods();

        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(100.0, CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), oldest)
                        .orElseThrow().totalCo2Kg, 0.001, "The revised data should replace the stored entry"));
    }

    @Test
    void syncRecentPeriods_keepsExistingDataOfFailedPeriodsOnly() {
        String older = Periods.previous(period);
        QuarkusTransaction.requiringNew().run(() -> {
            createEntry(Team.findByName(TEAM_ALPHA), older, 999.0);
            createEntry(Team.findByName(TEAM_ALPHA), period, 999.0);
        });
        aws.failingPeriods.add(older);
        aws.co2KgByPeriod.put(period, 100.0);

        carbonDataService.syncRecentPeriods();

        QuarkusTransaction.requiringNew().run(() -> {
            Team alpha = Team.findByName(TEAM_ALPHA);
            assertEquals(999.0, CarbonEntry.findByTeamAndPeriod(alpha, older).orElseThrow().totalCo2Kg, 0.001,
                    "A failed fetch should not overwrite the stored entry");
            assertEquals(100.0, CarbonEntry.findByTeamAndPeriod(alpha, period).orElseThrow().totalCo2Kg, 0.001,
                    "The other periods should still be updated");
        });
    }

    @Test
    void syncRecentPeriods_keepsAllExistingDataOfATeamWhoseFetchFails() {
        String older = Periods.previous(period);
        QuarkusTransaction.requiringNew().run(() -> {
            createEntry(Team.findByName(TEAM_BETA), older, 999.0);
            createEntry(Team.findByName(TEAM_BETA), period, 999.0);
        });
        aws.co2KgByPeriod.put(period, 100.0);
        aws.failingProjectIds.add(BETA_AWS_PROJECT);

        carbonDataService.syncRecentPeriods();

        QuarkusTransaction.requiringNew().run(() -> {
            Team beta = Team.findByName(TEAM_BETA);
            assertEquals(999.0, CarbonEntry.findByTeamAndPeriod(beta, older).orElseThrow().totalCo2Kg, 0.001,
                    "A failed fetch should not overwrite any stored entry of the team");
            assertEquals(999.0, CarbonEntry.findByTeamAndPeriod(beta, period).orElseThrow().totalCo2Kg, 0.001,
                    "A failed fetch should not overwrite any stored entry of the team");
            assertEquals(2, CarbonEntry.count("team", beta), "A failed fetch should not create entries");
            assertEquals(100.0, CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period)
                    .orElseThrow().totalCo2Kg, 0.001, "The other teams should still be updated");
        });
    }

    @Test
    void syncRecentPeriods_evaluatesEveryCompletePeriodOnce() {
        // 20% less every month, so that achievements are awarded
        List<String> periods = Periods.window(period, syncConfig.months());
        for (int i = 0; i < periods.size(); i++) {
            aws.co2KgByPeriod.put(periods.get(i), 1000.0 * Math.pow(0.8, i));
        }

        carbonDataService.syncRecentPeriods();

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(syncConfig.months(), AchievementEvaluation.count(),
                    "Every synced period should be evaluated");
            assertTrue(Achievement.count() > 0, "The reductions should be awarded");
            Achievement.deleteAll();
        });

        carbonDataService.syncRecentPeriods();

        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(0, Achievement.count(), "Evaluated periods should not be evaluated again"));
    }

    @Test
    void syncForPeriod_teamWithoutProviderDataHasNoTotal() {
        aws.co2KgByPeriod.put(period, 100.0);

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() -> {
            // Gamma only has Azure and GCP projects, which are disabled
            Team gamma = Team.findByName(TEAM_GAMMA);
            CarbonEntry entry = CarbonEntry.findByTeamAndPeriod(gamma, period).orElseThrow();
            assertNull(entry.totalCo2Kg, "No provider data should be no total, not 0");
        });
    }

    @Test
    void syncForPeriod_skipsWhenNoProviderIsEnabled() {
        aws.enabled = false;
        QuarkusTransaction.requiringNew().run(() ->
                createEntry(Team.findByName(TEAM_ALPHA), period, 999.0));

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(1, CarbonEntry.count(), "No entries should be created without any provider");
            assertEquals(999.0, CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period)
                    .orElseThrow().totalCo2Kg, 0.001, "Existing data should not be overwritten");
        });
    }

    @Test
    void syncForPeriod_keepsExistingDataWhenFetchFails() {
        QuarkusTransaction.requiringNew().run(() ->
                createEntry(Team.findByName(TEAM_ALPHA), period, 999.0));
        aws.failingPeriods.add(period);

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(999.0, CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period)
                    .orElseThrow().totalCo2Kg, 0.001, "A failed fetch should not overwrite the stored entry");
            assertTrue(CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_GAMMA), period).isPresent(),
                    "Teams without AWS projects should still be synced");
        });
    }

    @Test
    void syncForPeriod_evaluatesAchievementsOnceEveryTeamHasData() {
        createAlphaReduction();

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(1, Achievement.count("type", AchievementType.HIGHEST_REDUCTION),
                        "Achievements should be evaluated for a complete period"));
    }

    @Test
    void syncForPeriod_skipsAchievementsWhileThePeriodIsIncomplete() {
        // AWS has not published Beta's account yet, so Beta gets no entry, while the
        // other teams have data that would be awarded (Alpha's reduction)
        createAlphaReduction();
        aws.failingProjectIds.add(BETA_AWS_PROJECT);

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period).isPresent(),
                    "Teams with published data should still be synced");
            assertEquals(0, Achievement.count(), "Rankings over only some teams should not be awarded");
        });
    }

    @Test
    void syncForPeriod_evaluatesAPreviouslyIncompletePeriodOnceComplete() {
        createAlphaReduction();
        aws.failingProjectIds.add(BETA_AWS_PROJECT);
        carbonDataService.syncForPeriod(period);

        aws.failingProjectIds.clear();
        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(1, Achievement.count("type", AchievementType.HIGHEST_REDUCTION),
                        "Achievements should be evaluated once the period is complete"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Makes the sync of {@link #period} a reduction for Alpha, which is awarded once the
     * period is evaluated: 100 kg CO2e from AWS, after 200 kg in the previous period.
     */
    private void createAlphaReduction() {
        aws.co2KgByPeriod.put(period, 100.0);
        QuarkusTransaction.requiringNew().run(() ->
                createEntry(Team.findByName(TEAM_ALPHA), Periods.previous(period), 200.0));
    }

    private void createEntry(Team team, String period, Double totalCo2) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2;
        entry.syncTimestamp = Instant.now();
        entry.persist();
    }

    /**
     * Returns the configured CO2 per period for every project, 0 for unknown periods.
     * Leaves out the periods in {@link #failingPeriods} like unpublished data, and fails
     * like an unreachable API for the projects in {@link #failingProjectIds}. Records
     * the range of every call in {@link #fetchedRanges}, and in
     * {@link #fetchedInTransaction} whether any call ran in a transaction.
     */
    static class FakeAwsProvider extends AwsCarbonProvider {

        final Map<String, Double> co2KgByPeriod = new HashMap<>();
        final Set<String> failingPeriods = new HashSet<>();
        final Set<String> failingProjectIds = new HashSet<>();
        final List<PeriodRange> fetchedRanges = new ArrayList<>();
        boolean fetchedInTransaction = false;
        boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range) {
            fetchedRanges.add(range);
            fetchedInTransaction |= QuarkusTransaction.isActive();
            if (projects.stream().anyMatch(p -> failingProjectIds.contains(p.projectId))) {
                throw new CarbonDataUnavailableException("AWS unavailable for some projects");
            }
            Map<String, List<CarbonData>> results = new HashMap<>();
            for (String period : range.periods()) {
                if (!failingPeriods.contains(period)) {
                    double co2Kg = co2KgByPeriod.getOrDefault(period, 0.0);
                    results.put(period, projects.stream()
                            .map(p -> new CarbonData(p.projectId, co2Kg))
                            .toList());
                }
            }
            return results;
        }
    }
}
