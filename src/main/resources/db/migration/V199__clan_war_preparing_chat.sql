ALTER TABLE chat_messages
    DROP CONSTRAINT chk_chat_messages_event,
    ADD CONSTRAINT chk_chat_messages_event CHECK (event IN ('member_joined', 'member_left', 'member_kicked',
        'alliance_formed', 'alliance_ended', 'rival_declared', 'rivaled_by', 'war_declared', 'war_received',
        'war_preparing', 'war_started', 'war_hit', 'war_break', 'war_ended', 'missions_refreshed', 'war_ally_joined',
        'war_ally_left'));
