package io.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

/**
 * Configuration of the carbon data history.
 */
@ConfigMapping(prefix = "carbon.history")
public interface CarbonHistoryConfig {

    /**
     * Maximum number of months the history charts show, ending at (and including)
     * the selected month. 13 so that the same month of the previous year is included.
     */
    @WithDefault("13")
    @Min(1)
    int months();
}
