package io.retit.greensoftware;

import io.retit.greensoftware.client.AwsCarbonProvider;
import io.retit.greensoftware.client.CarbonDataUnavailableException;
import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.CarbonDataService;
import io.retit.greensoftware.util.Periods;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    private static final String TEAM_ALPHA = "Team Alpha";
    private static final String TEAM_GAMMA = "Team Gamma";
    /** Team Beta's AWS account from application.yaml. */
    private static final String BETA_AWS_PROJECT = "234567890123";

    private FakeAwsProvider aws;
    /** The period the scheduled sync fetches. */
    private String period;

    @BeforeEach
    @Transactional
    void setUp() {
        Achievement.deleteAll();
        CarbonEntry.deleteAll();

        aws = new FakeAwsProvider();
        QuarkusMock.installMockForType(aws, AwsCarbonProvider.class);
        period = Periods.previous();
    }

    @Test
    void syncPreviousPeriod_storesOnlyThePreviousPeriod() {
        aws.co2KgByPeriod.put(period, 100.0);

        carbonDataService.syncPreviousPeriod();

        QuarkusTransaction.requiringNew().run(() -> {
            CarbonEntry entry = CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period).orElseThrow();
            assertEquals(100.0, entry.totalCo2Kg, 0.001);
            assertEquals(100.0, entry.awsCo2Kg, 0.001);
            assertNull(entry.azureCo2Kg, "Disabled provider should have no data, not 0");
            assertNull(entry.gcpCo2Kg, "Disabled provider should have no data, not 0");
            assertEquals(CarbonEntry.count(), CarbonEntry.count("period", period),
                    "No other period should be synced");
        });
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
            assertTrue(Achievement.findByTeam(gamma).stream()
                            .noneMatch(a -> a.type == AchievementType.FIRST_NET_ZERO),
                    TEAM_GAMMA + " should not be awarded net zero without any data");
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
        aws.co2KgByPeriod.put(period, 100.0);

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() ->
                assertEquals(1, Achievement.count("type", AchievementType.LEADERBOARD_LEADER),
                        "Achievements should be evaluated for a complete period"));
    }

    @Test
    void syncForPeriod_skipsAchievementsWhileThePeriodIsIncomplete() {
        aws.co2KgByPeriod.put(period, 100.0);
        // AWS has not published Beta's account yet, so Beta gets no entry, while the
        // other teams have data that would be awarded (e.g., LEADERBOARD_LEADER)
        aws.failingProjectIds.add(BETA_AWS_PROJECT);

        carbonDataService.syncForPeriod(period);

        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(CarbonEntry.findByTeamAndPeriod(Team.findByName(TEAM_ALPHA), period).isPresent(),
                    "Teams with published data should still be synced");
            assertEquals(0, Achievement.count(), "Rankings over only some teams should not be awarded");
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void createEntry(Team team, String period, Double totalCo2) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2;
        entry.syncTimestamp = Instant.now();
        entry.persist();
    }

    /**
     * Returns the configured CO2 per period for every project, 0 for unknown periods,
     * and fails like an unreachable API for the periods in {@link #failingPeriods} and
     * the projects in {@link #failingProjectIds}.
     */
    static class FakeAwsProvider extends AwsCarbonProvider {

        final Map<String, Double> co2KgByPeriod = new HashMap<>();
        final Set<String> failingPeriods = new HashSet<>();
        final Set<String> failingProjectIds = new HashSet<>();
        boolean enabled = true;

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public List<CarbonData> fetchCarbonData(List<CloudProject> projects, String period) {
            if (failingPeriods.contains(period)) {
                throw new CarbonDataUnavailableException("AWS unavailable for " + period);
            }
            if (projects.stream().anyMatch(p -> failingProjectIds.contains(p.projectId))) {
                throw new CarbonDataUnavailableException("AWS unavailable for some projects");
            }
            double co2Kg = co2KgByPeriod.getOrDefault(period, 0.0);
            return projects.stream()
                    .map(p -> new CarbonData(p.projectId, co2Kg))
                    .toList();
        }
    }
}
