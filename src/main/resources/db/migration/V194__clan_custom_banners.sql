ALTER TABLE clans ADD COLUMN banner_url TEXT;

CREATE TEMP TABLE retired_clan_banners ON COMMIT DROP AS
SELECT i.id
FROM items i
JOIN item_types t ON t.id = i.type_id
WHERE t.key = 'clan_banner';

DELETE FROM clan_equipped_items WHERE item_id IN (SELECT id FROM retired_clan_banners);
DELETE FROM clan_items WHERE item_id IN (SELECT id FROM retired_clan_banners);
DELETE FROM clan_level_items WHERE item_id IN (SELECT id FROM retired_clan_banners);
DELETE FROM clan_season_rewards WHERE item_id IN (SELECT id FROM retired_clan_banners);
DELETE FROM clan_war_reward_items WHERE item_id IN (SELECT id FROM retired_clan_banners);
DELETE FROM items WHERE id IN (SELECT id FROM retired_clan_banners);
DELETE FROM item_types WHERE key = 'clan_banner';
