ALTER TABLE campaigns
    ADD COLUMN published_at TIMESTAMPTZ;

UPDATE campaigns
SET published_at = created_at
WHERE status IN ('published', 'curated');

CREATE INDEX idx_campaigns_published_at ON campaigns (published_at DESC);
