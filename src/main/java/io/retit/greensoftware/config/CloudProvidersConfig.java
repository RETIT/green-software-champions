package io.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Configuration for the three main cloud provider carbon footprint REST endpoints.
 * All values can be overridden via environment variables or application.yaml.
 */
@ConfigMapping(prefix = "cloud")
public interface CloudProvidersConfig {

    AwsConfig aws();

    AzureConfig azure();

    GcpConfig gcp();

    interface AwsConfig {
        /** Enable/disable AWS carbon data collection. */
        @WithDefault("false")
        boolean enabled();

        /**
         * AWS Carbon Footprint API endpoint.
         * Default: https://sustainability.us-east-1.amazonaws.com
         */
        @WithDefault("https://sustainability.us-east-1.amazonaws.com")
        String endpoint();

        /** AWS access key ID for authenticating to the Carbon Footprint API. */
        @WithName("access-key")
        Optional<String> accessKey();

        /** AWS secret access key. */
        @WithName("secret-key")
        Optional<String> secretKey();

        /** AWS region where the sustainability service is queried. */
        @WithDefault("us-east-1")
        String region();
    }

    interface AzureConfig {
        /** Enable/disable Azure carbon data collection. */
        @WithDefault("false")
        boolean enabled();

        /**
         * Azure Resource Manager / Sustainability API endpoint.
         * Default: https://management.azure.com
         */
        @WithDefault("https://management.azure.com")
        String endpoint();

        /** Azure AD tenant ID for OAuth2 authentication. */
        @WithName("tenant-id")
        Optional<String> tenantId();

        /** Azure AD client/application ID. */
        @WithName("client-id")
        Optional<String> clientId();

        /** Azure AD client secret. */
        @WithName("client-secret")
        Optional<String> clientSecret();

        /** Azure subscription ID to query carbon data for. */
        @WithName("subscription-id")
        Optional<String> subscriptionId();
    }

    interface GcpConfig {
        /** Enable/disable GCP carbon data collection. */
        @WithDefault("false")
        boolean enabled();

        /**
         * GCP Carbon Footprint API endpoint.
         * Default: https://cloudbilling.googleapis.com
         */
        @WithDefault("https://cloudbilling.googleapis.com")
        String endpoint();

        /** Path to the GCP service account credentials JSON file. */
        @WithName("credentials-file")
        Optional<String> credentialsFile();

        /** GCP billing account ID (e.g., "012345-6789AB-CDEF01"). */
        @WithName("billing-account")
        Optional<String> billingAccount();
    }
}
