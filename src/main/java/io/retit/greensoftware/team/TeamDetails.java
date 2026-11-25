package io.retit.greensoftware.team;

import java.util.List;

/** A team with its projects, which are loaded, unlike {@link Team#projects}. */
public record TeamDetails(Team team, List<CloudProject> projects) {
}
