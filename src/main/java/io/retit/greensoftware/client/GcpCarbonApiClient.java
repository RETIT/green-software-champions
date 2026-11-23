package io.retit.greensoftware.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * REST client for the Google Cloud Carbon Footprint API.
 *
 * @see <a href="https://cloud.google.com/carbon-footprint/docs/reference/rest/v1/projects.locations.carbonFootprints">
 *      GCP Carbon Footprint REST API</a>
 */
@RegisterRestClient(configKey = "gcp-carbon-api")
@Path("/v1")
public interface GcpCarbonApiClient {

    /**
     * The emissions of {@code name}, e.g. "projects/my-project", between two dates
     * (YYYY-MM-DD), aggregated by e.g. "MONTH".
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

    record CarbonFootprintResponse(List<CarbonFootprintSummary> carbonFootprintSummary) {}

    record CarbonFootprintSummary(String timePeriod, String serviceName, Double kgCO2e) {}
}
