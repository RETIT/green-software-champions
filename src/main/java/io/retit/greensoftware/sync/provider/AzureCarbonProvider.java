package io.retit.greensoftware.sync.provider;

import io.retit.greensoftware.emissions.PeriodRange;
import io.retit.greensoftware.emissions.Periods;
import io.retit.greensoftware.team.CloudProject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Project IDs are subscription or resource group paths. Authenticates with the OAuth2
 * client credentials {@code cloud.azure.tenant-id}, {@code client-id} and
 * {@code client-secret}.
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/carbon-optimization/">
 *      Azure Carbon Optimization</a>
 */
@ApplicationScoped
public class AzureCarbonProvider implements CarbonProvider {

    private static final Logger LOG = Logger.getLogger(AzureCarbonProvider.class);

    @Inject
    CloudProvidersConfig config;

    @Inject
    @RestClient
    AzureCarbonApiClient apiClient;

    @Override
    public boolean isEnabled() {
        return config.azure().enabled();
    }

    @Override
    public Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range) {
        if (!isEnabled()) {
            LOG.debug("Azure carbon provider is disabled; returning empty data.");
            return Map.of();
        }

        String bearerToken = acquireAccessToken();
        if (bearerToken == null) {
            throw new CarbonDataUnavailableException("Could not acquire Azure access token.");
        }

        // The API returns one month per request
        return CarbonProvider.fetchEachPeriod(range, period -> fetchPeriod(projects, period, bearerToken));
    }

    /** @throws CarbonDataUnavailableException if any project's data could not be fetched */
    private List<CarbonData> fetchPeriod(List<CloudProject> projects, String period, String bearerToken) {
        List<CarbonData> results = new ArrayList<>();
        YearMonth ym = Periods.toYearMonth(period);

        for (CloudProject project : projects) {
            try {
                LOG.infof("Fetching Azure carbon data for project %s, period %s", project.projectId, period);
                AzureCarbonApiClient.CarbonEmissionResponse response =
                        apiClient.getCarbonEmissions(
                                "Bearer " + bearerToken,
                                project.projectId,
                                "2014-11-01",
                                ym.getYear(), ym.getMonthValue());
                double co2Kg = extractTotalCo2Kg(response);
                results.add(new CarbonData(project.projectId, co2Kg));
            } catch (Exception e) {
                throw new CarbonDataUnavailableException(String.format(
                        "Failed to fetch Azure carbon data for project %s, period %s: %s",
                        project.projectId, period, e.getMessage()), e);
            }
        }
        return results;
    }

    /** Null if the credentials are missing or no token could be obtained. */
    private String acquireAccessToken() {
        CloudProvidersConfig.AzureConfig azureConfig = config.azure();
        if (azureConfig.tenantId().isEmpty() || azureConfig.clientId().isEmpty()
                || azureConfig.clientSecret().isEmpty()) {
            LOG.warn("Azure credentials not fully configured; cannot acquire token.");
            return null;
        }
        try {
            String tokenEndpoint = "https://login.microsoftonline.com/"
                    + azureConfig.tenantId().get() + "/oauth2/v2.0/token";
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            String body = "grant_type=client_credentials"
                    + "&client_id=" + azureConfig.clientId().get()
                    + "&client_secret=" + java.net.URLEncoder.encode(azureConfig.clientSecret().get(),
                    java.nio.charset.StandardCharsets.UTF_8)
                    + "&scope=https%3A%2F%2Fmanagement.azure.com%2F.default";
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            java.net.http.HttpResponse<String> response =
                    httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            // Parse access_token from JSON response
            String responseBody = response.body();
            int tokenStart = responseBody.indexOf("\"access_token\":\"") + 16;
            int tokenEnd = responseBody.indexOf("\"", tokenStart);
            if (tokenStart > 15 && tokenEnd > tokenStart) {
                return responseBody.substring(tokenStart, tokenEnd);
            }
        } catch (Exception e) {
            LOG.errorf("Failed to acquire Azure access token: %s", e.getMessage());
        }
        return null;
    }

    private double extractTotalCo2Kg(AzureCarbonApiClient.CarbonEmissionResponse response) {
        // No rows means not published yet (up to weeks after the month), not zero emissions
        if (response == null || response.value() == null || response.value().isEmpty()) {
            throw new CarbonDataUnavailableException("No data published yet");
        }
        // Metric tons to kg
        return response.value().stream()
                .mapToDouble(e -> e.totalCarbonEmission() != null ? e.totalCarbonEmission() * 1000.0 : 0.0)
                .sum();
    }
}
