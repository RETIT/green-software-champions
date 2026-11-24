package io.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Optional;

/**
 * The teams and their cloud projects, from the file named by
 * {@code quarkus.config.locations}, e.g. sample-teams.yaml.
 */
@ConfigMapping(prefix = "teams")
public interface TeamsConfig {

    /** At least one, so the application refuses to start without a teams file. */
    @Size(min = 1)
    List<TeamDefinition> definitions();

    interface TeamDefinition {
        String name();

        Optional<String> description();

        String color();

        Optional<String> icon();

        List<ProjectDefinition> projects();
    }

    interface ProjectDefinition {
        /** Cloud provider: AWS, AZURE, or GCP. */
        String provider();

        /** AWS account ID, Azure subscription or resource group path, or GCP project ID. */
        @WithName("project-id")
        String projectId();

        @WithName("project-name")
        Optional<String> projectName();
    }
}
