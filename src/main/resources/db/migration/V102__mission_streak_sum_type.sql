ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_type;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_type CHECK (type IN (
    'PLAY_N_MAPS', 'XP_IN_WINDOW',
    'ACC_ON_MAP', 'AP_ON_MAP',
    'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
    'SNIPE_PLAYER_ON_MAP', 'STREAK_ON_MAP', 'STREAK_N_IN_CATEGORY',
    'STREAK_SUM_N', 'COMEBACK_PB', 'SCORES_N'
));

ALTER TABLE mission_templates
    ADD CONSTRAINT chk_mission_templates_streak_sum_event_only
        CHECK (type <> 'STREAK_SUM_N' OR pool = 'event');
