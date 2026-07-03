CREATE TABLE campaign_chat_messages (
    id          UUID          PRIMARY KEY DEFAULT uuidv7(),
    campaign_id UUID          NOT NULL REFERENCES campaigns(id),
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    content     TEXT          NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_campaign_chat_messages_campaign ON campaign_chat_messages(campaign_id, created_at DESC);
