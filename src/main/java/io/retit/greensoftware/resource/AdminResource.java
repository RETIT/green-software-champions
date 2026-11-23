package io.retit.greensoftware.resource;

import io.retit.greensoftware.dto.AchievementDto;
import io.retit.greensoftware.entity.Achievement;
import io.retit.greensoftware.entity.Team;
import io.retit.greensoftware.service.AchievementService;
import io.retit.greensoftware.service.CarbonDataService;
import io.retit.greensoftware.util.PeriodRange;
import io.retit.greensoftware.util.Periods;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@Tag(name = "Admin", description = "Administrative endpoints (requires admin role)")
public class AdminResource {

    @Inject
    CarbonDataService carbonDataService;

    @Inject
    AchievementService achievementService;

    @POST
    @Path("/sync")
    @Operation(summary = "Trigger carbon data sync",
               description = "Fetches the carbon data of the last carbon.sync.months months, ending at the "
                       + "previous month, from all enabled cloud providers.")
    public Response triggerSync() {
        PeriodRange range = carbonDataService.syncRecentPeriods();
        return Response.accepted()
                .entity(new SyncResponse("Sync triggered for periods: " + range))
                .build();
    }

    @POST
    @Path("/sync/{period}")
    @Operation(summary = "Trigger carbon data sync for a specific period")
    public Response triggerSyncForPeriod(@PathParam("period") String period) {
        YearMonth month;
        try {
            month = Periods.toYearMonth(period);
        } catch (DateTimeParseException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SyncResponse("Invalid period format. Use YYYY-MM."))
                    .build();
        }
        carbonDataService.syncRange(PeriodRange.of(month));
        return Response.accepted()
                .entity(new SyncResponse("Sync triggered for period: " + month))
                .build();
    }

    @POST
    @Path("/teams/{teamId}/achievements")
    @Consumes(MediaType.APPLICATION_JSON)
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
        return Response.status(Response.Status.CREATED).entity(AchievementDto.from(achievement)).build();
    }

    public record SyncResponse(String message) {}

    public record AchievementRequest(String title, String description, String badgeIcon) {}
}
