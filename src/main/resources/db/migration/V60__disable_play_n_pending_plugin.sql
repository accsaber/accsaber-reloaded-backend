UPDATE mission_templates SET active = false WHERE type = 'PLAY_N_MAPS';

ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_type;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_type CHECK (type IN (
    'PLAY_N_MAPS', 'XP_IN_WINDOW',
    'ACC_ON_MAP', 'AP_ON_MAP',
    'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
    'SNIPE_PLAYER_ON_MAP', 'STREAK_ON_MAP', 'STREAK_N_IN_CATEGORY',
    'COMEBACK_PB', 'SCORES_N'
));

INSERT INTO mission_templates (
    code, name, description, type, pool, weight, guaranteed_doable,
    xp_curve_id, xp_multiplier, band_easy, band_medium, band_hard,
    target_count_min, target_count_max
) VALUES
    ('daily_set_scores', 'Daily Run',
        'Set {count} new scores.',
        'SCORES_N', 'daily', 100, true,
        'acc00000-0000-0000-0000-000000000010', 1.00, 0.92, 1.00, 1.08, 1, 4);

UPDATE mission_templates
SET target_count_min = 2, target_count_max = 4
WHERE code = 'weekly_streak_n_in_category';
