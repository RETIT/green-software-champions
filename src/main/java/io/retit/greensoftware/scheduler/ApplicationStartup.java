package io.retit.greensoftware.scheduler;

import io.retit.greensoftware.service.DevSampleDataService;
import io.retit.greensoftware.service.TeamSetupService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Handles application startup events: initialises teams from configuration
 * and runs an initial carbon data sync.
 */
@ApplicationScoped
public class ApplicationStartup {

    private static final Logger LOG = Logger.getLogger(ApplicationStartup.class);

    @Inject
    TeamSetupService teamSetupService;

    @Inject
    CarbonSyncScheduler carbonSyncScheduler;

    /**
     * Injected as {@link Instance} so that the bean is optional:
     * {@link DevSampleDataService} is only active in the "dev" build profile.
     */
    @Inject
    @Any
    Instance<DevSampleDataService> devSampleDataService;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Green Software Champions starting up...");
        teamSetupService.initialiseTeams();

        // Seed sample data when running in dev mode (bean absent in other profiles)
        if (!devSampleDataService.isUnsatisfied()) {
            devSampleDataService.get().seedSampleData();
        }

        // Needs the teams to exist. Runs synchronously, so the startup only completes
        // once the data of the recent months has been fetched from the providers
        carbonSyncScheduler.syncCarbonData();

        LOG.info("Application startup complete.");
    }
}
