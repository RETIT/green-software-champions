package io.retit.greensoftware.scheduler;

import io.retit.greensoftware.service.CarbonDataService;
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
 * and runs an initial carbon data sync, or, in dev mode without any enabled
 * cloud provider, seeds sample data instead.
 */
@ApplicationScoped
public class ApplicationStartup {

    private static final Logger LOG = Logger.getLogger(ApplicationStartup.class);

    @Inject
    TeamSetupService teamSetupService;

    @Inject
    CarbonSyncScheduler carbonSyncScheduler;

    @Inject
    CarbonDataService carbonDataService;

    /**
     * Injected as {@link Instance} so that the bean is optional:
     * {@link DevSampleDataService} is only active in the "dev" build profile.
     */
    @Inject
    @Any
    Instance<DevSampleDataService> devSampleDataService;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Green Software Champions starting up...");
        // Both the sync and the sample data need the teams to exist
        teamSetupService.initialiseTeams();

        // Real data from the providers or sample data, never both
        if (carbonDataService.anyProviderEnabled()) {
            // Runs synchronously, so the startup only completes once the data
            // of the recent months has been fetched from the providers
            carbonSyncScheduler.syncCarbonData();
        } else if (devSampleDataService.isResolvable()) {
            // Dev mode only (bean absent in other profiles)
            devSampleDataService.get().seedSampleData();
        } else {
            LOG.info("No cloud provider enabled, skipping the initial carbon data sync.");
        }

        LOG.info("Application startup complete.");
    }
}
