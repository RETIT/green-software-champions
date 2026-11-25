package io.retit.greensoftware.service;

import java.util.List;

/** All teams by their emissions in {@code period}, best first. */
public record Leaderboard(String period, List<RankedTeam> teams) {
}
