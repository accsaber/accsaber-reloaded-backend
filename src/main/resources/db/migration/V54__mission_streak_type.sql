ALTER TABLE user_missions
    ADD COLUMN target_streak INTEGER,
    ADD COLUMN band TEXT NOT NULL DEFAULT 'medium';

ALTER TABLE user_missions
    ADD CONSTRAINT chk_user_missions_band CHECK (band IN ('easy', 'medium', 'hard', 'extreme'));

ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_type;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_type CHECK (type IN (
    'PLAY_N_MAPS', 'XP_IN_WINDOW',
    'ACC_ON_MAP', 'AP_ON_MAP',
    'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
    'SNIPE_PLAYER_ON_MAP', 'STREAK_ON_MAP', 'STREAK_N_IN_CATEGORY'
));

UPDATE mission_templates
SET target_count_min = 15, target_count_max = 30
WHERE code = 'weekly_play_n';

INSERT INTO mission_templates (
    code, name, description, type, pool, weight, guaranteed_doable,
    xp_curve_id, xp_multiplier, band_easy, band_medium, band_hard,
    target_count_min, target_count_max
) VALUES
    ('daily_streak_on_map', 'Streak Chaser',
     'Hit a {streak}-note 115 streak on {map}.',
     'STREAK_ON_MAP', 'daily', 70, false,
     'acc00000-0000-0000-0000-000000000010', 1.15, 0.92, 1.00, 1.08, NULL, NULL),

    ('weekly_streak_n_in_category', 'Streak Runner',
     'Hit a {streak}-note 115 streak on {count} ranked maps in {category} this week.',
     'STREAK_N_IN_CATEGORY', 'weekly', 90, false,
     'acc00000-0000-0000-0000-000000000011', 1.15, 0.92, 1.00, 1.08, 3, 6);
