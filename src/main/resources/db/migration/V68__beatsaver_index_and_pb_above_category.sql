CREATE INDEX idx_maps_beatsaver_code_active
    ON maps (beatsaver_code)
    WHERE active = true AND beatsaver_code IS NOT NULL;

UPDATE mission_templates
SET description = 'Get {count} PBs on any {category} map where you already have a {threshold}+ AP play.'
WHERE code = 'daily_pb_above';

UPDATE mission_templates
SET description = 'Get {count} PBs on {category} maps where you already have a {threshold}+ AP play.'
WHERE code = 'weekly_pb_above';
