ALTER TABLE items
    ADD COLUMN unlock_level INTEGER;

CREATE INDEX idx_items_unlock_level ON items(unlock_level) WHERE unlock_level IS NOT NULL;

INSERT INTO items (type_id, name, description, value, rarity, tradeable, visible, unlock_level) VALUES
((SELECT id FROM item_types WHERE key = 'profile_border_shape'),
 'Default Frame',
 'The standard rounded-square frame every player starts with.',
 '{"viewBox":"0 0 100 100","states":[{"atMs":0,"paths":[{"d":"M14,0 L86,0 Q100,0 100,14 L100,86 Q100,100 86,100 L14,100 Q0,100 0,86 L0,14 Q0,0 14,0 Z","fill":"currentColor","stroke":"none"}]}]}'::jsonb,
 'common', false, true, 0),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Newcomer Border Color',
 'The starting tier hue.',
 '{"states":[{"atMs":0,"fill":{"type":"solid","hex":"#6b7280"}}]}'::jsonb,
 'common', false, true, 0),

((SELECT id FROM item_types WHERE key = 'title'),
 'Newcomer',
 'Awarded to all new arrivals.',
 '{"text":"Newcomer","states":[{"atMs":0,"color":"#6b7280"}]}'::jsonb,
 'common', false, true, 0),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Apprentice Border Color',
 'A clean blue for those finding their stride.',
 '{"states":[{"atMs":0,"fill":{"type":"solid","hex":"#3b82f6"}}]}'::jsonb,
 'common', false, true, 10),

((SELECT id FROM item_types WHERE key = 'title'),
 'Apprentice',
 'You are no longer new here.',
 '{"text":"Apprentice","states":[{"atMs":0,"color":"#3b82f6"}]}'::jsonb,
 'common', false, true, 10),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Adept Border Color',
 'Emerald with a soft halo.',
 '{"states":[{"atMs":0,"fill":{"type":"solid","hex":"#10b981"},"filters":[{"type":"glow","color":"rgba(16,185,129,0.3)","blurPx":6}]}]}'::jsonb,
 'uncommon', false, true, 20),

((SELECT id FROM item_types WHERE key = 'title'),
 'Adept',
 'Skill is starting to show.',
 '{"text":"Adept","states":[{"atMs":0,"color":"#10b981"}]}'::jsonb,
 'uncommon', false, true, 20),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Skilled Border Color',
 'Bronze with a warm glow.',
 '{"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":135,"stops":[{"atPct":0,"hex":"#92400e"},{"atPct":25,"hex":"#cd7f32"},{"atPct":50,"hex":"#e8a84c"},{"atPct":75,"hex":"#cd7f32"},{"atPct":100,"hex":"#92400e"}]},"filters":[{"type":"glow","color":"rgba(205,127,50,0.35)","blurPx":8}]}]}'::jsonb,
 'uncommon', false, true, 30),

((SELECT id FROM item_types WHERE key = 'title'),
 'Skilled',
 'Your name carries weight now.',
 '{"text":"Skilled","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#92400e"},{"atPct":50,"hex":"#e8a84c"},{"atPct":100,"hex":"#92400e"}]}}]}'::jsonb,
 'uncommon', false, true, 30),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Expert Border Color',
 'Polished silver with a sheen.',
 '{"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":135,"stops":[{"atPct":0,"hex":"#9090a0"},{"atPct":25,"hex":"#c0c0d0"},{"atPct":50,"hex":"#f0f0ff"},{"atPct":75,"hex":"#c0c0d0"},{"atPct":100,"hex":"#9090a0"}]},"filters":[{"type":"glow","color":"rgba(192,192,210,0.4)","blurPx":10}]}]}'::jsonb,
 'rare', false, true, 40),

((SELECT id FROM item_types WHERE key = 'title'),
 'Expert',
 'Mastery within reach.',
 '{"text":"Expert","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#9090a0"},{"atPct":50,"hex":"#f0f0ff"},{"atPct":100,"hex":"#9090a0"}]}}]}'::jsonb,
 'rare', false, true, 40),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Master Border Color',
 'Gold with a layered glow.',
 '{"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":135,"stops":[{"atPct":0,"hex":"#a16207"},{"atPct":25,"hex":"#fbbf24"},{"atPct":50,"hex":"#fde68a"},{"atPct":75,"hex":"#fbbf24"},{"atPct":100,"hex":"#a16207"}]},"filters":[{"type":"glow","color":"rgba(251,191,36,0.35)","blurPx":10},{"type":"glow","color":"rgba(251,191,36,0.12)","blurPx":22}]}]}'::jsonb,
 'rare', false, true, 50),

((SELECT id FROM item_types WHERE key = 'title'),
 'Master',
 'Few make it this far.',
 '{"text":"Master","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#a16207"},{"atPct":50,"hex":"#fde68a"},{"atPct":100,"hex":"#a16207"}]}}]}'::jsonb,
 'rare', false, true, 50),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Grandmaster Border Color',
 'Violet that drifts around the frame.',
 '{"loop":"loop","durationMs":6000,"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":0,"stops":[{"atPct":0,"hex":"#6d28d9"},{"atPct":25,"hex":"#8b5cf6"},{"atPct":50,"hex":"#a78bfa"},{"atPct":75,"hex":"#8b5cf6"},{"atPct":100,"hex":"#6d28d9"}]},"filters":[{"type":"glow","color":"rgba(139,92,246,0.4)","blurPx":12},{"type":"glow","color":"rgba(139,92,246,0.15)","blurPx":24}]},{"atMs":6000,"fill":{"type":"linear","angleDeg":360,"stops":[{"atPct":0,"hex":"#6d28d9"},{"atPct":25,"hex":"#8b5cf6"},{"atPct":50,"hex":"#a78bfa"},{"atPct":75,"hex":"#8b5cf6"},{"atPct":100,"hex":"#6d28d9"}]}}]}'::jsonb,
 'epic', false, true, 60),

((SELECT id FROM item_types WHERE key = 'title'),
 'Grandmaster',
 'You are spoken about.',
 '{"text":"Grandmaster","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#6d28d9"},{"atPct":50,"hex":"#a78bfa"},{"atPct":100,"hex":"#6d28d9"}]}}]}'::jsonb,
 'epic', false, true, 60),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Legend Border Color',
 'Burning orange that circles the frame.',
 '{"loop":"loop","durationMs":5000,"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":0,"stops":[{"atPct":0,"hex":"#c2410c"},{"atPct":25,"hex":"#f97316"},{"atPct":50,"hex":"#fdba74"},{"atPct":75,"hex":"#f97316"},{"atPct":100,"hex":"#c2410c"}]},"filters":[{"type":"glow","color":"rgba(249,115,22,0.45)","blurPx":12},{"type":"glow","color":"rgba(249,115,22,0.18)","blurPx":24}]},{"atMs":5000,"fill":{"type":"linear","angleDeg":360,"stops":[{"atPct":0,"hex":"#c2410c"},{"atPct":25,"hex":"#f97316"},{"atPct":50,"hex":"#fdba74"},{"atPct":75,"hex":"#f97316"},{"atPct":100,"hex":"#c2410c"}]}}]}'::jsonb,
 'epic', false, true, 70),

((SELECT id FROM item_types WHERE key = 'title'),
 'Legend',
 'A name people pass around.',
 '{"text":"Legend","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#c2410c"},{"atPct":50,"hex":"#fdba74"},{"atPct":100,"hex":"#c2410c"}]}}]}'::jsonb,
 'epic', false, true, 70),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Transcendent Border Color',
 'Cyan light that cycles around the frame.',
 '{"loop":"loop","durationMs":4000,"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":0,"stops":[{"atPct":0,"hex":"#0891b2"},{"atPct":25,"hex":"#22d3ee"},{"atPct":50,"hex":"#a5f3fc"},{"atPct":75,"hex":"#22d3ee"},{"atPct":100,"hex":"#0891b2"}]},"filters":[{"type":"glow","color":"rgba(34,211,238,0.5)","blurPx":12},{"type":"glow","color":"rgba(34,211,238,0.2)","blurPx":24},{"type":"glow","color":"rgba(165,243,252,0.3)","blurPx":4}]},{"atMs":4000,"fill":{"type":"linear","angleDeg":360,"stops":[{"atPct":0,"hex":"#0891b2"},{"atPct":25,"hex":"#22d3ee"},{"atPct":50,"hex":"#a5f3fc"},{"atPct":75,"hex":"#22d3ee"},{"atPct":100,"hex":"#0891b2"}]}}]}'::jsonb,
 'legendary', false, true, 80),

((SELECT id FROM item_types WHERE key = 'title'),
 'Transcendent',
 'Beyond the rest.',
 '{"text":"Transcendent","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#0891b2"},{"atPct":50,"hex":"#a5f3fc"},{"atPct":100,"hex":"#0891b2"}]}}]}'::jsonb,
 'legendary', false, true, 80),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Mythic Border Color',
 'Fire that breathes.',
 '{"loop":"loop","durationMs":3000,"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":0,"stops":[{"atPct":0,"hex":"#7f1d1d"},{"atPct":17,"hex":"#dc2626"},{"atPct":33,"hex":"#f97316"},{"atPct":50,"hex":"#fbbf24"},{"atPct":67,"hex":"#f97316"},{"atPct":83,"hex":"#dc2626"},{"atPct":100,"hex":"#7f1d1d"}]},"filters":[{"type":"glow","color":"rgba(220,38,38,0.6)","blurPx":14},{"type":"glow","color":"rgba(249,115,22,0.25)","blurPx":28},{"type":"radial_pulse","atXPct":30,"atYPct":15,"color":"rgba(251,191,36,0.4)","spreadPct":45,"durationMs":3500},{"type":"radial_pulse","atXPct":80,"atYPct":25,"color":"rgba(249,115,22,0.3)","spreadPct":45,"durationMs":4700}]},{"atMs":3000,"fill":{"type":"linear","angleDeg":360,"stops":[{"atPct":0,"hex":"#7f1d1d"},{"atPct":17,"hex":"#dc2626"},{"atPct":33,"hex":"#f97316"},{"atPct":50,"hex":"#fbbf24"},{"atPct":67,"hex":"#f97316"},{"atPct":83,"hex":"#dc2626"},{"atPct":100,"hex":"#7f1d1d"}]}}]}'::jsonb,
 'legendary', false, true, 90),

((SELECT id FROM item_types WHERE key = 'title'),
 'Mythic',
 'Spoken of in hushed voices.',
 '{"text":"Mythic","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#7f1d1d"},{"atPct":50,"hex":"#fbbf24"},{"atPct":100,"hex":"#7f1d1d"}]}}]}'::jsonb,
 'legendary', false, true, 90),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Ascendant Border Color',
 'Pastel hue with sparkles riding the rim.',
 '{"loop":"loop","durationMs":2500,"states":[{"atMs":0,"fill":{"type":"linear","angleDeg":0,"stops":[{"atPct":0,"hex":"#f472b6"},{"atPct":17,"hex":"#a78bfa"},{"atPct":33,"hex":"#818cf8"},{"atPct":50,"hex":"#60a5fa"},{"atPct":67,"hex":"#a78bfa"},{"atPct":83,"hex":"#e879f9"},{"atPct":100,"hex":"#f472b6"}]},"filters":[{"type":"glow","color":"rgba(244,114,182,0.55)","blurPx":14},{"type":"glow","color":"rgba(168,139,250,0.25)","blurPx":28},{"type":"glint","atXPct":15,"atYPct":15,"color":"rgba(255,255,255,0.9)","sizePx":4,"durationMs":2200},{"type":"glint","atXPct":85,"atYPct":85,"color":"rgba(255,255,255,0.9)","sizePx":4,"durationMs":2700}]},{"atMs":2500,"fill":{"type":"linear","angleDeg":360,"stops":[{"atPct":0,"hex":"#f472b6"},{"atPct":17,"hex":"#a78bfa"},{"atPct":33,"hex":"#818cf8"},{"atPct":50,"hex":"#60a5fa"},{"atPct":67,"hex":"#a78bfa"},{"atPct":83,"hex":"#e879f9"},{"atPct":100,"hex":"#f472b6"}]}}]}'::jsonb,
 'mythic', false, true, 100),

((SELECT id FROM item_types WHERE key = 'title'),
 'Ascendant',
 'You are above the leaderboard now.',
 '{"text":"Ascendant","states":[{"atMs":0,"gradient":{"type":"linear","angleDeg":90,"stops":[{"atPct":0,"hex":"#f472b6"},{"atPct":50,"hex":"#60a5fa"},{"atPct":100,"hex":"#e879f9"}]}}]}'::jsonb,
 'mythic', false, true, 100)

ON CONFLICT (type_id, name) DO NOTHING;

WITH level_curve AS (
    SELECT x_parameter_value AS base, y_parameter_value AS exponent
    FROM curves
    WHERE id = 'acc00000-0000-0000-0000-000000000004'
),
levels AS (
    SELECT
        n AS level,
        FLOOR((SELECT base FROM level_curve) * POWER(LEAST(n, 100), (SELECT exponent FROM level_curve))) AS xp_for_level
    FROM generate_series(1, 100) AS n
),
cumulative AS (
    SELECT level, SUM(xp_for_level) OVER (ORDER BY level) AS cumulative_xp
    FROM levels
),
user_levels AS (
    SELECT
        u.id AS user_id,
        COALESCE((SELECT MAX(c.level) FROM cumulative c WHERE c.cumulative_xp <= u.total_xp), 0) AS user_level
    FROM users u
)
INSERT INTO user_item_links (user_id, item_id, modifier_id, source, source_id, awarded_at)
SELECT
    ul.user_id,
    i.id,
    (SELECT id FROM item_modifiers WHERE key = 'normal'),
    'level',
    i.unlock_level::text,
    NOW()
FROM user_levels ul
CROSS JOIN items i
WHERE i.unlock_level IS NOT NULL
  AND i.unlock_level <= ul.user_level
  AND i.active = true
ON CONFLICT (user_id, item_id, source, source_id) DO NOTHING;
