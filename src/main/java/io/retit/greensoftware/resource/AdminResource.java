package io.retit.greensoftware.resource;

import io.retit.greensoftware.dto.AchievementDto;
import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.AchievementService;
import io.retit.greensoftware.service.CarbonDataService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * Admin-only REST resource for triggering data synchronisation and managing achievements.
 * Requires the "admin" Keycloak role.
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@Tag(name = "Admin", description = "Administrative endpoints (requires admin role)")
public class AdminResource {

    @Inject
    CarbonDataService carbonDataService;

    @Inject
    AchievementService achievementService;

    /**
     * Triggers an immediate carbon data sync for the recent months, the same as the
     * scheduled sync does.
     */
    @POST
    @Path("/sync")
    @Operation(summary = "Trigger carbon data sync",
               description = "Fetches the carbon data of the last carbon.sync.months months, ending at the "
                       + "previous month, from all enabled cloud providers.")
    public Response triggerSync() {
        List<String> periods = carbonDataService.syncRecentPeriods();
        return Response.accepted()
                .entity(new SyncResponse("Sync triggered for periods: "
                        + periods.getFirst() + " to " + periods.getLast()))
                .build();
    }

    /**
     * Triggers a carbon data sync for a specific period.
     *
     * @param period Period in YYYY-MM format.
     */
    @POST
    @Path("/sync/{period}")
    @Operation(summary = "Trigger carbon data sync for a specific period")
    public Response triggerSyncForPeriod(@PathParam("period") String period) {
        if (!period.matches("\\d{4}-\\d{2}")) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SyncResponse("Invalid period format. Use YYYY-MM."))
                    .build();
        }
        carbonDataService.syncForPeriod(period);
        return Response.accepted()
                .entity(new SyncResponse("Sync triggered for period: " + period))
                .build();
    }

    /**
     * Grants a custom achievement to a team (IT leadership shoutout).
     *
     * @param teamId  Target team ID.
     * @param request Achievement details.
     */
    @POST
    @Path("/teams/{teamId}/achievements")
    @Transactional
    @Operation(summary = "Grant a custom achievement to a team",
               description = "Allows IT leadership to award a custom achievement (shoutout) to any team.")
    public Response grantAchievement(
            @PathParam("teamId") Long teamId,
            AchievementRequest request) {
        Team team = Team.findById(teamId);
        if (team == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new SyncResponse("Team not found: " + teamId))
                    .build();
        }
        Achievement achievement = achievementService.grantCustomAchievement(
                team, request.title(), request.description(), request.badgeIcon());

        AchievementDto dto = new AchievementDto();
        dto.id = achievement.id;
        dto.title = achievement.title;
        dto.description = achievement.description;
        dto.badgeIcon = achievement.badgeIcon;
        dto.period = achievement.period;
        dto.type = achievement.type.name();
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    /** Simple response wrapper. */
    public record SyncResponse(String message) {}

    /** Request body for granting a custom achievement. */
    public record AchievementRequest(String title, String description, String badgeIcon) {}
}
