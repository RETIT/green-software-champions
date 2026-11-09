package io.retit.greensoftware.client;

import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.util.PeriodRange;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Common interface for all cloud provider carbon footprint data providers.
 * Each cloud provider implementation fetches carbon emission data for the
 * given projects and range of reporting periods.
 */
public interface CarbonProvider {

    /**
     * Result record holding carbon footprint data for a single cloud project.
     *
     * @param projectId  The cloud project identifier.
     * @param co2Kg      Total CO2-equivalent emissions in kg.
     */
    record CarbonData(String projectId, double co2Kg) {}

    /**
     * Fetch carbon footprint data for the specified projects in every period of the range.
     *
     * @param projects Cloud projects to fetch data for (all must belong to this provider).
     * @param range    Reporting periods to fetch.
     * @return The carbon data records by period (YYYY-MM), one per project. A period is
     *         missing if the data of any project could not be fetched or has not been
     *         published yet; partial results of a period are never returned.
     * @throws CarbonDataUnavailableException if the data could not be fetched at all,
     *         e.g., because the API is unreachable.
     */
    Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range);

    /**
     * Check whether this provider is enabled in the current configuration.
     */
    boolean isEnabled();

    /**
     * Implements {@link #fetchCarbonData(List, PeriodRange)} for APIs that return one
     * period per request, by calling {@code fetchPeriod} for each period of
     * {@code range}. A period whose fetch throws {@link CarbonDataUnavailableException}
     * is left out, so that it does not cost the other periods their data.
     *
     * @param fetchPeriod Fetches the data of all projects in the given period.
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
