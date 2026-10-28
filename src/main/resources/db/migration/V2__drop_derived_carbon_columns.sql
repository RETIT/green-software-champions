-- =============================================================================
-- V2: Drop the previous-period figures from carbon_entries
-- =============================================================================

-- They were copied from the previous period's entry when syncing and went stale
-- whenever that entry was revised later. They are now derived when reading.
ALTER TABLE carbon_entries DROP COLUMN previous_co2_kg;
ALTER TABLE carbon_entries DROP COLUMN reduction_percentage;
