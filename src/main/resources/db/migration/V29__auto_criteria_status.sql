ALTER TABLE map_difficulties
    ADD COLUMN auto_criteria_status TEXT NOT NULL DEFAULT 'pending'
        CHECK (auto_criteria_status IN ('pending', 'passed', 'failed', 'unavailable'));
