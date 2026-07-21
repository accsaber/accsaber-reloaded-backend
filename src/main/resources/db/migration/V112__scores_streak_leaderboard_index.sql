CREATE INDEX IF NOT EXISTS idx_scores_streak_desc
    ON scores (streak_115 DESC, ap DESC, score DESC)
    WHERE streak_115 IS NOT NULL;
