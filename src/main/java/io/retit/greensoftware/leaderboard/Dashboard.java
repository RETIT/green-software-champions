package io.retit.greensoftware.leaderboard;

/** The data of the dashboard for one period. */
public record Dashboard(CompanySummary summary, Leaderboard leaderboard) {

    public String period() {
        return summary.period();
    }

    /** Whether there are no teams, or no data up to the period. */
    public boolean isEmpty() {
        return leaderboard.teams().isEmpty() || summary.history().isEmpty();
    }
}
