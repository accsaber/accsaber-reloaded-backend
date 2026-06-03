UPDATE mission_templates
SET target_count_max = 3,
    target_count_min = LEAST(target_count_min, 3)
WHERE code = 'weekly_streak_n_in_category';
