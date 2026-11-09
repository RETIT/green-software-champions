package io.retit.greensoftware.client;

import io.retit.greensoftware.config.CloudProvidersConfig;
import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.util.PeriodRange;
import io.retit.greensoftware.util.Periods;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

/**
 * GCP Carbon Footprint data provider.
 *
 * <p>Connects to the Google Cloud Carbon Footprint API to retrieve CO2-equivalent
 * emissions data per GCP project.
 *
 * <p><b>Authentication:</b> GCP uses OAuth2 service account credentials. Configure
 * {@code cloud.gcp.credentials-file} to point to a service account JSON key file, or
 * use Application Default Credentials (ADC) when running on GCP infrastructure.
 *
 * @see <a href="https://cloud.google.com/carbon-footprint/docs/reference/rest">
 *      GCP Carbon Footprint API</a>
 */
@ApplicationScoped
public class GcpCarbonProvider implements CarbonProvider {

    private static final Logger LOG = Logger.getLogger(GcpCarbonProvider.class);

    @Inject
    CloudProvidersConfig config;

    @Inject
    @RestClient
    GcpCarbonApiClient apiClient;

    @Override
    public boolean isEnabled() {
        return config.gcp().enabled();
    }

    @Override
    public Map<String, List<CarbonData>> fetchCarbonData(List<CloudProject> projects, PeriodRange range) {
        if (!isEnabled()) {
            LOG.debug("GCP carbon provider is disabled; returning empty data.");
            return Map.of();
        }

        String accessToken = acquireAccessToken();
        if (accessToken == null) {
            throw new CarbonDataUnavailableException("Could not acquire GCP access token.");
        }

        // One request per project and month
        return CarbonProvider.fetchEachPeriod(range, period -> fetchPeriod(projects, period, accessToken));
    }

    /**
     * Fetches the data of all {@code projects} in {@code period}.
     *
     * @throws CarbonDataUnavailableException if the data of any project could not be fetched
     */
    private List<CarbonData> fetchPeriod(List<CloudProject> projects, String period, String accessToken) {
        List<CarbonData> results = new ArrayList<>();
        // GCP Carbon Footprint API uses YYYY-MM-DD ranges
        YearMonth ym = Periods.toYearMonth(period);
        String startDate = ym.atDay(1).toString();
        String endDate = ym.atEndOfMonth().toString();

        for (CloudProject project : projects) {
            try {
                LOG.infof("Fetching GCP carbon data for project %s, period %s", project.projectId, period);
                GcpCarbonApiClient.CarbonFootprintResponse response =
                        apiClient.getCarbonFootprint(
                                "Bearer " + accessToken,
                                "projects/" + project.projectId,
                                startDate,
                                endDate,
                                "MONTH");
                double co2Kg = extractTotalCo2Kg(response);
                results.add(new CarbonData(project.projectId, co2Kg));
            } catch (Exception e) {
                throw new CarbonDataUnavailableException(String.format(
                        "Failed to fetch GCP carbon data for project %s, period %s: %s",
                        project.projectId, period, e.getMessage()), e);
            }
        }
        return results;
    }

    /**
     * Acquires a GCP access token using the configured service account credentials
     * or Application Default Credentials.
     */
    private String acquireAccessToken() {
        CloudProvidersConfig.GcpConfig gcpConfig = config.gcp();
        try {
            // When running on GCP, use the metadata server for ADC
            java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(
                            "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"))
                    .header("Metadata-Flavor", "Google")
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> response =
                    httpClient.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();
                int tokenStart = body.indexOf("\"access_token\":\"") + 16;
                int tokenEnd = body.indexOf("\"", tokenStart);
                if (tokenStart > 15 && tokenEnd > tokenStart) {
                    return body.substring(tokenStart, tokenEnd);
                }
            }
        } catch (Exception e) {
            LOG.debugf("ADC metadata server not available: %s", e.getMessage());
        }

        try {
            // If the config contains raw JSON credentials, prefer that (no file required)
            if (!gcpConfig.credentialsJson().isEmpty()) {
                String json = gcpConfig.credentialsJson().get();
                LOG.info("Using GCP service account credentials from configuration (JSON).");
                try (ByteArrayInputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {
                    GoogleCredentials creds = ServiceAccountCredentials.fromStream(is)
                            .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
                    // Refresh to obtain an access token
                    AccessToken token = creds.refreshAccessToken();
                    return token != null ? token.getTokenValue() : null;
                }
            }

            // If a credentials file path is configured, load it
            if (!gcpConfig.credentialsFile().isEmpty()) {
                String path = gcpConfig.credentialsFile().get();
                LOG.infof("Using GCP service account key file: %s", path);
                try (FileInputStream fis = new FileInputStream(path)) {
                    GoogleCredentials creds = ServiceAccountCredentials.fromStream(fis)
                            .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));
                    AccessToken token = creds.refreshAccessToken();
                    return token != null ? token.getTokenValue() : null;
                }
            }
        } catch (Exception e) {
            LOG.warnf("Failed to load GCP service account credentials: %s", e.getMessage());
        }
        return null;
    }

    private double extractTotalCo2Kg(GcpCarbonApiClient.CarbonFootprintResponse response) {
        // Providers publish a month's data with a delay of up to a few weeks and
        // return no rows until then, so no rows must not be mistaken for zero emissions
        if (response == null || response.carbonFootprintSummary() == null || response.carbonFootprintSummary().isEmpty()) {
            throw new CarbonDataUnavailableException("No data published yet");
        }
        // GCP API returns kg of CO2e directly
        return response.carbonFootprintSummary().stream()
                .mapToDouble(s -> s.kgCO2e() != null ? s.kgCO2e() : 0.0)
                .sum();
    }
}
