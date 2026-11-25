package io.retit.greensoftware;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.retit.greensoftware.AccessTokens.RoleClaim;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.SilentCssErrorHandler;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSubmitInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static io.retit.greensoftware.AccessTokens.accessToken;
import static io.retit.greensoftware.AccessTokens.claims;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the login of the dashboard (authorization code flow with a session cookie),
 * the bearer tokens of the API and which of them each path accepts, against the fake
 * OIDC server of {@link OidcWiremockTestResource}. Its login form accepts the users
 * "alice" and "admin", both with the roles "user" and "admin".
 */
@QuarkusTest
@TestProfile(OidcTestProfile.class)
@WithTestResource(OidcWiremockTestResource.class)
class SecurityTest {

    @TestHTTPResource("/")
    URL dashboardUrl;

    @TestHTTPResource("/map?period=2026-03")
    URL mapUrl;

    @TestHTTPResource("/")
    URI baseUri;

    // -------------------------------------------------------------------------
    // Without login
    // -------------------------------------------------------------------------

    @Test
    void dashboard_withoutSession_redirectsToLogin() {
        given().redirects().follow(false)
                .when().get("/")
                .then().statusCode(302)
                .header("Location", containsString("/realms/quarkus?response_type=code"));
    }

    @Test
    void healthCheck_needsNoLogin() {
        given().redirects().follow(false)
                .when().get("/q/health")
                .then().statusCode(200);
    }

    // -------------------------------------------------------------------------
    // Bearer tokens
    // -------------------------------------------------------------------------

    @Test
    void leaderboard_withTokenOfUser_isAllowed() {
        given().auth().oauth2(accessToken("alice", Set.of("user")))
                .when().get("/api/leaderboard")
                .then().statusCode(200);
    }

    @Test
    void leaderboard_withTokenWithoutUserRole_isForbidden() {
        given().auth().oauth2(accessToken("bob", Set.of()))
                .when().get("/api/leaderboard")
                .then().statusCode(403)
                .body(not(containsString("<html")));
    }

    /** Covers each identity provider's role claim. */
    @ParameterizedTest
    @EnumSource(RoleClaim.class)
    void leaderboard_withTokenWithRoleInClaim_isAllowed(RoleClaim roleClaim) {
        Set<String> roles = Set.of("user", "offline_access", "default-roles-green-software");
        given().auth().oauth2(claims("alice", roles, roleClaim).sign())
                .when().get("/api/leaderboard")
                .then().statusCode(200);
    }

    @ParameterizedTest
    @EnumSource(RoleClaim.class)
    void leaderboard_withTokenWithOtherRolesInClaim_isForbidden(RoleClaim roleClaim) {
        Set<String> roles = Set.of("offline_access", "default-roles-green-software");
        given().auth().oauth2(claims("bob", roles, roleClaim).sign())
                .when().get("/api/leaderboard")
                .then().statusCode(403);
    }

    /** With the log-out link, to log in as someone else. */
    @ParameterizedTest
    @ValueSource(strings = {"/", "/map", "/?period=2026-03", "/demo", "/demo/map"})
    void page_withTokenWithoutUserRole_explainsThatAccessIsMissing(String path) {
        given().auth().oauth2(accessToken("bob", Set.of()))
                .when().get(path)
                .then().statusCode(403)
                .contentType("text/html")
                .header("Cache-Control", "no-store")
                .body(containsString("Ask an administrator"))
                .body(containsString("href=\"/logout\""))
                .body(not(containsString("Company CO₂e")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/sync", "/api/admin/pages/rebuild"})
    void adminApi_withTokenOfUser_isForbidden(String path) {
        given().auth().oauth2(accessToken("alice", Set.of("user")))
                .when().post(path)
                .then().statusCode(403);
    }

    @Test
    void adminApi_withTokenOfAdmin_isAllowed() {
        given().auth().oauth2(accessToken("admin", Set.of("user", "admin")))
                .when().post("/api/admin/sync")
                .then().statusCode(202);
    }

    /** A 401 rather than a redirect to the login, so that e.g. {@code curl -f} fails. */
    @Test
    void adminApi_withExpiredTokenOfAdmin_isUnauthorized() {
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        String expired = claims("admin", Set.of("user", "admin"))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(5)))
                .sign();

        given().auth().oauth2(expired).redirects().follow(false)
                .when().post("/api/admin/sync")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"));
    }

    @Test
    void adminApi_withTamperedTokenOfUser_isUnauthorized() {
        // The user's signature with an admin's claims
        String[] userToken = accessToken("alice", Set.of("user")).split("\\.");
        String adminClaims = accessToken("alice", Set.of("user", "admin")).split("\\.")[1];
        String tampered = userToken[0] + "." + adminClaims + "." + userToken[2];

        given().auth().oauth2(tampered).redirects().follow(false)
                .when().post("/api/admin/sync")
                .then().statusCode(401)
                .header("WWW-Authenticate", containsString("Bearer"));
    }

    // -------------------------------------------------------------------------
    // Login of the dashboard (session cookie)
    // -------------------------------------------------------------------------

    @Test
    void login_leadsToTheDashboard() throws Exception {
        try (WebClient webClient = createWebClient()) {
            Page dashboard = logIn(webClient, "alice");
            assertEquals(200, dashboard.getWebResponse().getStatusCode());
            assertTrue(dashboard.getWebResponse().getContentAsString().contains("Carbon Footprint Leaderboard")
                            || dashboard.getWebResponse().getContentAsString().contains("No leaderboard data"),
                    dashboard.getWebResponse().getContentAsString());

            assertEquals(200, webClient.getPage(baseUri.resolve("map").toURL()).getWebResponse().getStatusCode(),
                    "The session should count for the next page");
        }
    }

    /** So that links to a period work without a session. */
    @Test
    void login_keepsThePathAndPeriod() throws Exception {
        try (WebClient webClient = createWebClient()) {
            URL url = logIn(webClient, "alice", mapUrl).getUrl();
            assertEquals("/map?period=2026-03", url.getFile());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"api/admin/sync", "api/admin/pages/rebuild"})
    void adminApi_withSessionOfAdmin_isNotAuthenticated(String path) throws Exception {
        try (WebClient webClient = createWebClient()) {
            assertEquals(200, logIn(webClient, "admin").getWebResponse().getStatusCode());

            // Like another website's form posting with the admin's session cookie (CSRF):
            // the session does not count here, so the request is sent to the login
            webClient.getOptions().setRedirectEnabled(false);
            WebResponse response = webClient.loadWebResponse(
                    new WebRequest(baseUri.resolve(path).toURL(), HttpMethod.POST));
            assertRedirectsToLogin(response);
        }
    }

    @Test
    void logout_endsSession() throws Exception {
        try (WebClient webClient = createWebClient()) {
            HtmlPage dashboard = (HtmlPage) logIn(webClient, "alice");
            String logoutPath = dashboard.getAnchorByText("Log out").getHrefAttribute();

            webClient.getOptions().setRedirectEnabled(false);
            WebResponse logout = webClient.loadWebResponse(new WebRequest(baseUri.resolve(logoutPath).toURL()));
            assertEquals(302, logout.getStatusCode());
            assertTrue(logout.getResponseHeaderValue("Location").contains("/protocol/openid-connect/end-session?"),
                    logout.getResponseHeaderValue("Location"));

            assertRedirectsToLogin(webClient.loadWebResponse(new WebRequest(dashboardUrl)));
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static WebClient createWebClient() {
        WebClient webClient = new WebClient();
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        return webClient;
    }

    private Page logIn(WebClient webClient, String userName) throws IOException {
        return logIn(webClient, userName, dashboardUrl);
    }

    /**
     * Opens {@code url}, logs in at the fake OIDC server's form as {@code userName} (the
     * password is the user name) and returns the page Quarkus redirects back to.
     */
    private static Page logIn(WebClient webClient, String userName, URL url) throws IOException {
        HtmlPage loginPage = webClient.getPage(url);
        HtmlForm form = loginPage.getForms().getFirst();
        form.getInputByName("username").type(userName);
        form.getInputByName("password").type(userName);
        HtmlSubmitInput login = loginPage.getHtmlElementById("login");
        return login.click();
    }

    private static void assertRedirectsToLogin(WebResponse response) {
        assertEquals(302, response.getStatusCode());
        assertTrue(response.getResponseHeaderValue("Location").contains("response_type=code"),
                response.getResponseHeaderValue("Location"));
    }
}
