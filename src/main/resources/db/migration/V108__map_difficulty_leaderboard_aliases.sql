CREATE TABLE map_difficulty_leaderboard_aliases (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    map_difficulty_id  UUID        NOT NULL REFERENCES map_difficulties(id) ON DELETE CASCADE,
    ss_leaderboard_id  TEXT,
    bl_leaderboard_id  TEXT,
    created_by         UUID        REFERENCES staff_users(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (ss_leaderboard_id IS NOT NULL OR bl_leaderboard_id IS NOT NULL)
);

CREATE INDEX idx_map_difficulty_leaderboard_aliases_difficulty
    ON map_difficulty_leaderboard_aliases(map_difficulty_id);
CREATE UNIQUE INDEX idx_map_difficulty_leaderboard_aliases_bl
    ON map_difficulty_leaderboard_aliases(bl_leaderboard_id) WHERE bl_leaderboard_id IS NOT NULL;
CREATE UNIQUE INDEX idx_map_difficulty_leaderboard_aliases_ss
    ON map_difficulty_leaderboard_aliases(ss_leaderboard_id) WHERE ss_leaderboard_id IS NOT NULL;
