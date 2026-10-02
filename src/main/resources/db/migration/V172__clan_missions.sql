ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_pool;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_pool
    CHECK (pool IN ('daily', 'weekly', 'event', 'community', 'clan'));

ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_templates_fixed_target_types;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_templates_fixed_target_types CHECK (
    type NOT IN ('STREAK_SUM_N', 'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
                 'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N')
    OR pool IN ('event', 'community', 'clan')
);

ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_templates_clan_fixed_targets CHECK (
    pool <> 'clan'
    OR event_targets IS NOT NULL
    OR type NOT IN ('STREAK_SUM_N', 'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
                    'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N')
);

ALTER TABLE user_missions
    ADD COLUMN clan_id           UUID REFERENCES clans(id),
    ADD COLUMN parent_mission_id UUID REFERENCES user_missions(id);

ALTER TABLE user_missions DROP CONSTRAINT chk_user_missions_pool;
ALTER TABLE user_missions ADD CONSTRAINT chk_user_missions_pool
    CHECK (pool IN ('daily', 'weekly', 'event', 'community', 'clan'));

ALTER TABLE user_missions DROP CONSTRAINT chk_user_missions_community_owner;
ALTER TABLE user_missions ADD CONSTRAINT chk_user_missions_clan_owner
    CHECK ((pool = 'clan') = (clan_id IS NOT NULL));
ALTER TABLE user_missions ADD CONSTRAINT chk_user_missions_owner CHECK (
    CASE pool
        WHEN 'community' THEN user_id IS NULL AND parent_mission_id IS NULL
        WHEN 'clan' THEN (user_id IS NULL) = (parent_mission_id IS NULL)
        ELSE user_id IS NOT NULL AND parent_mission_id IS NULL
    END
);

DROP INDEX uq_user_missions_one_open_community;
CREATE UNIQUE INDEX uq_user_missions_one_open_community
    ON user_missions(template_id)
    WHERE pool = 'community' AND status = 'active';

CREATE UNIQUE INDEX uq_user_missions_one_open_clan
    ON user_missions(clan_id, template_id)
    WHERE pool = 'clan' AND parent_mission_id IS NULL AND status = 'active';

CREATE UNIQUE INDEX uq_user_missions_clan_member
    ON user_missions(parent_mission_id, user_id)
    WHERE parent_mission_id IS NOT NULL;

CREATE INDEX idx_user_missions_clan
    ON user_missions(clan_id, assigned_at DESC)
    WHERE clan_id IS NOT NULL;
