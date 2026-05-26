ALTER TABLE scores ADD COLUMN ss_score_id BIGINT;
CREATE INDEX idx_scores_ss_score_id ON scores(ss_score_id) WHERE ss_score_id IS NOT NULL;
