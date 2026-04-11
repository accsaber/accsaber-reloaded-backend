CREATE TABLE user_category_ranking_history (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            BIGINT      NOT NULL REFERENCES users(id),
    category_id        UUID        NOT NULL REFERENCES categories(id),
    ranking            INTEGER,
    country_ranking    INTEGER,
    recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cat_rank_history_user_cat_recorded
    ON user_category_ranking_history(user_id, category_id, recorded_at DESC);
