package io.retit.greensoftware;

import io.retit.greensoftware.demo.DevSampleDataService;
import io.retit.greensoftware.sync.CarbonSyncScheduler;
import io.retit.greensoftware.sync.CarbonSyncService;
import io.retit.greensoftware.team.TeamSetupService;
import io.retit.greensoftware.web.PageService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ApplicationStartup {

    private static final Logger LOG = Logger.getLogger(ApplicationStartup.class);

    @Inject
    TeamSetupService teamSetupService;

    @Inject
    CarbonSyncScheduler carbonSyncScheduler;

    @Inject
    CarbonSyncService carbonSyncService;

    @Inject
    PageService pageService;

    /** Only resolvable in the dev build profile. */
    @Inject
    @Any
    Instance<DevSampleDataService> devSampleDataService;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Green Software Champions starting up...");
        // Both the sync and the sample data need the teams to exist
        teamSetupService.initialiseTeams();

        if (carbonSyncService.anyProviderEnabled()) {
            // Blocks the startup until the recent months have been fetched
            carbonSyncScheduler.syncCarbonData();
        } else if (devSampleDataService.isResolvable()) {
            devSampleDataService.get().seedSampleData();
        } else {
            LOG.info("No cloud provider enabled, skipping the initial carbon data sync.");
        }

        // Redundant after a sync, but needed without one, also for changed teams
        pageService.refresh();
        LOG.info("Application startup complete.");
    }
}
