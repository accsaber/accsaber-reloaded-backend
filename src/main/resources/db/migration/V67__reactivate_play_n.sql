UPDATE mission_templates
SET active = true,
    weight = 40,
    target_count_min = 2,
    target_count_max = 7
WHERE code IN ('daily_play_n', 'daily_play_n_any');

UPDATE mission_templates
SET active = true,
    weight = 40,
    target_count_min = 6,
    target_count_max = 20
WHERE code = 'weekly_play_n';
