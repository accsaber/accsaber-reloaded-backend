ALTER TABLE campaign_difficulties
    ADD COLUMN prerequisite_mode TEXT NOT NULL DEFAULT 'OR'
    CHECK (prerequisite_mode IN ('OR', 'AND'));
