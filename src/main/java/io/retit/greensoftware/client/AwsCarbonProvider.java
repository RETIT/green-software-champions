package io.retit.greensoftware.client;

import io.retit.greensoftware.config.CloudProvidersConfig;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.util.PeriodRange;
import io.retit.greensoftware.util.Periods;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AWS carbon data provider, fetching the estimated emissions of AWS accounts through
 * the {@link AwsCarbonApiClient}. The project IDs are the AWS account IDs. All periods
 * of a range are fetched with a single request.
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
    public Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range) {
        if (!isEnabled()) {
            LOG.debug("AWS carbon provider is disabled; returning empty data.");
            return Map.of();
        }

        List<String> accountIds = projects.stream().map(p -> p.projectId).toList();
        Map<YearMonth, Map<String, Double>> metricTonsByMonth;
        try {
            LOG.infof("Fetching AWS carbon data for accounts %s, periods %s", accountIds, range);
            metricTonsByMonth = apiClient.getEstimatedEmissions(accountIds,
                    range.first(), range.last());
        } catch (Exception e) {
            throw new CarbonDataUnavailableException(String.format(
                    "Failed to fetch AWS carbon data for projects %s, periods %s: %s",
                    accountIds, range, e.getMessage()), e);
        }

        Map<String, List<CarbonData>> results = new HashMap<>();
        for (String period : range.periods()) {
            Map<String, Double> metricTonsByAccount =
                    metricTonsByMonth.getOrDefault(Periods.toYearMonth(period), Map.of());
            // Providers publish a month's data with a delay of up to a few weeks and
            // return no rows until then, so no rows must not be mistaken for zero emissions
            List<String> unpublished = accountIds.stream()
                    .filter(accountId -> !metricTonsByAccount.containsKey(accountId))
                    .toList();
            if (!unpublished.isEmpty()) {
                LOG.warnf("No AWS carbon data published yet for accounts %s, period %s; leaving the period out",
                        unpublished, period);
                continue;
            }
            // AWS API returns metric tons of CO2-equivalent; convert to kg
            results.put(period, accountIds.stream()
                    .map(accountId -> new CarbonData(accountId, metricTonsByAccount.get(accountId) * 1000.0))
                    .toList());
        }
        return results;
    }
}
