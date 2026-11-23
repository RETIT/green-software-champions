package io.retit.greensoftware;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables OIDC, which the other tests run without, against the fake OIDC server of
 * {@link io.quarkus.test.oidc.server.OidcWiremockTestResource}, with its realm and
 * client.
 */
public class OidcTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.oidc.enabled", "true",
                // No Keycloak container, the fake server replaces it
                "quarkus.keycloak.devservices.enabled", "false",
                // keycloak.url is set by OidcWiremockTestResource
                "quarkus.oidc.auth-server-url", "${keycloak.url}/realms/quarkus/",
                "quarkus.oidc.client-id", "quarkus-web-app",
                "quarkus.oidc.credentials.secret", "secret");
    }
}
