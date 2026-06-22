CREATE INDEX IF NOT EXISTS idx_scores_user_ap
    ON scores (user_id, ap DESC)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_scores_diff_score_rank
    ON scores (map_difficulty_id, score DESC, rank)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_users_lower_country
    ON users (LOWER(country))
    WHERE active = true;
