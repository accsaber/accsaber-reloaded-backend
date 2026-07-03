CREATE TABLE campaign_votes (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    campaign_id UUID        NOT NULL REFERENCES campaigns(id),
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    vote        TEXT        NOT NULL CHECK (vote IN ('up', 'down')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_campaign_vote_per_user UNIQUE (campaign_id, user_id)
);

CREATE INDEX idx_campaign_votes_campaign ON campaign_votes(campaign_id);
CREATE INDEX idx_campaign_votes_user     ON campaign_votes(user_id);

CREATE TRIGGER trg_campaign_votes_updated_at
    BEFORE UPDATE ON campaign_votes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE campaigns
    ADD COLUMN total_upvotes   INTEGER          NOT NULL DEFAULT 0,
    ADD COLUMN total_downvotes INTEGER          NOT NULL DEFAULT 0,
    ADD COLUMN vote_score      DOUBLE PRECISION NOT NULL DEFAULT 0;

CREATE INDEX idx_campaigns_vote_score ON campaigns(vote_score DESC);
