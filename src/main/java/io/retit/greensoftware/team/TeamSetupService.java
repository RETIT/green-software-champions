package io.retit.greensoftware.team;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

/**
 * Creates or updates the teams and projects of {@link TeamsConfig} in the database.
 * Teams and projects removed from the configuration are kept.
 */
@ApplicationScoped
public class TeamSetupService {

    private static final Logger LOG = Logger.getLogger(TeamSetupService.class);

    @Inject
    TeamsConfig teamsConfig;

    @Transactional
    public void initialiseTeams() {
        LOG.info("Initialising teams from configuration...");
        for (TeamsConfig.TeamDefinition def : teamsConfig.definitions()) {
            Team team = Team.findByName(def.name());
            if (team == null) {
                team = new Team();
                team.name = def.name();
                LOG.infof("Creating new team: %s", def.name());
            } else {
                LOG.infof("Updating existing team: %s", def.name());
            }
            team.description = def.description().orElse(null);
            team.color = def.color();
            team.icon = def.icon().orElse("🌍");
            team.persist();

            syncProjects(team, def.projects());
        }
        LOG.infof("Team initialisation complete. %d team(s) configured.",
                teamsConfig.definitions().size());
    }

    private void syncProjects(Team team, java.util.List<TeamsConfig.ProjectDefinition> projectDefs) {
        for (TeamsConfig.ProjectDefinition pd : projectDefs) {
            CloudProvider provider;
            try {
                provider = CloudProvider.valueOf(pd.provider().toUpperCase());
            } catch (IllegalArgumentException e) {
                LOG.warnf("Unknown cloud provider '%s' for project '%s' in team '%s'; skipping.",
                        pd.provider(), pd.projectId(), team.name);
                continue;
            }

            boolean exists = CloudProject.findByProviderAndProjectId(provider, pd.projectId())
                    .stream()
                    .anyMatch(p -> p.team.id.equals(team.id));

            if (!exists) {
                CloudProject project = new CloudProject();
                project.team = team;
                project.provider = provider;
                project.projectId = pd.projectId();
                project.projectName = pd.projectName().orElse(pd.projectId());
                project.persist();
                LOG.infof("  Added project %s (%s) to team %s", pd.projectId(), provider, team.name);
            }
        }
    }
}
