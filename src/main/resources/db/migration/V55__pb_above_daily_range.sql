UPDATE mission_templates
SET target_count_min = 1,
    target_count_max = 2,
    description      = 'Get {count} PBs on any map where you already have a {threshold} AP play.'
WHERE code = 'daily_pb_above';

UPDATE mission_templates
SET description = 'Earn {xp} XP from any source.'
WHERE code = 'daily_xp_window';

UPDATE mission_templates
SET description = 'Play {count} ranked maps in {category}.'
WHERE code = 'weekly_play_n';

UPDATE mission_templates
SET description = 'Get {count} PBs on maps where you already have a {threshold} AP play.'
WHERE code = 'weekly_pb_above';

UPDATE mission_templates
SET description = 'Beat {player}''s score on {map}.'
WHERE code = 'weekly_snipe';

UPDATE mission_templates
SET description = 'Hit a {streak}-note 115 streak on {count} ranked maps in {category}.'
WHERE code = 'weekly_streak_n_in_category';
