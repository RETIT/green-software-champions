package io.retit.greensoftware;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.RestAssured;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.AchievementEvaluation;
import io.retit.greensoftware.entity.AchievementType;
import io.retit.greensoftware.entity.CarbonEntry;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.Dashboard;
import io.retit.greensoftware.service.LeaderboardService;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.web.PageService;
import io.retit.greensoftware.web.render.RenderedPage;
import io.retit.greensoftware.web.render.View;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the pages as a user with a bearer token; {@link SecurityTest} tests the login. */
@QuarkusTest
@TestProfile(OidcTestProfile.class)
@WithTestResource(OidcWiremockTestResource.class)
class PageResourceTest {

    private static final String EVIL_NAME = "<script>alert('team')</script>";
    private static final String EVIL_TITLE = "\"><img src=x onerror=alert('title')>";

    @Inject
    PageService pageService;

    /** Every team has data for these periods. */
    private final String period = Periods.previous();
    private final String older = Periods.previous(period);

    @BeforeEach
    void setUp() {
        QuarkusTransaction.requiringNew().run(() -> {
            deleteData();
            for (Team team : Team.<Team>listAll()) {
                createEntry(team, older, 100);
                createEntry(team, period, "Team Alpha".equals(team.name) ? 50 : 300);
            }
        });
        assertTrue(pageService.refresh());
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            deleteData();
            Team.delete("name", EVIL_NAME);
        });
    }

    private static void deleteData() {
        Achievement.deleteAll();
        AchievementEvaluation.deleteAll();
        CarbonEntry.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Content
    // -------------------------------------------------------------------------

    @Test
    void leaderboard_showsTheDefaultPeriod() {
        String html = getHtml("/");

        assertTrue(html.startsWith("<!doctype html>"), html);
        assertTrue(html.contains("Company CO₂e in " + format(period)), html);
        assertTrue(html.contains("950 kg"), "The total, 50 + 3 × 300 kg");
        assertTrue(html.contains("Carbon Footprint Leaderboard"));
        assertTrue(html.contains("4 teams · " + format(period)));
        assertTrue(html.contains("<details class=\"card rank-1\" style=\"--team-color: #4CAF50\">"),
                "Team Alpha should be the first");
        assertTrue(html.indexOf("Team Alpha") < html.indexOf("Team Beta"));
        assertTrue(html.contains("<a class=\"nav-link\" href=\"/\" aria-current=\"page\">"));
        assertTrue(html.contains("<a class=\"nav-link\" href=\"/map\">"));
    }

    @Test
    void map_showsTheDefaultPeriod() {
        String html = getHtml("/map");

        assertTrue(html.contains("Company CO₂e in " + format(period)), html);
        assertTrue(html.contains("Carbon Footprint World Map"));
        assertEquals(4, html.split("<g class=\"team-bubble\"", -1).length - 1);
        assertEquals(4, html.split("<template ", -1).length - 1);
        assertEquals(1, html.split("class=\"pulse-ring\"", -1).length - 1, "Only the best team's bubble");
        assertTrue(html.contains("<a class=\"nav-link\" href=\"/map\" aria-current=\"page\">"));
    }

    @Test
    void map_hasATooltipForEachBubble() {
        Map<String, String> bubbles = namesByRank(
                "<g class=\"team-bubble\" data-rank=\"(\\d+)\">.*?<text class=\"team-label\"[^>]*>(.*?)</text>");
        // The icon, then the name
        Map<String, String> tooltips = namesByRank(
                "<template data-rank=\"(\\d+)\">.*?<div class=\"tooltip-name\">\\S+ (.*?)</div>");

        assertEquals(Set.of("1", "2", "3", "4"), bubbles.keySet());
        assertEquals("Team Alpha", bubbles.get("1"));
        assertEquals(bubbles, tooltips);
    }

    /** The first group of each match, the rank, to the second one, the team's name. */
    private static Map<String, String> namesByRank(String regex) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(getHtml("/map")).results()
                .collect(Collectors.toMap(match -> match.group(1), match -> match.group(2)));
    }

    @Test
    void periodParameter_selectsThePeriod() {
        String html = getHtml("/?period=" + older);

        assertTrue(html.contains("Company CO₂e in " + format(older)), html);
        assertTrue(html.contains("400 kg"), "The total, 4 × 100 kg");
        assertTrue(html.contains("<a class=\"nav-link\" href=\"/map?period=" + older + "\">"),
                "The other view should show the same period");
        assertTrue(getHtml("/map?period=" + older).contains("Company CO₂e in " + format(older)));
    }

    @Test
    void periodPicker_offersThePeriodsWithData() {
        String html = getHtml("/map?period=" + older);

        assertTrue(html.contains("<form class=\"period-form\" method=\"get\" action=\"/map\">"), html);
        assertTrue(html.contains("<option value=\"" + period + "\">" + format(period) + "</option>"));
        assertTrue(html.contains("<option value=\"" + older + "\" selected>" + format(older) + "</option>"));
        assertEquals(2, html.split("<option ", -1).length - 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/map"})
    void defaultPeriodAsParameter_isTheSamePage(String path) {
        assertEquals(get(path).header("ETag"), get(path + "?period=" + period).header("ETag"));
        assertEquals(get(path).header("ETag"), get(path + "?period=").header("ETag"));
    }

    @Test
    void namesAndTitlesAreEscaped() {
        QuarkusTransaction.requiringNew().run(() -> {
            Team team = new Team();
            team.name = EVIL_NAME;
            team.icon = "<i>";
            team.color = "red\" onclick=\"alert(1)";
            team.persist();
            createEntry(team, older, 100);
            createEntry(team, period, 10);
            Achievement achievement = new Achievement();
            achievement.team = team;
            achievement.type = AchievementType.CUSTOM;
            achievement.title = EVIL_TITLE;
            achievement.description = EVIL_TITLE;
            achievement.badgeIcon = "<b>";
            achievement.period = period;
            achievement.persist();
        });
        assertTrue(pageService.refresh());

        for (String path : List.of("/", "/map")) {
            String html = getHtml(path);
            assertTrue(html.contains("&lt;script&gt;alert(&#39;team&#39;)&lt;/script&gt;"), html);
            assertFalse(html.contains("<script>alert"), html);
            assertFalse(html.contains("<img"), html);
            assertFalse(html.contains("<i>"), html);
            assertFalse(html.contains("<b>"), html);
            assertFalse(html.contains("onclick"), html);
        }
        assertTrue(getHtml("/").contains("&quot;&gt;&lt;img src=x onerror=alert(&#39;title&#39;)&gt;"));
    }

    @Test
    void logoutLink_isShownAsSecurityIsOn() {
        assertTrue(getHtml("/").contains("<a class=\"logout\" href=\"/logout\">"));
    }

    @Test
    void withoutFrontend_hasNoScriptsAndStyles() {
        String html = getHtml("/");
        assertFalse(html.contains("<script"), html);
        assertFalse(html.contains("rel=\"stylesheet\""), html);
    }

    // -------------------------------------------------------------------------
    // Caching and compression
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"/", "/map", "/demo", "/demo/map"})
    void page_isRevalidatedWithItsEtag(String path) {
        Response response = get(path);
        assertEquals("private, no-cache", response.header("Cache-Control"));
        assertEquals("Accept-Encoding", response.header("Vary"));
        assertNull(response.header("Content-Encoding"));
        String etag = response.header("ETag");

        Response notModified = request().header("If-None-Match", etag).get(path);
        assertEquals(304, notModified.statusCode());
        assertEquals(0, notModified.asByteArray().length);
        assertEquals(etag, notModified.header("ETag"));
        assertEquals("private, no-cache", notModified.header("Cache-Control"));
        assertEquals("Accept-Encoding", notModified.header("Vary"));

        assertEquals(200, request().header("If-None-Match", "\"other\"").get(path).statusCode());
    }

    @Test
    void etag_changesWithTheData() {
        String etag = get("/").header("ETag");
        QuarkusTransaction.requiringNew().run(() -> CarbonEntry.update("totalCo2Kg = 300"));
        assertTrue(pageService.refresh());

        Response response = request().header("If-None-Match", etag).get("/");
        assertEquals(200, response.statusCode());
        assertNotEquals(etag, response.header("ETag"));
    }

    @Test
    void page_isCompressedIfTheClientAcceptsIt() throws Exception {
        Response plain = get("/");
        Response gzip = request().header("Accept-Encoding", "gzip, br").get("/");

        assertEquals("gzip", gzip.header("Content-Encoding"));
        assertEquals("text/html;charset=UTF-8", gzip.contentType());
        byte[] unzipped = new GZIPInputStream(new ByteArrayInputStream(gzip.asByteArray())).readAllBytes();
        assertArrayEquals(plain.asByteArray(), unzipped);
        assertTrue(gzip.asByteArray().length < plain.asByteArray().length);

        assertEquals(plain.header("ETag").replaceAll("\"$", "-gzip\""), gzip.header("ETag"),
                "Another representation of the page needs an ETag of its own");
        assertEquals(304, request().header("Accept-Encoding", "gzip")
                .header("If-None-Match", gzip.header("ETag")).get("/").statusCode());
        assertEquals(200, request().header("If-None-Match", gzip.header("ETag")).get("/").statusCode());
    }

    /** By Quarkus, with the ETag of the plain page, see quarkus.http.compressors. */
    @ParameterizedTest
    @ValueSource(strings = {"deflate", "br", "gzip;q=0, deflate"})
    void page_isNotCompressedOtherwise(String acceptEncoding) {
        Response plain = get("/");
        Response response = request().header("Accept-Encoding", acceptEncoding).get("/");

        assertNull(response.header("Content-Encoding"));
        assertArrayEquals(plain.asByteArray(), response.asByteArray());
        assertEquals(plain.header("ETag"), response.header("ETag"));
    }

    /** Like the scripts and styles, which the tests run without. */
    @Test
    void api_isCompressedIfTheClientAcceptsIt() {
        assertEquals("gzip", request().header("Accept-Encoding", "gzip")
                .get("/api/leaderboard").header("Content-Encoding"));
    }

    // -------------------------------------------------------------------------
    // Errors
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(textBlock = """
            /?period=2026-13,      400, not a month
            /?period=nonsense,     400, not a month
            /map?period=2026-1,    400, not a month
            /?period=1999-01,      404, no data
            /map?period=1999-01,   404, no data
            """)
    void invalidPeriod_isAnErrorPage(String path, int status, String message) {
        request().get(path).then().statusCode(status)
                .contentType("text/html")
                .header("Cache-Control", "no-store")
                .header("ETag", (String) null)
                .body(containsString(message))
                .body(containsString("href=\"/\""))
                .body(not(containsString("Company CO₂e")));
    }

    @Test
    void failedRendering_isAnErrorPageWithALinkToTheDemo() {
        QuarkusMock.installMockForType(new PageService() {
            @Override
            public Optional<RenderedPage> get(View view, String period) {
                throw new IllegalStateException("The pages have not been rendered yet");
            }
        }, PageService.class);

        request().get("/").then().statusCode(500)
                .contentType("text/html")
                .header("Cache-Control", "no-store")
                .body(containsString("href=\"/demo\""))
                .body(not(containsString("Company CO₂e")));
    }

    // -------------------------------------------------------------------------
    // Demo data
    // -------------------------------------------------------------------------

    @Test
    void demo_showsDemoData() {
        String leaderboard = getHtml("/demo");
        assertTrue(leaderboard.contains("Demo data:"), leaderboard);
        assertTrue(leaderboard.contains("<a href=\"/\">Show your teams</a>"), leaderboard);
        assertTrue(leaderboard.contains("Company CO₂e in " + format(period)), leaderboard);
        assertTrue(leaderboard.contains("Reduction Champion"));
        assertTrue(leaderboard.contains("<a class=\"nav-link\" href=\"/demo/map\">"));
        assertFalse(leaderboard.contains("<select"), "Demo data has only one period");

        String map = getHtml("/demo/map");
        assertTrue(map.contains("Demo data:"));
        assertTrue(map.contains("Carbon Footprint World Map"));

        assertFalse(getHtml("/").contains("Demo data:"));
    }

    @Test
    void demo_leavesTheDatabaseAlone() {
        QuarkusMock.installMockForType(new LeaderboardService() {
            @Override
            public Dashboard getDashboard(String period) {
                throw new IllegalStateException("The demo should not read the database");
            }
        }, LeaderboardService.class);
        List<Long> before = counts();

        getHtml("/demo");
        getHtml("/demo/map");

        assertEquals(before, counts());
    }

    private static List<Long> counts() {
        return QuarkusTransaction.requiringNew().call(() ->
                List.of(Team.count(), CarbonEntry.count(), Achievement.count(), AchievementEvaluation.count()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String getHtml(String path) {
        Response response = get(path);
        assertEquals("text/html;charset=UTF-8", response.contentType());
        return new String(response.asByteArray(), StandardCharsets.UTF_8);
    }

    private static Response get(String path) {
        Response response = request().get(path);
        assertEquals(200, response.statusCode(), path);
        return response;
    }

    /** As a user, without decompressing the response or asking for a compressed one. */
    private static RequestSpecification request() {
        RestAssuredConfig config = RestAssured.config()
                .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders());
        return given().config(config)
                .auth().oauth2(AccessTokens.accessToken("alice", Set.of("user")))
                .redirects().follow(false);
    }

    /** E.g. "March 2026". */
    private static String format(String period) {
        Month month = Periods.toYearMonth(period).getMonth();
        return month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + period.substring(0, 4);
    }

    private static void createEntry(Team team, String period, double totalCo2Kg) {
        CarbonEntry entry = new CarbonEntry();
        entry.team = team;
        entry.period = period;
        entry.totalCo2Kg = totalCo2Kg;
        entry.awsCo2Kg = totalCo2Kg;
        entry.persist();
    }
}
