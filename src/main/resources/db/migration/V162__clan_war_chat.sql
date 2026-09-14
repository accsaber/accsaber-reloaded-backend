ALTER TABLE chat_messages
    ADD COLUMN war_id UUID REFERENCES clan_wars(id),
    DROP CONSTRAINT chk_chat_messages_event,
    ADD CONSTRAINT chk_chat_messages_event CHECK (event IN ('member_joined', 'member_left', 'member_kicked',
        'alliance_formed', 'alliance_ended', 'rival_declared', 'rivaled_by', 'war_declared', 'war_received',
        'war_started', 'war_ended')),
    ADD CONSTRAINT chk_chat_messages_war CHECK (war_id IS NULL OR event LIKE 'war\_%');
