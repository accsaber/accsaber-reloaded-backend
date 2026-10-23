ALTER TABLE clan_war_participants
    ADD COLUMN targeting TEXT NOT NULL DEFAULT 'follow',
    ADD COLUMN order_kind TEXT NOT NULL DEFAULT 'auto',
    ADD COLUMN ordered_target_user_id BIGINT REFERENCES users(id),
    ADD CONSTRAINT chk_clan_war_participants_targeting CHECK (targeting IN ('follow', 'auto', 'manual')),
    ADD CONSTRAINT chk_clan_war_participants_order CHECK (order_kind IN ('auto', 'target', 'halt')
        AND (order_kind = 'target') = (ordered_target_user_id IS NOT NULL));

UPDATE clan_war_participants SET targeting = 'manual' WHERE target_user_id IS NOT NULL;
