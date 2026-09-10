DELETE FROM map_difficulty_complexity_estimates WHERE source = 'old_script';

ALTER TABLE map_difficulty_complexity_estimates DROP CONSTRAINT unique_complexity_estimate_per_source;
DROP INDEX idx_map_difficulty_complexity_estimates_source;
ALTER TABLE map_difficulty_complexity_estimates DROP COLUMN source;
ALTER TABLE map_difficulty_complexity_estimates
    ADD CONSTRAINT unique_complexity_estimate_per_difficulty UNIQUE (map_difficulty_id);
