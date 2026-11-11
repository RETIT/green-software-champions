-- =============================================================================
-- V1: Initial schema for Green Software Champions leaderboard
-- =============================================================================

-- Hibernate ORM 6 uses sequences for ID generation.
-- Create them here so H2 (in PostgreSQL mode) and PostgreSQL both work.
CREATE SEQUENCE IF NOT EXISTS teams_seq        START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS cloud_projects_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS carbon_entries_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS achievements_seq   START WITH 1 INCREMENT BY 50;

CREATE TABLE teams
(
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    color       VARCHAR(50)  NOT NULL,
    icon        VARCHAR(50)
);

CREATE TABLE cloud_projects
(
    id           BIGSERIAL    PRIMARY KEY,
    team_id      BIGINT       NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    provider     VARCHAR(50)  NOT NULL,
    project_id   VARCHAR(255) NOT NULL,
    project_name VARCHAR(255),
    CONSTRAINT uq_cloud_project UNIQUE (team_id, provider, project_id)
);

-- The previous period's figures are not stored, but derived when reading, so
-- they cannot go stale when that period's entry is revised later.
CREATE TABLE carbon_entries
(
    id             BIGSERIAL      PRIMARY KEY,
    team_id        BIGINT         NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    period         VARCHAR(7)     NOT NULL,
    total_co2_kg   DECIMAL(20, 4),
    sync_timestamp TIMESTAMP WITH TIME ZONE,
    aws_co2_kg     DECIMAL(20, 4),
    azure_co2_kg   DECIMAL(20, 4),
    gcp_co2_kg     DECIMAL(20, 4),
    CONSTRAINT uq_carbon_entry UNIQUE (team_id, period)
);

CREATE INDEX idx_carbon_entries_period ON carbon_entries (period);
CREATE INDEX idx_carbon_entries_team   ON carbon_entries (team_id);

-- Each achievement is granted at most once per team and period. Some store the
-- threshold reached (e.g., the length of a green streak), and the value they
-- were awarded for (e.g., the reduction in percent).
CREATE TABLE achievements
(
    id                    BIGSERIAL    PRIMARY KEY,
    team_id               BIGINT       NOT NULL REFERENCES teams (id) ON DELETE CASCADE,
    title                 VARCHAR(255) NOT NULL,
    description           TEXT,
    badge_icon            VARCHAR(100),
    period                VARCHAR(7)   NOT NULL,
    achievement_type      VARCHAR(100),
    achievement_threshold INTEGER,
    measured_value        DOUBLE PRECISION
);

CREATE INDEX idx_achievements_team ON achievements (team_id);

-- Every sync re-fetches several months, but each period's achievements are
-- evaluated only once, as soon as every team has data for it.
CREATE TABLE achievement_evaluations
(
    period       VARCHAR(7)               PRIMARY KEY,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
