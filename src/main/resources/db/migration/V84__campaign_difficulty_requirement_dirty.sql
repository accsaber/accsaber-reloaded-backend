ALTER TABLE campaign_difficulties
    ADD COLUMN requirement_dirty BOOLEAN NOT NULL DEFAULT FALSE;
