package io.retit.greensoftware.scheduler;

import io.retit.greensoftware.service.CarbonDataService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Scheduled job that periodically fetches carbon emission data from all enabled
 * cloud providers. By default, runs on the 25th of each month at 02:00 UTC.
 *
 * <p>The providers publish a month's data with a delay: GCP by about the 21st,
 * Azure by about the 19th and AWS by about the 21st of the following month.
 * Running on the 25th makes sure the previous month is available everywhere.
 * Each sync also re-fetches the months before it ({@code carbon.sync.months} in
 * total), as the providers may revise already published data.
 *
 * <p>{@link ApplicationStartup} also runs a sync at every startup with an enabled cloud
 * provider and waits for it, so a new installation does not have to wait for the next
 * scheduled sync. Before the 25th, the providers may not have published the previous
 * month yet. It then stays incomplete until a later sync.
 *
 * <p>The schedule can be overridden in application.yaml via
 * {@code quarkus.scheduler.cron-type} or by providing a custom cron expression
 * for {@code carbon.sync.cron}.
 */
@ApplicationScoped
public class CarbonSyncScheduler {

    private static final Logger LOG = Logger.getLogger(CarbonSyncScheduler.class);

    @Inject
    CarbonDataService carbonDataService;

    /**
     * Runs on the 25th of each month at 02:00 UTC and at startup to sync the carbon
     * footprint data of the recent months from all configured cloud providers.
     *
     * <p>The cron expression {@code 0 0 2 25 * ?} means:
     * at 02:00:00 on the 25th day of every month.
     */
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
