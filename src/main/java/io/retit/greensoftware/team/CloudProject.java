package io.retit.greensoftware.team;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "cloud_projects")
public class CloudProject extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    public Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public CloudProvider provider;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(name = "project_name")
    public String projectName;

    public static java.util.List<CloudProject> findByTeam(Team team) {
        return list("team", team);
    }

    public static java.util.List<CloudProject> findByProviderAndProjectId(CloudProvider provider, String projectId) {
        return list("provider = ?1 and projectId = ?2", provider, projectId);
    }
}
