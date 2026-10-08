CREATE INDEX idx_scores_user_last_played ON scores (user_id, (COALESCE(time_set, created_at)) DESC);
