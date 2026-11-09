package io.retit.greensoftware.client;

import io.retit.greensoftware.config.CloudProvidersConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sustainability.SustainabilityClient;
import software.amazon.awssdk.services.sustainability.SustainabilityClientBuilder;
import software.amazon.awssdk.services.sustainability.model.Dimension;
import software.amazon.awssdk.services.sustainability.model.Emissions;
import software.amazon.awssdk.services.sustainability.model.EmissionsType;
import software.amazon.awssdk.services.sustainability.model.EstimatedCarbonEmissions;
import software.amazon.awssdk.services.sustainability.model.GetEstimatedCarbonEmissionsRequest;
import software.amazon.awssdk.services.sustainability.model.TimeGranularity;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the AWS Sustainability API, based on the AWS SDK for Java.
 *
 * <p>The SDK derives the endpoint from {@code cloud.aws.region} and signs the requests.
 * It authenticates with {@code cloud.aws.access-key} and {@code cloud.aws.secret-key}
 * if both are set, otherwise with its default credentials chain (e.g., IAM roles). The
 * identity needs the IAM action {@code sustainability:GetEstimatedCarbonEmissions}.
 *
 * <p>The SDK client is only built on first use, so nothing is set up while the AWS
 * provider is disabled.
 *
 * @see <a href="https://docs.aws.amazon.com/sustainability/latest/APIReference/API_GetEstimatedCarbonEmissions.html">
 *      GetEstimatedCarbonEmissions</a>
 */
@ApplicationScoped
public class AwsCarbonApiClient {

    private static final Logger LOG = Logger.getLogger(AwsCarbonApiClient.class);

    /**
     * AWS reports each month both location-based and market-based, so exactly one of
     * them has to be selected, as adding them up would count the emissions twice.
     */
    private static final EmissionsType EMISSIONS_TYPE = EmissionsType.TOTAL_LBM_CARBON_EMISSIONS;

    @Inject
    CloudProvidersConfig config;

    private SustainabilityClient client;

    @PostConstruct
    void init() {
        CloudProvidersConfig.AwsConfig aws = config.aws();
        SustainabilityClientBuilder builder = SustainabilityClient.builder()
                .region(Region.of(aws.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (aws.accessKey().isPresent() && aws.secretKey().isPresent()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(aws.accessKey().get(), aws.secretKey().get())));
        }
        client = builder.build();
    }

    @PreDestroy
    void close() {
        client.close();
    }

    /**
     * Retrieves the estimated emissions of the given accounts in each month from
     * {@code first} to {@code last} with a single (paginated) request.
     *
     * @param accountIds AWS account IDs (12-digit numbers), must not be empty.
     * @param first      The first month to retrieve (inclusive).
     * @param last       The last month to retrieve (inclusive).
     * @return Emissions in metric tons of CO2-equivalent by month and account ID. Months
     *         and accounts AWS returned no data for are missing.
     */
    public Map<YearMonth, Map<String, Double>> getEstimatedEmissions(List<String> accountIds,
                                                                     YearMonth first, YearMonth last) {
        GetEstimatedCarbonEmissionsRequest request = GetEstimatedCarbonEmissionsRequest.builder()
                // The end of the time period is exclusive
                .timePeriod(period -> period.start(startOf(first)).end(startOf(last.plusMonths(1))))
                .filterBy(filter -> filter.dimensions(Map.of(Dimension.USAGE_ACCOUNT_ID, accountIds)))
                .groupBy(Dimension.USAGE_ACCOUNT_ID)
                .granularity(TimeGranularity.MONTHLY)
                .build();

        Map<YearMonth, Map<String, Double>> metricTonsByMonth = new HashMap<>();
        for (EstimatedCarbonEmissions row : client.getEstimatedCarbonEmissionsPaginator(request).results()) {
            Emissions emissions = row.emissionsValues().get(EMISSIONS_TYPE);
            if (emissions != null) {
                metricTonsByMonth.computeIfAbsent(monthOf(row), month -> new HashMap<>())
                        .merge(row.dimensionsValues().get(Dimension.USAGE_ACCOUNT_ID), emissions.value(), Double::sum);
            }
        }
        return metricTonsByMonth;
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The month {@code row} reports on, as rows of the monthly granularity cover one month each. */
    private static YearMonth monthOf(EstimatedCarbonEmissions row) {
        return YearMonth.from(row.timePeriod().start().atZone(ZoneOffset.UTC));
    }
}
