ALTER TABLE clan_war_participants ADD COLUMN revive_at TIMESTAMPTZ, ADD COLUMN revive_speed DOUBLE PRECISION;

UPDATE clan_war_participants SET revive_at = broken_at + INTERVAL '7 days', revive_speed = 1
WHERE broken_at IS NOT NULL;
