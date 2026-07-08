ALTER TABLE item_modifiers
    ADD COLUMN global_drop_chance NUMERIC CHECK (global_drop_chance > 0 AND global_drop_chance <= 1),
    ADD COLUMN season_start       TEXT,
    ADD COLUMN season_end         TEXT;

UPDATE item_modifiers
SET global_drop_chance = 0.05, season_start = '10-25', season_end = '11-01'
WHERE key = 'haunted';

UPDATE item_modifiers
SET global_drop_chance = 0.05, season_start = '12-20', season_end = '12-31'
WHERE key = 'jolly';

CREATE TABLE crate_modifiers (
    crate_item_id UUID        NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    modifier_id   UUID        NOT NULL REFERENCES item_modifiers(id) ON DELETE CASCADE,
    drop_chance   NUMERIC     NOT NULL CHECK (drop_chance > 0 AND drop_chance <= 1),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (crate_item_id, modifier_id)
);

CREATE INDEX idx_crate_modifiers_crate    ON crate_modifiers(crate_item_id);
CREATE INDEX idx_crate_modifiers_modifier ON crate_modifiers(modifier_id);
