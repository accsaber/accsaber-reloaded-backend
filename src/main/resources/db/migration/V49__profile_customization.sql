ALTER TABLE users ADD COLUMN bio TEXT NOT NULL DEFAULT '';

CREATE TABLE user_pinned_scores (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score_id      UUID         NOT NULL REFERENCES scores(id) ON DELETE CASCADE,
    display_order INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, score_id),
    UNIQUE (user_id, display_order)
);

CREATE INDEX idx_user_pinned_scores_user ON user_pinned_scores(user_id);
CREATE INDEX idx_user_pinned_scores_score ON user_pinned_scores(score_id);

CREATE TRIGGER trg_user_pinned_scores_updated_at
    BEFORE UPDATE ON user_pinned_scores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
