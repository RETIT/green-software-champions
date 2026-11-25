package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.Team;

/** What identifies a team to the users. */
public record TeamInfo(Long id, String name, String color, String icon) {

    static TeamInfo of(Team team) {
        return new TeamInfo(team.id, team.name, team.color, team.icon);
    }
}
