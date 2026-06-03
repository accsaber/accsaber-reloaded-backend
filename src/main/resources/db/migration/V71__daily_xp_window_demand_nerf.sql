UPDATE mission_templates
SET band_easy = 0.50,
    band_medium = 0.75,
    band_hard = 1.10,
    updated_at = NOW()
WHERE code = 'daily_xp_window';
