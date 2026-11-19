ALTER TABLE map_difficulties DROP CONSTRAINT map_difficulties_status_check;
ALTER TABLE map_difficulties ADD CONSTRAINT map_difficulties_status_check
    CHECK (status IN ('queue', 'qualified', 'ranked', 'campaign', 'suggested'));

ALTER TABLE map_difficulties ADD COLUMN deactivation_reason TEXT;

ALTER TABLE chat_messages
    ADD COLUMN map_difficulty_id UUID REFERENCES map_difficulties(id),
    DROP CONSTRAINT chk_chat_messages_channel,
    ADD CONSTRAINT chk_chat_messages_channel CHECK (num_nonnulls(campaign_id, clan_id, map_difficulty_id) = 1);

CREATE INDEX idx_chat_messages_map_difficulty ON chat_messages (map_difficulty_id, created_at DESC)
    WHERE map_difficulty_id IS NOT NULL;

CREATE TABLE map_difficulty_subscriptions (
    map_difficulty_id UUID        NOT NULL REFERENCES map_difficulties(id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (map_difficulty_id, user_id)
);

CREATE INDEX idx_map_difficulty_subscriptions_user ON map_difficulty_subscriptions (user_id);

ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('trade_offer', 'trade_accepted', 'trade_declined',
                    'market_sold', 'market_bid', 'market_outbid', 'market_won',
                    'item_earned', 'server', 'clan_membership', 'clan_alliance', 'clan_war',
                    'campaign_invite', 'map_followed'));
