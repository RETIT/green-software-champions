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
         * AWS access key ID for authenticating to the Sustainability API. Without it and
         * {@link #secretKey()}, the AWS SDK's default credentials chain is used.
         */
        @WithName("access-key")
        Optional<String> accessKey();

        /** AWS secret access key. */
        @WithName("secret-key")
        Optional<String> secretKey();

        /** AWS region where the sustainability service is queried, also determines the endpoint. */
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

        /**
         * Optional: service account key JSON contents provided directly as a string.
         * This can be supplied via environment variable (e.g. CLOUD_GCP_CREDENTIALS_JSON)
         * so that no file needs to be mounted. When present, the application will
         * use these JSON contents to create credentials in-memory.
         */
        @WithName("credentials-json")
        Optional<String> credentialsJson();
        /** GCP billing account ID (e.g., "012345-6789AB-CDEF01"). */
        @WithName("billing-account")
        Optional<String> billingAccount();
    }
}
