package io.retit.greensoftware.team;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/** Reads the teams that {@link TeamSetupService} sets up. */
@ApplicationScoped
public class TeamService {

    @Transactional
    public List<TeamDetails> getAllTeams() {
        return Team.<Team>listAll().stream()
                .map(team -> new TeamDetails(team, CloudProject.findByTeam(team)))
                .toList();
    }
}
