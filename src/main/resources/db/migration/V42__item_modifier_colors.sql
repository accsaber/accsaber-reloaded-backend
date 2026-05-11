ALTER TABLE item_modifiers
    ADD COLUMN color_hex TEXT;

UPDATE item_modifiers SET color_hex = '#9ca3af' WHERE key = 'normal';
UPDATE item_modifiers SET color_hex = '#fff4b8' WHERE key = 'unique';
UPDATE item_modifiers SET color_hex = '#476291' WHERE key = 'vintage';
UPDATE item_modifiers SET color_hex = '#4d7455' WHERE key = 'genuine';
UPDATE item_modifiers SET color_hex = '#cf6a32' WHERE key = 'strange';
UPDATE item_modifiers SET color_hex = '#8650ac' WHERE key = 'unusual';
UPDATE item_modifiers SET color_hex = '#38f3ab' WHERE key = 'haunted';
UPDATE item_modifiers SET color_hex = '#c8102e' WHERE key = 'jolly';
UPDATE item_modifiers SET color_hex = '#aa0000' WHERE key = 'collectors';
UPDATE item_modifiers SET color_hex = '#e879f9' WHERE key = 'holographic';
UPDATE item_modifiers SET color_hex = '#fafafa' WHERE key = 'decorated';
UPDATE item_modifiers SET color_hex = '#ff007a' WHERE key = 'ascendant';
UPDATE item_modifiers SET color_hex = '#8a6a4a' WHERE key = 'battle_worn';
UPDATE item_modifiers SET color_hex = '#ff8800' WHERE key = 'founders';

ALTER TABLE item_modifiers
    ALTER COLUMN color_hex SET NOT NULL;
