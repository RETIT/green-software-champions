package io.retit.greensoftware;

import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.build.JwtClaimsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Access tokens of the fake OIDC server of {@link OidcWiremockTestResource}. */
final class AccessTokens {

    private AccessTokens() {
    }

    /** With the roles in the claim {@code groups}. */
    static String accessToken(String userName, Set<String> roles) {
        return claims(userName, roles).sign();
    }

    static JwtClaimsBuilder claims(String userName, Set<String> roles) {
        return claims(userName, roles, RoleClaim.GROUPS);
    }

    /** To be signed with {@link JwtClaimsBuilder#sign()}, which uses the fake OIDC server's key. */
    static JwtClaimsBuilder claims(String userName, Set<String> roles, RoleClaim roleClaim) {
        return roleClaim.add(Jwt.preferredUserName(userName), roles)
                .issuer("https://server.example.com")
                .audience("https://service.example.com");
    }

    /** Where an identity provider puts the user's roles in its tokens. */
    enum RoleClaim {
        KEYCLOAK {
            @Override
            JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles) {
                return claims.claim("realm_access", Map.of("roles", List.copyOf(roles)));
            }
        },
        /** MicroProfile JWT's claim, used e.g. by Okta and authentik. */
        GROUPS {
            @Override
            JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles) {
                return claims.groups(roles);
            }
        },
        ENTRA_ID {
            @Override
            JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles) {
                return claims.claim("roles", List.copyOf(roles));
            }
        };

        abstract JwtClaimsBuilder add(JwtClaimsBuilder claims, Set<String> roles);
    }
}
