package io.retit.greensoftware.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * REST client for the Azure Carbon Optimization API.
 *
 * @see <a href="https://learn.microsoft.com/en-us/rest/api/carbon/">
 *      Microsoft Azure Carbon REST API</a>
 */
@RegisterRestClient(configKey = "azure-carbon-api")
@Path("/providers/Microsoft.Carbon")
public interface AzureCarbonApiClient {

    /**
     * The emissions of {@code scope}, a subscription or resource group path, in one
     * month (1–12).
     */
    @GET
    @Path("/carbonEmissionReports")
    @Produces(MediaType.APPLICATION_JSON)
    CarbonEmissionResponse getCarbonEmissions(
            @HeaderParam("Authorization") String authorization,
            @QueryParam("scope") String scope,
            @QueryParam("api-version") String apiVersion,
            @QueryParam("reportYear") int year,
            @QueryParam("reportMonth") int month);

    record CarbonEmissionResponse(List<CarbonEmissionEntry> value) {}

    record CarbonEmissionEntry(
            String resourceId,
            String resourceType,
            Double totalCarbonEmission,
            String unit) {}
}
