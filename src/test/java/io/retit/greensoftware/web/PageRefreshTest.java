package io.retit.greensoftware.web;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.specification.RequestSpecification;
import io.retit.greensoftware.AccessTokens;
import io.retit.greensoftware.OidcTestProfile;
import io.retit.greensoftware.achievement.Achievement;
import io.retit.greensoftware.achievement.AchievementEvaluation;
import io.retit.greensoftware.emissions.CarbonEntry;
import io.retit.greensoftware.emissions.PeriodRange;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.event.DashboardDataChanged;
import io.retit.greensoftware.leaderboard.Dashboard;
import io.retit.greensoftware.leaderboard.LeaderboardService;
import io.retit.greensoftware.sync.CarbonSyncService;
import io.retit.greensoftware.sync.provider.AwsCarbonProvider;
import io.retit.greensoftware.sync.provider.FakeAwsProvider;
import io.retit.greensoftware.team.CloudProject;
import io.retit.greensoftware.team.Team;
import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.View;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests that every change of the data renders the pages again. */
@QuarkusTest
@TestProfile(OidcTestProfile.class)
@WithTestResource(OidcWiremockTestResource.class)
class PageRefreshTest {

    @Inject
    PageService pageService;

    @Inject
    CarbonSyncService carbonSyncService;

    @Inject
    Event<DashboardDataChanged> dataChanged;

    private final String period = Periods.previous();

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            deleteData();
            for (Team team : Team.<Team>listAll()) {
                CarbonEntry entry = new CarbonEntry();
                entry.team = team;
                entry.period = period;
                entry.totalCo2Kg = 100.0;
                entry.persist();
            }
        });
        assertTrue(pageService.refresh());
        assertTrue(leaderboard().contains("400 kg"), leaderboard());
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(PageRefreshTest::deleteData);
    }

    private static void deleteData() {
        Achievement.deleteAll();
        AchievementEvaluation.deleteAll();
        CarbonEntry.deleteAll();
    }

    @Test
    void sync_rendersThePagesAgain() {
        // Alpha, Beta and Delta have AWS projects, Gamma has none
        var aws = new FakeAwsProvider();
        aws.co2KgByPeriod.put(period, 700.0);
        QuarkusMock.installMockForType(aws, AwsCarbonProvider.class);

        carbonSyncService.syncForPeriod(period);

        assertTrue(leaderboard().contains("2.10 t"), leaderboard());
        assertTrue(map().contains("2.10 t"), map());
    }

    @Test
    void sync_thatFailsHalfway_rendersTheTeamsStoredBefore() {
        QuarkusMock.installMockForType(new FakeAwsProvider() {
            @Override
            public Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range) {
                if (!fetchedRanges.isEmpty()) {
                    throw new IllegalStateException("Unexpected response");
                }
                co2KgByPeriod.put(period, 700.0);
                return super.fetchCarbonData(projects, range);
            }
        }, AwsCarbonProvider.class);

        assertThrows(IllegalStateException.class, () -> carbonSyncService.syncForPeriod(period));

        assertTrue(leaderboard().contains("700 kg"), leaderboard());
    }

    @Test
    void sync_withoutProvider_changesNothing() {
        changeDataUnnoticed();
        carbonSyncService.syncForPeriod(period);
        assertTrue(leaderboard().contains("400 kg"), leaderboard());
    }

    @Test
    void change_isShownOnceItIsCommitted() {
        QuarkusTransaction.requiringNew().run(() -> {
            CarbonEntry.update("totalCo2Kg = 600");
            dataChanged.fire(new DashboardDataChanged());
            assertTrue(leaderboard().contains("400 kg"), leaderboard());
        });
        assertTrue(leaderboard().contains("2.40 t"), leaderboard());
    }

    @Test
    void change_thatIsRolledBack_rendersNothing() {
        RenderedPage before = pageService.get(View.LEADERBOARD, null).orElseThrow();
        QuarkusTransaction.begin();
        CarbonEntry.update("totalCo2Kg = 600");
        dataChanged.fire(new DashboardDataChanged());
        QuarkusTransaction.rollback();
        assertSame(before, pageService.get(View.LEADERBOARD, null).orElseThrow());
    }

    @Test
    void refresh_withinATransaction_rendersOnlyCommittedData() {
        QuarkusTransaction.requiringNew().run(() -> {
            CarbonEntry.update("totalCo2Kg = 600");
            assertTrue(pageService.refresh());
        });
        assertTrue(leaderboard().contains("400 kg"), leaderboard());
    }

    @Test
    void grantedAchievement_isShownRightAway() {
        assertFalse(leaderboard().contains("Cleanup Hero"));

        grantAchievement(teamId(), 201);

        assertTrue(leaderboard().contains("Cleanup Hero"), leaderboard());
        assertTrue(map().contains("🧹"), map());
    }

    @Test
    void grantAchievement_toAnUnknownTeam_isNotFound() {
        grantAchievement(-1L, 404);
    }

    @Test
    void grantAchievement_succeedsEvenIfRenderingFails() {
        installFailingLeaderboardService();

        grantAchievement(teamId(), 201);

        QuarkusTransaction.requiringNew().run(() ->
                assertTrue(Achievement.count("title", "Cleanup Hero") == 1, "The achievement should be stored"));
        assertTrue(leaderboard().contains("400 kg"), "The old pages should be kept");
    }

    @Test
    void rebuild_rendersThePagesAgain() {
        changeDataUnnoticed();
        assertTrue(leaderboard().contains("400 kg"), leaderboard());

        asAdmin().post("/api/admin/pages/rebuild").then().statusCode(204);

        assertTrue(leaderboard().contains("2.40 t"), leaderboard());
    }

    @Test
    void rebuild_thatFails_isAnError() {
        installFailingLeaderboardService();

        asAdmin().post("/api/admin/pages/rebuild").then().statusCode(500)
                .contentType("application/json")
                .body("message", containsString("failed"));

        assertTrue(leaderboard().contains("400 kg"), "The old pages should be kept");
    }

    private static void changeDataUnnoticed() {
        QuarkusTransaction.requiringNew().run(() -> CarbonEntry.update("totalCo2Kg = 600"));
    }

    private static Long teamId() {
        return QuarkusTransaction.requiringNew().call(() -> Team.findByName("Team Alpha").id);
    }

    private static void grantAchievement(Long teamId, int expectedStatus) {
        asAdmin().contentType("application/json")
                .body("""
                        {"title": "Cleanup Hero", "description": "Deleted 40 idle VMs", "badgeIcon": "🧹"}""")
                .post("/api/admin/teams/{id}/achievements", teamId)
                .then().statusCode(expectedStatus);
    }

    private static RequestSpecification asAdmin() {
        return given().auth().oauth2(AccessTokens.accessToken("admin", Set.of("admin")));
    }

    private static void installFailingLeaderboardService() {
        QuarkusMock.installMockForType(new LeaderboardService() {
            @Override
            public Dashboard getDashboard(String period) {
                throw new IllegalStateException("Database unavailable");
            }
        }, LeaderboardService.class);
    }

    private String leaderboard() {
        return html(View.LEADERBOARD);
    }

    private String map() {
        return html(View.MAP);
    }

    private String html(View view) {
        return new String(pageService.get(view, null).orElseThrow().html(), StandardCharsets.UTF_8);
    }
}
