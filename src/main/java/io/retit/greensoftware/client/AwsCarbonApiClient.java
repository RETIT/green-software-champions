package io.retit.greensoftware.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * MicroProfile REST Client for the AWS Carbon Footprint API.
 *
 * <p>Base URL is configured via {@code quarkus.rest-client.aws-carbon-api.url} in
 * application.yaml (defaults to the value of {@code cloud.aws.endpoint}).
 *
 * <p><b>Important:</b> AWS requires SigV4 request signing. In production, register a
 * {@code ClientRequestFilter} that adds the {@code Authorization}, {@code x-amz-date},
 * and {@code x-amz-security-token} headers using the configured credentials.
 *
 * @see <a href="https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_CarbonFootprint.html">
 *      AWS Carbon Footprint API</a>
 */
@RegisterRestClient(configKey = "aws-carbon-api")
@Path("/")
public interface AwsCarbonApiClient {

    /**
     * Retrieve carbon footprint summary for one billing account.
     *
     * @param billingAccountId AWS account ID (12-digit number).
     * @param startTime        ISO-8601 start timestamp (inclusive).
     * @param endTime          ISO-8601 end timestamp (inclusive).
     * @param unit             Unit for the response values (e.g., "METRIC_TON").
     * @return Carbon footprint response from the AWS API.
     */
    @GET
    @Path("GetCarbonFootprintSummary")
    @Produces(MediaType.APPLICATION_JSON)
    CarbonFootprintResponse getCarbonFootprintSummary(
            @QueryParam("billingAccountId") String billingAccountId,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("unit") String unit);

    /** Top-level response wrapper. */
    record CarbonFootprintResponse(List<CarbonFootprintSummary> carbonFootprintSummary) {}

    /** One entry in the carbon footprint summary (per service or aggregate). */
    record CarbonFootprintSummary(String serviceName, Double value, String unit) {}
}
