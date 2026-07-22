CREATE TABLE notifications (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       TEXT        NOT NULL
                           CHECK (type IN ('trade_offer', 'trade_accepted', 'trade_declined',
                                           'market_sold', 'market_bid', 'item_earned', 'server')),
    title      TEXT        NOT NULL,
    link_to    TEXT,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_feed
    ON notifications (user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications (user_id) WHERE read_at IS NULL;

CREATE INDEX idx_notifications_retention
    ON notifications (created_at);
