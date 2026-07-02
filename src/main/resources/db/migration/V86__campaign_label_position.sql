ALTER TABLE campaign_difficulties
    ADD COLUMN checkpoint_label_position TEXT
        CHECK (checkpoint_label_position IN ('LEFT', 'RIGHT', 'UP', 'DOWN', 'NONE'));
