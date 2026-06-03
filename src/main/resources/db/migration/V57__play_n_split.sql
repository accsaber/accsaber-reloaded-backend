UPDATE mission_templates
SET description = 'Play {count} ranked maps in {category}.'
WHERE code = 'daily_play_n';

INSERT INTO mission_templates (
    code, name, description, type, pool, weight, guaranteed_doable,
    xp_curve_id, xp_multiplier, band_easy, band_medium, band_hard,
    target_count_min, target_count_max
) VALUES
    ('daily_play_n_any', 'Daily Mileage',
     'Play {count} ranked maps.',
     'PLAY_N_MAPS', 'daily', 80, true,
     'acc00000-0000-0000-0000-000000000010', 1.00, 0.92, 1.00, 1.08, 3, 12);
