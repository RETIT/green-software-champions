package io.retit.greensoftware.resource;

import io.retit.greensoftware.dto.AchievementDto;
import io.retit.greensoftware.service.AchievementService;
import io.retit.greensoftware.service.AwardedAchievement;
import io.retit.greensoftware.service.CarbonDataService;
import io.retit.greensoftware.util.PeriodRange;
import io.retit.greensoftware.util.Periods;
import io.retit.greensoftware.web.PageService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("admin")
@Tag(name = "Admin", description = "Administrative endpoints (requires admin role)")
public class AdminResource {

    @Inject
    CarbonDataService carbonDataService;

    @Inject
    AchievementService achievementService;

    @Inject
    PageService pageService;

    @POST
    @Path("/sync")
    @Operation(summary = "Trigger carbon data sync",
               description = "Fetches the carbon data of the last carbon.sync.months months, ending at the "
                       + "previous month, from all enabled cloud providers.")
    public Response triggerSync() {
        PeriodRange range = carbonDataService.syncRecentPeriods();
        return Response.accepted()
                .entity(new MessageResponse("Sync triggered for periods: " + range))
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
                    .entity(new MessageResponse("Invalid period format. Use YYYY-MM."))
                    .build();
        }
        carbonDataService.syncRange(PeriodRange.of(month));
        return Response.accepted()
                .entity(new MessageResponse("Sync triggered for period: " + month))
                .build();
    }

    @POST
    @Path("/teams/{teamId}/achievements")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Grant a custom achievement to a team",
               description = "Allows IT leadership to award a custom achievement (shoutout) to any team.")
    public Response grantAchievement(
            @PathParam("teamId") Long teamId,
            AchievementRequest request) {
        Optional<AwardedAchievement> achievement = achievementService.grantCustomAchievement(
                teamId, request.title(), request.description(), request.badgeIcon());
        if (achievement.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new MessageResponse("Team not found: " + teamId))
                    .build();
        }
        return Response.status(Response.Status.CREATED).entity(AchievementDto.from(achievement.get())).build();
    }

    @POST
    @Path("/pages/rebuild")
    @Operation(summary = "Render the dashboard's pages again",
               description = "Happens by itself whenever the data changes, so this is only needed after "
                       + "rendering has failed.")
    public Response rebuildPages() {
        if (!pageService.refresh()) {
            return Response.serverError()
                    .entity(new MessageResponse("Rendering the pages failed, see the log. The previous pages are kept."))
                    .build();
        }
        return Response.noContent().build();
    }

    /** Of a success or an error. */
    public record MessageResponse(String message) {}

    public record AchievementRequest(String title, String description, String badgeIcon) {}
}
