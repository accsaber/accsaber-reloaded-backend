CREATE TABLE map_difficulty_complexity_estimates (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    map_difficulty_id UUID        NOT NULL REFERENCES map_difficulties(id),
    source            TEXT        NOT NULL CHECK (source IN ('old_script', 'new_script')),
    complexity        DOUBLE PRECISION NOT NULL,
    version           TEXT        NOT NULL,
    inputs            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_complexity_estimate_per_source UNIQUE (map_difficulty_id, source)
);

CREATE INDEX idx_map_difficulty_complexity_estimates_source
    ON map_difficulty_complexity_estimates (source);

CREATE TRIGGER trg_map_difficulty_complexity_estimates_updated_at
    BEFORE UPDATE ON map_difficulty_complexity_estimates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
