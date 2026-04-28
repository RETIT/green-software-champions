package com.retit.greensoftware.service;

import com.retit.greensoftware.entity.Achievement;
import com.retit.greensoftware.entity.AchievementType;
import com.retit.greensoftware.entity.CarbonEntry;
import com.retit.greensoftware.entity.Team;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Inserts realistic sample carbon data in dev mode so the UI is immediately useful
 * without requiring live cloud-provider API credentials.
 *
 * Only active when the "dev" build profile is selected ({@code mvn quarkus:dev}).
 * Idempotent: already-existing entries are skipped on every restart.
 */
@ApplicationScoped
@IfBuildProfile("dev")
public class DevSampleDataService {

    private static final Logger LOG = Logger.getLogger(DevSampleDataService.class);
    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * Seeds sample carbon entries and achievements for all configured teams.
     * Must be called after {@link TeamSetupService#initialiseTeams()} so that
     * {@link Team} records already exist in the database.
     */
    @Transactional
    public void seedSampleData() {
        String current  = LocalDate.now().format(PERIOD_FMT);
        String previous = LocalDate.now().minusMonths(1).format(PERIOD_FMT);

        // -----------------------------------------------------------------------
        // Carbon entries per team
        // Fields: teamName | currentTotal | awsCurr | azureCurr | gcpCurr
        //                   | prevTotal   | awsPrev  | azurePrev | gcpPrev
        // null = provider not used by that team
        // -----------------------------------------------------------------------
        List<SampleEntry> entries = List.of(
            new SampleEntry("Team Alpha",
                /*current*/ 245.5, 180.2, null,  65.3,
                /*prev*/    312.3, 237.8, null,  74.5),
            new SampleEntry("Team Beta",
                /*current*/ 189.2,  98.4, 90.8,  null,
                /*prev*/    167.8,  82.1, 85.7,  null),
            new SampleEntry("Team Gamma",
                /*current*/ 128.4,  null, 42.1,  86.3,
                /*prev*/    189.1,  null, 61.4, 127.7),
            new SampleEntry("Team Delta",
                /*current*/ 412.7, 201.3, 112.8,  98.6,
                /*prev*/    445.2, 220.5, 118.4, 106.3)
        );

        int inserted = 0;
        for (SampleEntry s : entries) {
            Team team = Team.findByName(s.teamName);
            if (team == null) {
                LOG.warnf("Dev sample data: team '%s' not found – run TeamSetupService first.", s.teamName);
                continue;
            }

            // Previous-period entry (no reduction figure – this IS the baseline)
            if (CarbonEntry.findByTeamAndPeriod(team, previous).isEmpty()) {
                CarbonEntry prev = new CarbonEntry();
                prev.team           = team;
                prev.period         = previous;
                prev.totalCo2Kg     = s.prevTotal;
                prev.awsCo2Kg       = s.awsPrev;
                prev.azureCo2Kg     = s.azurePrev;
                prev.gcpCo2Kg       = s.gcpPrev;
                prev.syncTimestamp  = Instant.now();
                prev.persist();
                inserted++;
            }

            // Current-period entry (includes comparison to previous)
            if (CarbonEntry.findByTeamAndPeriod(team, current).isEmpty()) {
                double reduction = s.prevTotal > 0
                        ? ((s.prevTotal - s.currentTotal) / s.prevTotal) * 100.0
                        : 0.0;

                CarbonEntry curr = new CarbonEntry();
                curr.team                = team;
                curr.period              = current;
                curr.totalCo2Kg          = s.currentTotal;
                curr.previousCo2Kg       = s.prevTotal;
                curr.reductionPercentage = reduction;
                curr.awsCo2Kg            = s.awsCurrent;
                curr.azureCo2Kg          = s.azureCurrent;
                curr.gcpCo2Kg            = s.gcpCurrent;
                curr.syncTimestamp       = Instant.now();
                curr.persist();
                inserted++;
            }
        }

        seedAchievements();
        LOG.infof("Dev sample data seeding complete – %d carbon entr%s inserted.",
                inserted, inserted == 1 ? "y" : "ies");
    }

    // -------------------------------------------------------------------------
    // Achievements
    // -------------------------------------------------------------------------

    private void seedAchievements() {
        // Winner: biggest relative reduction
        insertAchievement("Team Gamma",  "🌱 Green Streak",
                "Three months of consecutive carbon reduction!",     "🌱", AchievementType.GREEN_STREAK);
        insertAchievement("Team Gamma",  "⚡ Efficiency Expert",
                "Reduced cloud carbon footprint by over 32 % in a single month.", "⚡", AchievementType.EFFICIENCY_EXPERT);

        // Runner-up: absolute lowest footprint history
        insertAchievement("Team Alpha",  "🏆 Leaderboard Leader",
                "Held the top spot on the leaderboard last month.",  "🏆", AchievementType.LEADERBOARD_LEADER);

        // Large team with good absolute saving
        insertAchievement("Team Delta",  "💧 Big Saver",
                "Saved over 32 kg CO₂ compared to the previous month.", "💧", AchievementType.BIG_SAVER);
    }

    private void insertAchievement(String teamName, String title, String description,
                                   String icon, AchievementType type) {
        Team team = Team.findByName(teamName);
        if (team == null) return;
        long exists = Achievement.count("team = ?1 and type = ?2", team, type);
        if (exists == 0) {
            Achievement a = new Achievement();
            a.team        = team;
            a.title       = title;
            a.description = description;
            a.badgeIcon   = icon;
            a.earnedAt    = Instant.now();
            a.type        = type;
            a.persist();
        }
    }

    // -------------------------------------------------------------------------
    // Internal data holder
    // -------------------------------------------------------------------------

    private record SampleEntry(
            String teamName,
            // current period
            double currentTotal, Double awsCurrent, Double azureCurrent, Double gcpCurrent,
            // previous period
            double prevTotal,    Double awsPrev,    Double azurePrev,    Double gcpPrev
    ) {}
}

