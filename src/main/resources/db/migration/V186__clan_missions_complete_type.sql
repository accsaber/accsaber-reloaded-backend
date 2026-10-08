ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_template_type;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_template_type CHECK (type IN (
    'PLAY_N_MAPS', 'XP_IN_WINDOW',
    'ACC_ON_MAP', 'AP_ON_MAP',
    'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
    'SNIPE_PLAYER_ON_MAP', 'STREAK_ON_MAP', 'STREAK_N_IN_CATEGORY',
    'STREAK_SUM_N', 'COMEBACK_PB', 'SCORES_N',
    'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
    'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N', 'MISSIONS_COMPLETE_N'
));

ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_templates_fixed_target_types;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_templates_fixed_target_types CHECK (
    type NOT IN ('STREAK_SUM_N', 'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
                 'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N', 'MISSIONS_COMPLETE_N')
    OR pool IN ('event', 'community', 'clan')
);

ALTER TABLE mission_templates DROP CONSTRAINT chk_mission_templates_clan_fixed_targets;
ALTER TABLE mission_templates ADD CONSTRAINT chk_mission_templates_clan_fixed_targets CHECK (
    pool <> 'clan'
    OR event_targets IS NOT NULL
    OR type NOT IN ('STREAK_SUM_N', 'SNIPE_RIVAL_ANY_MAP', 'AP_GAIN_OVERALL', 'BATCH_PLAY_N',
                    'PB_RANKED_BEFORE_N', 'CAMPAIGN_COMPLETE_N', 'MISSIONS_COMPLETE_N')
);

INSERT INTO mission_templates (code, name, description, type, pool, weight, xp_curve_id, xp_multiplier, fixed_xp,
                               band_easy, band_medium, band_hard, target_count_min, target_count_max, event_targets)
VALUES
    ('clan_missions_pooled', 'Mission grind', 'The clan clears its daily and weekly missions together this week.',
     'MISSIONS_COMPLETE_N', 'clan', 100, NULL, 1.0, 150, 0.92, 1.00, 1.08, NULL, NULL,
     '{"count": 15}')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    pool = EXCLUDED.pool,
    weight = EXCLUDED.weight,
    xp_curve_id = EXCLUDED.xp_curve_id,
    xp_multiplier = EXCLUDED.xp_multiplier,
    fixed_xp = EXCLUDED.fixed_xp,
    band_easy = EXCLUDED.band_easy,
    band_medium = EXCLUDED.band_medium,
    band_hard = EXCLUDED.band_hard,
    target_count_min = EXCLUDED.target_count_min,
    target_count_max = EXCLUDED.target_count_max,
    event_targets = EXCLUDED.event_targets,
    active = true,
    updated_at = NOW();
