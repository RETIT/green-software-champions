package io.retit.greensoftware.api;

import io.retit.greensoftware.leaderboard.LeaderboardService;
import io.retit.greensoftware.team.TeamService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/leaderboard")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed("user")
@Tag(name = "Leaderboard", description = "Carbon footprint leaderboard endpoints")
public class LeaderboardResource {

    @Inject
    LeaderboardService leaderboardService;

    @Inject
    TeamService teamService;

    /** @param period YYYY-MM, or empty for the latest period every team has data for */
    @GET
    @Operation(summary = "Get the carbon footprint leaderboard",
               description = "Returns all teams ranked by their total cloud carbon footprint for the given period.")
    public List<LeaderboardEntryDto> getLeaderboard(
            @QueryParam("period") @DefaultValue("") String period) {
        return LeaderboardEntryDto.from(leaderboardService.getLeaderboard(period));
    }

    /** @param period like {@link #getLeaderboard}; the response names the resolved period */
    @GET
    @Path("/summary")
    @Operation(summary = "Get company-wide carbon summary",
               description = "Returns aggregated carbon footprint data across all teams for the given period.")
    public CarbonSummaryDto getSummary(
            @QueryParam("period") @DefaultValue("") String period) {
        return CarbonSummaryDto.from(leaderboardService.getCompanySummary(period));
    }

    @GET
    @Path("/teams")
    @Operation(summary = "Get all teams", description = "Returns all teams and their configured cloud projects.")
    public List<TeamDto> getTeams() {
        return teamService.getAllTeams().stream().map(TeamDto::from).toList();
    }
}
