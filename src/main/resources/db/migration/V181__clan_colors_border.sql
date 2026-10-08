ALTER TABLE clans
    ADD COLUMN primary_color   TEXT,
    ADD COLUMN secondary_color TEXT,
    ADD CONSTRAINT chk_clans_primary_color CHECK (primary_color ~ '^#[0-9a-f]{6}$'),
    ADD CONSTRAINT chk_clans_secondary_color CHECK (secondary_color ~ '^#[0-9a-f]{6}$');

INSERT INTO item_types (parent_type_id, key, name, description, value_schema)
SELECT parent.id, 'clan_border', 'Clan Border',
       'The frame around the clan icon on every clan surface. The border shape contract, drawn in the clan''s primary colour.',
       contract.value_schema
FROM item_types parent, item_types contract
WHERE parent.key = 'clan_cosmetic' AND contract.key = 'profile_border_shape';
