package io.retit.greensoftware.dto;

import java.util.List;

public class TeamDto {

    public Long id;
    public String name;
    public String description;
    public String color;
    public String icon;
    public List<CloudProjectDto> projects;

    public static class CloudProjectDto {
        public Long id;
        public String provider;
        public String projectId;
        public String projectName;
    }
}
