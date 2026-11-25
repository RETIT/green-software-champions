package io.retit.greensoftware.sync.provider;

import io.retit.greensoftware.emissions.PeriodRange;
import io.retit.greensoftware.team.CloudProject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Fetches the carbon emissions of one cloud provider's projects. */
public interface CarbonProvider {

    record CarbonData(String projectId, double co2Kg) {}

    /**
     * Fetches the emissions of {@code projects}, all of this provider, in each period of
     * {@code range}.
     *
     * @return one entry per project by period (YYYY-MM). A period is left out unless
     *         every project's data could be fetched and has been published.
     * @throws CarbonDataUnavailableException if nothing could be fetched, e.g. because
     *         the API is unreachable
     */
    Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range);

    boolean isEnabled();

    /**
     * Implements {@link #fetchCarbonData} for APIs that return one period per request. A
     * period whose {@code fetchPeriod} throws {@link CarbonDataUnavailableException} is
     * left out without costing the other periods their data.
     */
    static Map<String, List<CarbonData>> fetchEachPeriod(PeriodRange range,
                                                         Function<String, List<CarbonData>> fetchPeriod) {
        Map<String, List<CarbonData>> results = new HashMap<>();
        for (String period : range.periods()) {
            try {
                results.put(period, fetchPeriod.apply(period));
            } catch (CarbonDataUnavailableException e) {
                Logger.getLogger(CarbonProvider.class).warnf("Leaving out period %s: %s", period, e.getMessage());
            }
        }
        return results;
    }
}
