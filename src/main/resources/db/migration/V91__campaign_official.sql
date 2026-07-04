ALTER TABLE campaigns
    ADD COLUMN official BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_campaigns_official ON campaigns(official) WHERE official = true;
