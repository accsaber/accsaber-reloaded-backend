ALTER TABLE user_campaign_scores
    ADD COLUMN rewards_paid BOOLEAN NOT NULL DEFAULT false;

UPDATE user_campaign_scores ucs
SET rewards_paid = true
FROM campaigns c
WHERE ucs.campaign_id = c.id AND c.status = 'curated';

ALTER TABLE user_campaigns
    ADD COLUMN completion_rewards_paid BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_user_campaign_scores_unpaid
    ON user_campaign_scores(campaign_id)
    WHERE rewards_paid = false AND active = true;
