package io.retit.greensoftware.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Optional;

/**
 * Configuration mapping that defines which teams exist and which cloud projects
 * belong to each team. Configured under the "teams" key of the file named by
 * quarkus.config.locations in application.yaml: sample-teams.yaml in dev mode
 * and tests, the file in TEAMS_FILE in production.
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

    /**
     * At least one team, so the application refuses to start if no teams file
     * was loaded, e.g. because TEAMS_FILE is not set in production.
     */
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

        /** The cloud provider's project/account/subscription identifier. */
        @WithName("project-id")
        String projectId();

        /** Human-readable name for the project. */
        @WithName("project-name")
        Optional<String> projectName();
    }
}
