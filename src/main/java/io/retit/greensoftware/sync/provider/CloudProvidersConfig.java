package io.retit.greensoftware.sync.provider;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "cloud")
public interface CloudProvidersConfig {

    AwsConfig aws();

    AzureConfig azure();

    GcpConfig gcp();

    interface AwsConfig {
        @WithDefault("false")
        boolean enabled();

        /** Without it and {@link #secretKey()}, the AWS SDK's default credentials chain is used. */
        @WithName("access-key")
        Optional<String> accessKey();

        @WithName("secret-key")
        Optional<String> secretKey();

        @WithDefault("us-east-1")
        String region();
    }

    interface AzureConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("https://management.azure.com")
        String endpoint();

        @WithName("tenant-id")
        Optional<String> tenantId();

        @WithName("client-id")
        Optional<String> clientId();

        @WithName("client-secret")
        Optional<String> clientSecret();

        @WithName("subscription-id")
        Optional<String> subscriptionId();
    }

    interface GcpConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("https://cloudbilling.googleapis.com")
        String endpoint();

        /** Path of a service account key file (JSON). */
        @WithName("credentials-file")
        Optional<String> credentialsFile();

        /**
         * The service account key (JSON) itself, e.g. from CLOUD_GCP_CREDENTIALS_JSON, so
         * that no file needs to be mounted. Takes precedence over {@link #credentialsFile()}.
         */
        @WithName("credentials-json")
        Optional<String> credentialsJson();

        /** E.g. "012345-6789AB-CDEF01". */
        @WithName("billing-account")
        Optional<String> billingAccount();
    }
}
