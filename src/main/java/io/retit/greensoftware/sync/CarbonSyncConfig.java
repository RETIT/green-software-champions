package io.retit.greensoftware.sync;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

@ConfigMapping(prefix = "carbon.sync")
public interface CarbonSyncConfig {

    /** Only mapped for completeness; {@code CarbonSyncScheduler} reads it via {@code @Scheduled}. */
    @WithDefault("0 0 2 25 * ?")
    String cron();

    /**
     * Number of months each sync fetches, ending with the previous month. Older months
     * are re-fetched, as the providers may revise their data.
     */
    @WithDefault("12")
    @Min(1)
    int months();
}
