ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_type;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_type CHECK (type IN (
    'PLAY_N_MAPS', 'XP_IN_WINDOW',
    'ACC_ON_MAP', 'AP_ON_MAP',
    'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
    'SNIPE_PLAYER_ON_MAP', 'STREAK_ON_MAP', 'STREAK_N_IN_CATEGORY',
    'STREAK_SUM_N', 'COMEBACK_PB', 'SCORES_N',
    'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
    'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N'
));

ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_templates_streak_sum_event_only;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_templates_event_only_types CHECK (
    type NOT IN ('STREAK_SUM_N', 'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
                 'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N')
    OR pool = 'event'
);

ALTER TABLE user_missions
    ADD COLUMN target_ranked_before TIMESTAMPTZ,
    ADD COLUMN target_curated_only  BOOLEAN,
    ADD COLUMN progress_ap          NUMERIC(20,6) NOT NULL DEFAULT 0;
