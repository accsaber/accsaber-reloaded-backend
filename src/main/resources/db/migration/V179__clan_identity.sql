ALTER TABLE clans
    ADD COLUMN icon_url  TEXT,
    ADD COLUMN tag_color TEXT,
    ADD CONSTRAINT chk_clans_tag_color CHECK (tag_color ~ '^#[0-9a-f]{6}$');

CREATE TEMP TABLE retired_clan_emblems ON COMMIT DROP AS
SELECT i.id
FROM items i
JOIN item_types t ON t.id = i.type_id
WHERE t.key = 'clan_emblem';

DELETE FROM clan_equipped_items WHERE item_id IN (SELECT id FROM retired_clan_emblems);
DELETE FROM clan_items WHERE item_id IN (SELECT id FROM retired_clan_emblems);
DELETE FROM clan_level_items WHERE item_id IN (SELECT id FROM retired_clan_emblems);
DELETE FROM clan_season_rewards WHERE item_id IN (SELECT id FROM retired_clan_emblems);
DELETE FROM clan_war_reward_items WHERE item_id IN (SELECT id FROM retired_clan_emblems);
DELETE FROM items WHERE id IN (SELECT id FROM retired_clan_emblems);
DELETE FROM item_types WHERE key = 'clan_emblem';

UPDATE item_types
SET key = 'clan_title_effect',
    name = 'Clan Title Effect',
    description = 'How the clan name renders on clan pages. The title contract without text, since the text is the clan name.'
WHERE key = 'clan_tag_effect';

INSERT INTO item_types (parent_type_id, key, name, description, value_schema)
SELECT parent.id, 'clan_tag_card', 'Clan Tag Card',
       'The card behind the clan tag next to every member name. The border colour fill contract, and it replaces the clan''s plain tag colour while equipped.',
       contract.value_schema
FROM item_types parent, item_types contract
WHERE parent.key = 'clan_cosmetic' AND contract.key = 'profile_border_color';
