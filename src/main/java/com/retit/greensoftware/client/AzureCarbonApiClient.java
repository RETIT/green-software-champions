package com.retit.greensoftware.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * MicroProfile REST Client for the Azure Carbon Optimization / Resource Manager API.
 *
 * <p>Base URL is configured via {@code quarkus.rest-client.azure-carbon-api.url}
 * in application.yaml.
 *
 * @see <a href="https://learn.microsoft.com/en-us/rest/api/carbon/">
 *      Microsoft Azure Carbon REST API</a>
 */
@RegisterRestClient(configKey = "azure-carbon-api")
@Path("/providers/Microsoft.Carbon")
public interface AzureCarbonApiClient {

    /**
     * Retrieve carbon emission reports for a subscription or resource group.
     *
     * @param authorization Bearer token (Azure AD access token).
     * @param scope         Azure resource scope (e.g., subscription or resource group path).
     * @param apiVersion    API version string.
     * @param year          Reporting year.
     * @param month         Reporting month (1–12).
     * @return Carbon emission response.
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

    /** Top-level response wrapper from the Azure carbon API. */
    record CarbonEmissionResponse(List<CarbonEmissionEntry> value) {}

    /** One emission entry per resource or aggregate. */
    record CarbonEmissionEntry(
            String resourceId,
            String resourceType,
            Double totalCarbonEmission,
            String unit) {}
}
