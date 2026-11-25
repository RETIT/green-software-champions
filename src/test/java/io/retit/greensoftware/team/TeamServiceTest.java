package io.retit.greensoftware.team;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TeamServiceTest {

    @Inject
    TeamService teamService;

    private static final String TEAM_ALPHA = "Team Alpha";

    @Test
    void getAllTeams_returnsTeams() {
        var teams = teamService.getAllTeams();
        assertNotNull(teams);
        assertFalse(teams.isEmpty());
        assertTrue(teams.stream().anyMatch(t -> TEAM_ALPHA.equals(t.team().name)),
                TEAM_ALPHA + " should exist");
    }

    @Test
    void getAllTeams_includesCloudProjectsPerTeam() {
        var teams = teamService.getAllTeams();
        var alpha = teams.stream()
                .filter(t -> TEAM_ALPHA.equals(t.team().name))
                .findFirst().orElseThrow(() -> new AssertionError(TEAM_ALPHA + " not found"));
        assertFalse(alpha.projects().isEmpty(), TEAM_ALPHA + " should have cloud projects");
    }
}
