CREATE TABLE unusual_effects (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    key         TEXT        NOT NULL UNIQUE,
    name        TEXT        NOT NULL,
    description TEXT,
    effect_spec JSONB       NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_unusual_effects_updated_at
    BEFORE UPDATE ON unusual_effects
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

INSERT INTO unusual_effects (key, name, description, effect_spec) VALUES
    ('fiery', 'Fiery', 'Animated flames licking the item borders.', '{
        "contractVersion": 1,
        "compositions": [
            {"type": "particle", "preset": "flames", "color": "#ff6a00", "ratePerSec": 10},
            {"type": "border_outline", "color": "#ff3b00", "widthPx": 2, "glow": {"color": "#ff6a00", "blurPx": 8}}
        ]
    }'::jsonb),
    ('angelic', 'Angelic', 'A radiant halo hovers above the item.', '{
        "contractVersion": 1,
        "compositions": [
            {"type": "particle", "preset": "halo", "color": "#fff4b8", "ratePerSec": 4},
            {"type": "shader_overlay", "shader": "soft_glow", "opacity": 0.5, "blendMode": "screen"}
        ]
    }'::jsonb);

CREATE TABLE crate_unusual_effects (
    crate_item_id UUID        NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    effect_id     UUID        NOT NULL REFERENCES unusual_effects(id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (crate_item_id, effect_id)
);

CREATE INDEX idx_crate_unusual_effects_crate  ON crate_unusual_effects(crate_item_id);
CREATE INDEX idx_crate_unusual_effects_effect ON crate_unusual_effects(effect_id);

ALTER TABLE user_item_links
    ADD COLUMN unusual_effect_id UUID REFERENCES unusual_effects(id);

CREATE INDEX idx_user_item_links_unusual_effect ON user_item_links(unusual_effect_id);
