package com.retit.greensoftware.resource;

import com.retit.greensoftware.dto.CarbonSummaryDto;
import com.retit.greensoftware.dto.LeaderboardEntryDto;
import com.retit.greensoftware.dto.TeamDto;
import com.retit.greensoftware.service.LeaderboardService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

/**
 * REST resource exposing the leaderboard and team data to the single-page frontend.
 * All endpoints require the "user" role (any authenticated Keycloak user).
 */
@Path("/api/leaderboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@Tag(name = "Leaderboard", description = "Carbon footprint leaderboard endpoints")
public class LeaderboardResource {

    @Inject
    LeaderboardService leaderboardService;

    /**
     * Returns the ranked leaderboard for the specified period.
     *
     * @param period Optional period in YYYY-MM format. Defaults to the current month.
     */
    @GET
    @Operation(summary = "Get the carbon footprint leaderboard",
               description = "Returns all teams ranked by their total cloud carbon footprint for the given period.")
    public List<LeaderboardEntryDto> getLeaderboard(
            @QueryParam("period") @DefaultValue("") String period) {
        return leaderboardService.getLeaderboard(period);
    }

    /**
     * Returns company-wide carbon summary for the specified period.
     */
    @GET
    @Path("/summary")
    @Operation(summary = "Get company-wide carbon summary",
               description = "Returns aggregated carbon footprint data across all teams for the given period.")
    public CarbonSummaryDto getSummary(
            @QueryParam("period") @DefaultValue("") String period) {
        return leaderboardService.getCompanySummary(period);
    }

    /**
     * Returns all teams with their cloud project assignments.
     */
    @GET
    @Path("/teams")
    @Operation(summary = "Get all teams", description = "Returns all teams and their configured cloud projects.")
    public List<TeamDto> getTeams() {
        return leaderboardService.getAllTeams();
    }
}
