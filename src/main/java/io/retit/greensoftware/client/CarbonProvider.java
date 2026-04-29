package io.retit.greensoftware.client;

import io.retit.greensoftware.entity.CloudProject;

import java.util.List;

/**
 * Common interface for all cloud provider carbon footprint data providers.
 * Each cloud provider implementation fetches carbon emission data for the
 * given projects and reporting period.
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
     * Fetch carbon footprint data for the specified projects in the given period.
     *
     * @param projects Cloud projects to fetch data for (all must belong to this provider).
     * @param period   Reporting period in "YYYY-MM" format.
     * @return List of carbon data records, one per project.
     */
    List<CarbonData> fetchCarbonData(List<CloudProject> projects, String period);

    /**
     * Check whether this provider is enabled in the current configuration.
     */
    boolean isEnabled();
}
