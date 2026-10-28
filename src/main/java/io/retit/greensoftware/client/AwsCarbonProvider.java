package io.retit.greensoftware.client;

import io.retit.greensoftware.config.CloudProvidersConfig;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.util.Periods;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * AWS Carbon Footprint data provider.
 *
 * <p>Connects to the AWS Sustainability Carbon Footprint API
 * (https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_CarbonFootprint.html).
 *
 * <p><b>Authentication:</b> The AWS Carbon Footprint API requires AWS Signature Version 4 (SigV4)
 * authentication. In production, configure {@code cloud.aws.access-key} and
 * {@code cloud.aws.secret-key} (or use IAM roles / instance profiles).
 * The {@link AwsCarbonApiClient} REST client must be augmented with a SigV4 request filter
 * for production use.
 */
@ApplicationScoped
public class AwsCarbonProvider implements CarbonProvider {

    private static final Logger LOG = Logger.getLogger(AwsCarbonProvider.class);

    @Inject
    CloudProvidersConfig config;

    @Inject
    @RestClient
    AwsCarbonApiClient apiClient;

    @Override
    public boolean isEnabled() {
        return config.aws().enabled();
    }

    @Override
    public List<CarbonData> fetchCarbonData(List<CloudProject> projects, String period) {
        if (!isEnabled()) {
            LOG.debug("AWS carbon provider is disabled; returning empty data.");
            return List.of();
        }

        List<CarbonData> results = new ArrayList<>();
        // Convert the period to start/end timestamps for the AWS API
        YearMonth ym = Periods.toYearMonth(period);
        String startTime = ym.atDay(1) + "T00:00:00Z";
        String endTime = ym.atEndOfMonth() + "T23:59:59Z";

        for (CloudProject project : projects) {
            try {
                LOG.infof("Fetching AWS carbon data for project %s, period %s", project.projectId, period);
                AwsCarbonApiClient.CarbonFootprintResponse response =
                        apiClient.getCarbonFootprintSummary(project.projectId, startTime, endTime, "METRIC_TON");
                double co2Kg = extractTotalCo2Kg(response);
                results.add(new CarbonData(project.projectId, co2Kg));
            } catch (Exception e) {
                throw new CarbonDataUnavailableException(String.format(
                        "Failed to fetch AWS carbon data for project %s, period %s: %s",
                        project.projectId, period, e.getMessage()), e);
            }
        }
        return results;
    }

    private double extractTotalCo2Kg(AwsCarbonApiClient.CarbonFootprintResponse response) {
        // Providers publish a month's data with a delay of up to a few weeks and
        // return no rows until then, so no rows must not be mistaken for zero emissions
        if (response == null || response.carbonFootprintSummary() == null || response.carbonFootprintSummary().isEmpty()) {
            throw new CarbonDataUnavailableException("No data published yet");
        }
        // AWS API returns metric tons of CO2-equivalent; convert to kg
        double metricTons = response.carbonFootprintSummary().stream()
                .mapToDouble(s -> s.value() != null ? s.value() : 0.0)
                .sum();
        return metricTons * 1000.0;
    }
}
