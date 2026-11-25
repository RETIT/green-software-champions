package io.retit.greensoftware.dto;

import io.retit.greensoftware.entity.CloudProject;
import io.retit.greensoftware.service.TeamDetails;

import java.util.List;

public class TeamDto {

    public Long id;
    public String name;
    public String description;
    public String color;
    public String icon;
    public List<CloudProjectDto> projects;

    public static TeamDto from(TeamDetails details) {
        TeamDto dto = new TeamDto();
        dto.id = details.team().id;
        dto.name = details.team().name;
        dto.description = details.team().description;
        dto.color = details.team().color;
        dto.icon = details.team().icon;
        dto.projects = details.projects().stream().map(CloudProjectDto::from).toList();
        return dto;
    }

    public static class CloudProjectDto {
        public Long id;
        public String provider;
        public String projectId;
        public String projectName;

        static CloudProjectDto from(CloudProject project) {
            CloudProjectDto dto = new CloudProjectDto();
            dto.id = project.id;
            dto.provider = project.provider.name();
            dto.projectId = project.projectId;
            dto.projectName = project.projectName;
            return dto;
        }
    }
}
