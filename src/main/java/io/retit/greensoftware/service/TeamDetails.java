package io.retit.greensoftware.service;

import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.entity.Team;

import java.util.List;

/** A team with its projects, which are loaded, unlike {@link Team#projects}. */
public record TeamDetails(Team team, List<CloudProject> projects) {
}
