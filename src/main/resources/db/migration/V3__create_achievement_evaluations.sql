-- =============================================================================
-- V3: Record which periods' achievements have been evaluated
-- =============================================================================

-- Every sync re-fetches several months, but each period's achievements are
-- evaluated only once, as soon as every team has data for it.
CREATE TABLE achievement_evaluations
(
    period       VARCHAR(7)               PRIMARY KEY,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
