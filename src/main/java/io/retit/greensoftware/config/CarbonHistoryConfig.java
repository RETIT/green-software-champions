package io.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import jakarta.validation.constraints.Min;

@ConfigMapping(prefix = "carbon.history")
public interface CarbonHistoryConfig {

    /**
     * Maximum number of months the history charts show, ending with the selected one.
     * 13 to include the same month of the previous year.
     */
    @WithDefault("13")
    @Min(1)
    int months();
}
