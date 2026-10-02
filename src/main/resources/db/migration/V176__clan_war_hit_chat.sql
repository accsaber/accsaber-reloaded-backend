ALTER TABLE chat_messages
    DROP CONSTRAINT chk_chat_messages_event,
    ADD CONSTRAINT chk_chat_messages_event CHECK (event IN ('member_joined', 'member_left', 'member_kicked',
        'alliance_formed', 'alliance_ended', 'rival_declared', 'rivaled_by', 'war_declared', 'war_received',
        'war_started', 'war_hit', 'war_break', 'war_ended'));

CREATE UNIQUE INDEX uq_clan_war_hits_one_break_per_cycle ON clan_war_hits (war_id, victim_user_id, victim_cycle)
    WHERE broke;
