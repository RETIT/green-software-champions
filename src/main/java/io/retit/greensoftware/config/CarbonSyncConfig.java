package io.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

/**
 * Configuration of the carbon data sync.
 */
@ConfigMapping(prefix = "carbon.sync")
public interface CarbonSyncConfig {

    /**
     * Cron expression of the scheduled sync. Read by {@code CarbonSyncScheduler} via
     * {@code @Scheduled}, mapped here so the whole {@code carbon.sync} namespace is
     * documented in one place.
     */
    @WithDefault("0 0 2 25 * ?")
    String cron();

    /**
     * Number of months each sync fetches, ending at (and including) the previous
     * month. Older months are re-fetched because the providers may revise their data.
     * Independent of {@link CarbonHistoryConfig#months()}, the number of months the
     * history charts show.
     */
    @WithDefault("12")
    @Min(1)
    int months();
}
