ALTER TABLE chat_messages
    ALTER COLUMN campaign_id DROP NOT NULL,
    ALTER COLUMN user_id DROP NOT NULL,
    ALTER COLUMN content DROP NOT NULL,
    ADD COLUMN clan_id         UUID   REFERENCES clans(id),
    ADD COLUMN event           TEXT,
    ADD COLUMN subject_user_id BIGINT REFERENCES users(id),
    ADD COLUMN subject_clan_id UUID   REFERENCES clans(id),
    ADD CONSTRAINT chk_chat_messages_channel CHECK (num_nonnulls(campaign_id, clan_id) = 1),
    ADD CONSTRAINT chk_chat_messages_event CHECK (event IN ('member_joined', 'member_left', 'member_kicked',
        'alliance_formed', 'alliance_ended', 'rival_declared', 'rivaled_by')),
    ADD CONSTRAINT chk_chat_messages_kind CHECK (
        CASE WHEN event IS NULL THEN content IS NOT NULL AND user_id IS NOT NULL
             ELSE content IS NULL AND clan_id IS NOT NULL END);

DROP INDEX idx_chat_messages_campaign;
CREATE INDEX idx_chat_messages_campaign ON chat_messages (campaign_id, created_at DESC) WHERE campaign_id IS NOT NULL;
CREATE INDEX idx_chat_messages_clan ON chat_messages (clan_id, created_at DESC) WHERE clan_id IS NOT NULL;
