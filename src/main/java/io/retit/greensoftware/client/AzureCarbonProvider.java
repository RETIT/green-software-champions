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
 * Azure carbon footprint data provider.
 *
 * <p>Connects to the Microsoft Cloud for Sustainability / Azure Resource Manager API
 * to retrieve carbon emissions data per resource group or subscription.
 *
 * <p><b>Authentication:</b> Uses OAuth2 client credentials flow against Azure Active Directory.
 * Configure {@code cloud.azure.tenant-id}, {@code cloud.azure.client-id}, and
 * {@code cloud.azure.client-secret} to enable token acquisition.
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
    public List<CarbonData> fetchCarbonData(List<CloudProject> projects, String period) {
        if (!isEnabled()) {
            LOG.debug("Azure carbon provider is disabled; returning empty data.");
            return List.of();
        }

        String bearerToken = acquireAccessToken();
        if (bearerToken == null) {
            throw new CarbonDataUnavailableException("Could not acquire Azure access token.");
        }

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

    /**
     * Acquires an OAuth2 access token from Azure Active Directory using the
     * client credentials flow.
     */
    private String acquireAccessToken() {
        CloudProvidersConfig.AzureConfig azureConfig = config.azure();
        if (azureConfig.tenantId().isEmpty() || azureConfig.clientId().isEmpty()
                || azureConfig.clientSecret().isEmpty()) {
            LOG.warn("Azure credentials not fully configured; cannot acquire token.");
            return null;
        }
        try {
            // Use the Azure AD token endpoint
            String tokenEndpoint = "https://login.microsoftonline.com/"
                    + azureConfig.tenantId().get() + "/oauth2/v2.0/token";
            // In production, use a proper HTTP client; simplified here for clarity
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
        // Providers publish a month's data with a delay of up to a few weeks and
        // return no rows until then, so no rows must not be mistaken for zero emissions
        if (response == null || response.value() == null || response.value().isEmpty()) {
            throw new CarbonDataUnavailableException("No data published yet");
        }
        // Azure returns carbon in metric tons of CO2e; convert to kg
        return response.value().stream()
                .mapToDouble(e -> e.totalCarbonEmission() != null ? e.totalCarbonEmission() * 1000.0 : 0.0)
                .sum();
    }
}
