DELETE FROM curve_points
WHERE curve_id IN (
    'acc00000-0000-0000-0000-000000000010',
    'acc00000-0000-0000-0000-000000000011'
);

INSERT INTO curve_points (curve_id, x, y) VALUES
    ('acc00000-0000-0000-0000-000000000010', 0,   20),
    ('acc00000-0000-0000-0000-000000000010', 20,  50),
    ('acc00000-0000-0000-0000-000000000010', 40,  95),
    ('acc00000-0000-0000-0000-000000000010', 50,  120),
    ('acc00000-0000-0000-0000-000000000010', 60,  150),
    ('acc00000-0000-0000-0000-000000000010', 70,  175),
    ('acc00000-0000-0000-0000-000000000010', 80,  205),
    ('acc00000-0000-0000-0000-000000000010', 90,  235),
    ('acc00000-0000-0000-0000-000000000010', 100, 265);

INSERT INTO curve_points (curve_id, x, y) VALUES
    ('acc00000-0000-0000-0000-000000000011', 0,   85),
    ('acc00000-0000-0000-0000-000000000011', 20,  175),
    ('acc00000-0000-0000-0000-000000000011', 40,  320),
    ('acc00000-0000-0000-0000-000000000011', 50,  420),
    ('acc00000-0000-0000-0000-000000000011', 60,  520),
    ('acc00000-0000-0000-0000-000000000011', 70,  610),
    ('acc00000-0000-0000-0000-000000000011', 80,  690),
    ('acc00000-0000-0000-0000-000000000011', 90,  755),
    ('acc00000-0000-0000-0000-000000000011', 100, 790);

UPDATE mission_templates
SET description = 'Hit {acc}+ accuracy on {map}.'
WHERE code = 'daily_acc_on_map';

UPDATE mission_templates
SET description = 'Score {ap}+ AP on {map}.'
WHERE code = 'daily_ap_on_map';

UPDATE mission_templates
SET description = 'Get {count} PBs on any map where you already have a {threshold}+ AP play.'
WHERE code = 'daily_pb_above';

UPDATE mission_templates
SET description = 'Score {ap}+ AP on {map}.'
WHERE code = 'weekly_ap_on_map';

UPDATE mission_templates
SET description = 'Get {count} PBs on maps where you already have a {threshold}+ AP play.'
WHERE code = 'weekly_pb_above';
