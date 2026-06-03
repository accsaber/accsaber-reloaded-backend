ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_type;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_type CHECK (type IN (
    'PLAY_N_MAPS', 'XP_IN_WINDOW',
    'ACC_ON_MAP', 'AP_ON_MAP',
    'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
    'SNIPE_PLAYER_ON_MAP', 'STREAK_ON_MAP', 'STREAK_N_IN_CATEGORY',
    'COMEBACK_PB'
));

INSERT INTO mission_templates (
    code, name, description, type, pool, weight, guaranteed_doable,
    xp_curve_id, xp_multiplier, band_easy, band_medium, band_hard,
    target_count_min, target_count_max
) VALUES
    ('daily_comeback', 'Comeback',
        'Beat your old PB on {map}.',
        'COMEBACK_PB', 'daily', 70, false,
        'acc00000-0000-0000-0000-000000000010', 1.15, 0.95, 1.02, 1.10, NULL, NULL);
