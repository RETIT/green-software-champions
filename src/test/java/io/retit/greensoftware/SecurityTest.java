package io.retit.greensoftware;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.restassured.path.json.JsonPath;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;
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

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
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

    /** The header the frontend marks its requests with, see java-script-auto-redirect. */
    private static final String JAVA_SCRIPT_HEADER = "X-Requested-With";

    @TestHTTPResource("/api/me")
    URL meUrl;

    @TestHTTPResource("/api/leaderboard")
    URL leaderboardUrl;

    @TestHTTPResource("/api/admin/sync")
    URL syncUrl;

    @TestHTTPResource("/logout")
    URL logoutUrl;

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
    void api_requestOfFrontendWithoutSession_gets499() {
        given().header(JAVA_SCRIPT_HEADER, "JavaScript").redirects().follow(false)
                .when().get("/api/leaderboard")
                .then().statusCode(499);
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
                .then().statusCode(403);
    }

    /**
     * With the roles in the claim of each kind of identity provider, so that a change
     * of the configuration that makes the application read the roles of only one
     * kind fails. Includes Keycloak's own default roles, which are left out.
     */
    @ParameterizedTest
    @EnumSource(RoleClaim.class)
    void me_withToken_returnsNameAndApplicationRoles(RoleClaim roleClaim) {
        Set<String> roles = Set.of("user", "admin",
                "offline_access", "uma_authorization", "default-roles-green-software");
        given().auth().oauth2(claims("admin", roles, roleClaim).sign())
                .when().get("/api/me")
                .then().statusCode(200)
                .body("name", equalTo("admin"))
                .body("roles", containsInAnyOrder("user", "admin"));
    }

    /**
     * Unlike the leaderboard, /api/me only requires a login, so that the frontend can
     * show a user without access their name and the logout link, to log in as
     * someone else.
     */
    @Test
    void me_withTokenWithoutUserRole_isAllowed() {
        given().auth().oauth2(accessToken("bob", Set.of()))
                .when().get("/api/me")
                .then().statusCode(200)
                .body("name", equalTo("bob"))
                .body("roles", empty());
    }

    @Test
    void adminApi_withTokenOfUser_isForbidden() {
        given().auth().oauth2(accessToken("alice", Set.of("user")))
                .when().post("/api/admin/sync")
                .then().statusCode(403);
    }

    @Test
    void adminApi_withTokenOfAdmin_isAllowed() {
        given().auth().oauth2(accessToken("admin", Set.of("user", "admin")))
                .when().post("/api/admin/sync")
                .then().statusCode(202);
    }

    /**
     * What a script gets once its token has expired: Keycloak's access tokens are
     * only valid for 5 minutes by default. A 401, unlike the redirect to the login
     * a request without any token gets, makes e.g. {@code curl -f} fail.
     */
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
        // A user's token with the claims of one with the role "admin", so that the
        // signature no longer matches the claims
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
    void login_opensSessionForTheFrontendsRequests() throws Exception {
        try (WebClient webClient = createWebClient()) {
            WebResponse me = logIn(webClient, "alice").getWebResponse();
            assertEquals(200, me.getStatusCode());
            JsonPath user = JsonPath.from(me.getContentAsString());
            assertEquals("alice", user.getString("name"));
            assertTrue(user.getList("roles").contains("user"), me.getContentAsString());

            assertEquals(200, fetch(webClient, HttpMethod.GET, leaderboardUrl).getStatusCode());
        }
    }

    @Test
    void adminApi_withSessionOfAdmin_isNotAuthenticated() throws Exception {
        try (WebClient webClient = createWebClient()) {
            WebResponse me = logIn(webClient, "admin").getWebResponse();
            assertTrue(JsonPath.from(me.getContentAsString()).getList("roles").contains("admin"),
                    me.getContentAsString());

            // E.g. a form of another website, posting with the admin's session cookie:
            // the session does not count here, so the request is sent to the login
            // instead of reaching the endpoint
            webClient.getOptions().setRedirectEnabled(false);
            WebResponse response = webClient.loadWebResponse(new WebRequest(syncUrl, HttpMethod.POST));
            assertEquals(302, response.getStatusCode());
            assertTrue(response.getResponseHeaderValue("Location").contains("response_type=code"),
                    response.getResponseHeaderValue("Location"));
        }
    }

    @Test
    void logout_endsSession() throws Exception {
        try (WebClient webClient = createWebClient()) {
            logIn(webClient, "alice");

            webClient.getOptions().setRedirectEnabled(false);
            WebResponse logout = webClient.loadWebResponse(new WebRequest(logoutUrl));
            assertEquals(302, logout.getStatusCode());
            assertTrue(logout.getResponseHeaderValue("Location").contains("/protocol/openid-connect/end-session?"),
                    logout.getResponseHeaderValue("Location"));

            assertEquals(499, fetch(webClient, HttpMethod.GET, meUrl).getStatusCode());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * An access token of the fake OIDC server for {@code userName} with {@code roles}
     * in the claim {@code groups}.
     */
    private static String accessToken(String userName, Set<String> roles) {
        return claims(userName, roles).sign();
    }

    /** {@link #claims(String, Set, RoleClaim)} with the roles in the claim {@code groups}. */
    private static JwtClaimsBuilder claims(String userName, Set<String> roles) {
        return claims(userName, roles, RoleClaim.GROUPS);
    }

    /**
     * The claims of an access token of the fake OIDC server, to be signed with
     * {@link JwtClaimsBuilder#sign()}, which uses the server's key.
     */
    private static JwtClaimsBuilder claims(String userName, Set<String> roles, RoleClaim roleClaim) {
        return roleClaim.add(Jwt.preferredUserName(userName), roles)
                .issuer("https://server.example.com")
                .audience("https://service.example.com");
    }

    /** Where an identity provider puts the user's roles in its tokens. */
    enum RoleClaim {
        /** Keycloak: its realm roles, in realm_access.roles. */
        KEYCLOAK {
            @Override
            JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles) {
                return claims.claim("realm_access", Map.of("roles", List.copyOf(roles)));
            }
        },
        /** The claim of MicroProfile JWT, which e.g. Okta and authentik use. */
        GROUPS {
            @Override
            JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles) {
                return claims.groups(roles);
            }
        },
        /** Microsoft Entra ID: its app roles, in roles. */
        ENTRA_ID {
            @Override
            JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles) {
                return claims.claim("roles", List.copyOf(roles));
            }
        };

        abstract JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles);
    }

    private static WebClient createWebClient() {
        WebClient webClient = new WebClient();
        webClient.setCssErrorHandler(new SilentCssErrorHandler());
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        return webClient;
    }

    /**
     * Opens /api/me like a browser, which redirects to the login form of the fake OIDC
     * server, logs in as {@code userName} (the password is the user name) and returns
     * the page Quarkus redirects back to.
     */
    private Page logIn(WebClient webClient, String userName) throws IOException {
        HtmlPage loginPage = webClient.getPage(meUrl);
        HtmlForm form = loginPage.getForms().getFirst();
        form.getInputByName("username").type(userName);
        form.getInputByName("password").type(userName);
        HtmlSubmitInput login = loginPage.getHtmlElementById("login");
        return login.click();
    }

    /** Sends a request like the frontend's fetch() calls, with the session cookie. */
    private static WebResponse fetch(WebClient webClient, HttpMethod method, URL url) throws IOException {
        WebRequest request = new WebRequest(url, method);
        request.setAdditionalHeader(JAVA_SCRIPT_HEADER, "JavaScript");
        return webClient.loadWebResponse(request);
    }
}
