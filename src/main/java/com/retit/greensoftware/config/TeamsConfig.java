package com.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;

/**
 * Configuration mapping that defines which teams exist and which cloud projects
 * belong to each team. Configured in application.yaml under the "teams" key.
 *
 * Example:
 * <pre>
 * teams:
 *   definitions:
 *     - name: "Team Alpha"
 *       color: "#4CAF50"
 *       icon: "🌱"
 *       projects:
 *         - provider: AWS
 *           project-id: "123456789012"
 *           project-name: "alpha-prod"
 * </pre>
 */
@ConfigMapping(prefix = "teams")
public interface TeamsConfig {

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

        /** The cloud provider's project/account/subscription identifier. */
        @WithName("project-id")
        String projectId();

        /** Human-readable name for the project. */
        @WithName("project-name")
        Optional<String> projectName();
    }
}
