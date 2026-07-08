ALTER TABLE campaign_difficulties
    ALTER COLUMN size TYPE INTEGER
        USING (CASE WHEN size ~ '^\s*-?\d+\s*$' THEN trim(size)::INTEGER ELSE NULL END),
    ALTER COLUMN checkpoint_size TYPE INTEGER
        USING (CASE WHEN checkpoint_size ~ '^\s*-?\d+\s*$' THEN trim(checkpoint_size)::INTEGER ELSE NULL END);
