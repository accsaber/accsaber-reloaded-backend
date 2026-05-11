ALTER TABLE items
    ADD COLUMN is_welcome_grant BOOLEAN NOT NULL DEFAULT false;

UPDATE item_modifiers SET description = 'Awarded on special occasions.'                              WHERE key = 'unique';
UPDATE item_modifiers SET description = 'Carries a particle / animated effect.'                      WHERE key = 'unusual';
UPDATE item_modifiers SET description = 'Special modifier for collectionist milestones or awards.'   WHERE key = 'collectors';
UPDATE item_modifiers SET description = 'High-tier modifier available only for the most skillful players.' WHERE key = 'ascendant';
UPDATE item_modifiers SET description = 'Visibly weathered. Earned in special events.'              WHERE key = 'battle_worn';

INSERT INTO items (
    type_id, name, description, rarity, tradeable, visible, active, deprecated,
    stackable, worth, requirement, is_welcome_grant, value
)
SELECT it.id, 'Dark', 'The default dark mode.', 'common',
    false, true, true, false, false, NULL, NULL, true,
    '{
        "tokens": {
            "bg-base": "#08080d",
            "bg-surface": "#11111c",
            "bg-elevated": "#1a1929",
            "bg-overlay": "#2d2a3c",
            "text-primary": "#e6e4ee",
            "text-secondary": "#8c87a3",
            "text-tertiary": "#5e5973",
            "accent": "#a855f7",
            "accent-overall": "#a855f7",
            "chart-grid": "rgba(255,255,255,0.06)",
            "chart-text": "#8888a0",
            "skeleton-base": "#2d2a3c",
            "skeleton-highlight": "color-mix(in srgb, #5e5973 25%, #2d2a3c)",
            "tint-true-acc": "#0f3d1e",
            "tint-standard-acc": "#162650",
            "tint-tech-acc": "#3d1414",
            "tint-low-mid": "#3d3508",
            "tint-overall": "#2d1650"
        }
    }'::jsonb
FROM item_types it
WHERE it.key = 'theme'
ON CONFLICT (type_id, name) DO NOTHING;

INSERT INTO items (
    type_id, name, description, rarity, tradeable, visible, active, deprecated,
    stackable, worth, requirement, is_welcome_grant, value
)
SELECT it.id, 'Light', 'The default light mode.', 'common',
    false, true, true, false, false, NULL, NULL, true,
    '{
        "tokens": {
            "bg-base": "#f3f2f7",
            "bg-surface": "#fdfcff",
            "bg-elevated": "#ebe9f1",
            "bg-overlay": "#cfccda",
            "text-primary": "#1a1726",
            "text-secondary": "#6a6580",
            "text-tertiary": "#9b96b0",
            "accent": "#a855f7",
            "accent-overall": "#a855f7",
            "chart-grid": "rgba(0,0,0,0.08)",
            "chart-text": "#6a6a82",
            "skeleton-base": "#ebe9f1",
            "skeleton-highlight": "color-mix(in srgb, #9b96b0 35%, #ebe9f1)",
            "tint-true-acc": "#d4f5e0",
            "tint-standard-acc": "#d4e4fd",
            "tint-tech-acc": "#fdd4d4",
            "tint-low-mid": "#faf0c8",
            "tint-overall": "#ead4fd"
        }
    }'::jsonb
FROM item_types it
WHERE it.key = 'theme'
ON CONFLICT (type_id, name) DO NOTHING;

INSERT INTO user_item_links (user_id, item_id, source, source_id, awarded_at, reason)
SELECT u.id, i.id, 'manual', 'welcome', NOW(), 'Welcome grant'
FROM users u
CROSS JOIN items i
WHERE i.is_welcome_grant = true
AND i.active = true
AND NOT EXISTS (
    SELECT 1 FROM user_item_links uil
    WHERE uil.user_id = u.id AND uil.item_id = i.id
);

INSERT INTO user_item_link_modifiers (user_item_link_id, modifier_id)
SELECT l.id, m.id
FROM user_item_links l
JOIN items i ON i.id = l.item_id
JOIN item_modifiers m ON m.key = 'normal'
WHERE i.is_welcome_grant = true
AND l.source_id = 'welcome'
AND NOT EXISTS (
    SELECT 1 FROM user_item_link_modifiers existing
    WHERE existing.user_item_link_id = l.id
);
