package io.retit.greensoftware.api;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.specification.RequestSpecification;
import io.retit.greensoftware.AccessTokens;
import io.retit.greensoftware.OidcTestProfile;
import io.retit.greensoftware.achievement.Achievement;
import io.retit.greensoftware.achievement.AchievementEvaluation;
import io.retit.greensoftware.achievement.AchievementType;
import io.retit.greensoftware.emissions.CarbonEntry;
import io.retit.greensoftware.team.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/** Pins the JSON of the API, which scripts rely on. */
@QuarkusTest
@TestProfile(OidcTestProfile.class)
@WithTestResource(OidcWiremockTestResource.class)
class LeaderboardResourceTest {

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            deleteData();
            Team alpha = Team.findByName("Team Alpha");
            // Both months have 31 days
            createEntry(alpha, "2024-07", 400.0, null);
            createEntry(alpha, "2024-08", 200.0, 100.0);

            Achievement achievement = new Achievement();
            achievement.team = alpha;
            achievement.type = AchievementType.GREEN_STREAK;
            achievement.title = "Green Streak";
            achievement.description = "Six months";
            achievement.badgeIcon = "🌱";
            achievement.period = "2024-08";
            achievement.threshold = 6;
            achievement.measuredValue = 2.5;
            achievement.persist();
        });
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(LeaderboardResourceTest::deleteData);
    }

    private static void deleteData() {
        Achievement.deleteAll();
        AchievementEvaluation.deleteAll();
        CarbonEntry.deleteAll();
    }

    @Test
    void leaderboard_hasTheFieldsOfEachTeam() {
        request().get("/api/leaderboard?period=2024-08").then().statusCode(200)
                .contentType("application/json")
                .body("$", hasSize(4))
                .body("[0]", aMapWithSize(15))
                .body("[0].teamId", notNullValue())
                .body("[0].teamName", equalTo("Team Alpha"))
                .body("[0].teamColor", equalTo("#4CAF50"))
                .body("[0].teamIcon", notNullValue())
                .body("[0].rank", equalTo(1))
                .body("[0].period", equalTo("2024-08"))
                .body("[0].totalCo2Kg", equalTo(300f))
                .body("[0].previousCo2Kg", equalTo(400f))
                .body("[0].reductionPercent", equalTo(25f))
                .body("[0].companySharePercent", equalTo(100f))
                .body("[0].awsCo2Kg", equalTo(200f))
                .body("[0].azureCo2Kg", nullValue())
                .body("[0].gcpCo2Kg", equalTo(100f))
                .body("[0].history", hasSize(2))
                .body("[0].history[0]", aMapWithSize(5))
                .body("[0].history.period", contains("2024-07", "2024-08"))
                .body("[0].history[1].totalCo2Kg", equalTo(300f))
                .body("[0].history[1].awsCo2Kg", equalTo(200f))
                .body("[0].history[1]", hasKey("azureCo2Kg"))
                .body("[0].history[1].gcpCo2Kg", equalTo(100f))
                .body("[3].totalCo2Kg", nullValue())
                .body("[3].achievements", hasSize(0));
    }

    @Test
    void leaderboard_hasTheAchievements() {
        request().get("/api/leaderboard?period=2024-08").then().statusCode(200)
                .body("[0].achievements", hasSize(1))
                .body("[0].achievements[0]", aMapWithSize(10))
                .body("[0].achievements[0].id", notNullValue())
                .body("[0].achievements[0].title", equalTo("Green Streak"))
                .body("[0].achievements[0].description", equalTo("Six months"))
                .body("[0].achievements[0].criteria", containsString("months in a row"))
                .body("[0].achievements[0].badgeIcon", equalTo("🌱"))
                .body("[0].achievements[0].period", equalTo("2024-08"))
                .body("[0].achievements[0].type", equalTo("GREEN_STREAK"))
                .body("[0].achievements[0].threshold", equalTo(6))
                .body("[0].achievements[0].tier", equalTo(2))
                .body("[0].achievements[0].measuredValue", equalTo(2.5f));
    }

    @Test
    void summary_hasTheCompanyWideFigures() {
        request().get("/api/leaderboard/summary?period=2024-08").then().statusCode(200)
                .body("$", aMapWithSize(6))
                .body("period", equalTo("2024-08"))
                .body("totalCo2Kg", equalTo(300f))
                .body("previousTotalCo2Kg", equalTo(400f))
                .body("reductionPercent", equalTo(25f))
                .body("teamCount", equalTo(4))
                .body("history.period", contains("2024-07", "2024-08"))
                .body("history.totalCo2Kg", contains(400f, 300f));
    }

    @Test
    void teams_haveTheirProjects() {
        String alpha = "find { it.name == 'Team Alpha' }";
        request().get("/api/leaderboard/teams").then().statusCode(200)
                .body(alpha, aMapWithSize(6))
                .body(alpha + ".id", notNullValue())
                .body(alpha + ".description", notNullValue())
                .body(alpha + ".color", equalTo("#4CAF50"))
                .body(alpha + ".icon", notNullValue())
                .body(alpha + ".projects[0]", aMapWithSize(4))
                .body(alpha + ".projects[0].id", notNullValue())
                .body(alpha + ".projects[0].projectId", notNullValue())
                .body(alpha + ".projects[0].projectName", notNullValue())
                .body(alpha + ".projects.provider", hasItem("AWS"));
    }

    private static RequestSpecification request() {
        return given().auth().oauth2(AccessTokens.accessToken("alice", Set.of("user")));
    }

    private static void createEntry(Team team, String period, Double awsCo2Kg, Double gcpCo2Kg) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.awsCo2Kg = awsCo2Kg;
        entry.gcpCo2Kg = gcpCo2Kg;
        entry.totalCo2Kg = awsCo2Kg + (gcpCo2Kg != null ? gcpCo2Kg : 0);
        entry.persist();
    }
}
