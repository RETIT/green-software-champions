package io.retit.greensoftware.scheduler;

import io.retit.greensoftware.service.CarbonDataService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Syncs the recent months by default at 02:00 on the 25th, as the providers publish a
 * month's data by about the 19th (Azure) or 21st (AWS, GCP) of the following month.
 * {@link ApplicationStartup} also runs it at startup, when the previous month may not
 * be published yet.
 */
@ApplicationScoped
public class CarbonSyncScheduler {

    private static final Logger LOG = Logger.getLogger(CarbonSyncScheduler.class);

    @Inject
    CarbonDataService carbonDataService;

    @Scheduled(cron = "${carbon.sync.cron:0 0 2 25 * ?}")
    void syncCarbonData() {
        LOG.info("Carbon data sync triggered.");
        try {
            carbonDataService.syncRecentPeriods();
        } catch (Exception e) {
            LOG.errorf("Carbon data sync failed: %s", e.getMessage(), e);
        }
    }
}
