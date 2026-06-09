ALTER TABLE campaign_difficulties
    ADD COLUMN border_color     TEXT,
    ADD COLUMN border_shape     TEXT,
    ADD COLUMN checkpoint_color TEXT,
    ADD COLUMN size             TEXT,
    ADD COLUMN checkpoint_size  TEXT;

ALTER TABLE campaigns
    ADD COLUMN background_url TEXT;
