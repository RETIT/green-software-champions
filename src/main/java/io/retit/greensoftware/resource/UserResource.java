package io.retit.greensoftware.resource;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.retit.greensoftware.dto.UserDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * REST resource telling the frontend who is logged in. Unlike the leaderboard, it
 * only requires a login, not the "user" role.
 */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "User", description = "The logged-in user")
public class UserResource {

    /** The roles of this application, leaving out the identity provider's, e.g. offline_access. */
    private static final Set<String> APPLICATION_ROLES = Set.of("user", "admin");

    @Inject
    SecurityIdentity identity;

    @GET
    @Authenticated
    @Operation(summary = "Get the logged-in user",
               description = "Returns the name and the roles of the logged-in user. The name is null "
                       + "if nobody is logged in, which only happens in dev mode.")
    public UserDto getUser() {
        if (identity.isAnonymous()) {
            return new UserDto(null, Set.of());
        }
        return new UserDto(identity.getPrincipal().getName(), identity.getRoles()
                .stream()
                .filter(APPLICATION_ROLES::contains)
                .collect(Collectors.toSet()));
    }
}
