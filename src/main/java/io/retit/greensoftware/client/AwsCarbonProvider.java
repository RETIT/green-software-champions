package io.retit.greensoftware.client;

import io.retit.greensoftware.config.CloudProvidersConfig;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.util.Periods;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AWS carbon data provider, fetching the estimated emissions of AWS accounts through
 * the {@link AwsCarbonApiClient}. The project IDs are the AWS account IDs.
 */
@ApplicationScoped
public class AwsCarbonProvider implements CarbonProvider {

    private static final Logger LOG = Logger.getLogger(AwsCarbonProvider.class);

    @Inject
    CloudProvidersConfig config;

    @Inject
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

        List<String> accountIds = projects.stream().map(p -> p.projectId).toList();
        Map<String, Double> metricTonsByAccount;
        try {
            LOG.infof("Fetching AWS carbon data for accounts %s, period %s", accountIds, period);
            metricTonsByAccount = apiClient.getEstimatedEmissions(accountIds, Periods.toYearMonth(period));
        } catch (Exception e) {
            throw new CarbonDataUnavailableException(String.format(
                    "Failed to fetch AWS carbon data for projects %s, period %s: %s",
                    accountIds, period, e.getMessage()), e);
        }

        List<CarbonData> results = new ArrayList<>();
        for (String accountId : accountIds) {
            Double metricTons = metricTonsByAccount.get(accountId);
            // Providers publish a month's data with a delay of up to a few weeks and
            // return no rows until then, so no rows must not be mistaken for zero emissions
            if (metricTons == null) {
                throw new CarbonDataUnavailableException(String.format(
                        "No AWS carbon data published yet for account %s, period %s", accountId, period));
            }
            // AWS API returns metric tons of CO2-equivalent; convert to kg
            results.add(new CarbonData(accountId, metricTons * 1000.0));
        }
        return results;
    }
}
