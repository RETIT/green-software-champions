package io.retit.greensoftware.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * MicroProfile REST Client for the Google Cloud Carbon Footprint API.
 *
 * <p>Base URL is configured via {@code quarkus.rest-client.gcp-carbon-api.url}
 * in application.yaml.
 *
 * @see <a href="https://cloud.google.com/carbon-footprint/docs/reference/rest/v1/projects.locations.carbonFootprints">
 *      GCP Carbon Footprint REST API</a>
 */
@RegisterRestClient(configKey = "gcp-carbon-api")
@Path("/v1")
public interface GcpCarbonApiClient {

    /**
     * Retrieve carbon footprint data for a GCP project.
     *
     * @param authorization Bearer access token.
     * @param name          Resource name (e.g., "projects/my-project").
     * @param startTime     Start date in YYYY-MM-DD format.
     * @param endTime       End date in YYYY-MM-DD format.
     * @param aggregation   Aggregation type (e.g., "MONTH").
     * @return Carbon footprint response.
     */
    @GET
    @Path("/{name}/carbonFootprints")
    @Produces(MediaType.APPLICATION_JSON)
    CarbonFootprintResponse getCarbonFootprint(
            @HeaderParam("Authorization") String authorization,
            @PathParam("name") String name,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("aggregation") String aggregation);

    /** Top-level response wrapper. */
    record CarbonFootprintResponse(List<CarbonFootprintSummary> carbonFootprintSummary) {}

    /** Summary entry for a period/service combination. */
    record CarbonFootprintSummary(String timePeriod, String serviceName, Double kgCO2e) {}
}
