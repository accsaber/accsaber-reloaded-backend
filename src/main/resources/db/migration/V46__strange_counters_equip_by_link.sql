ALTER TABLE items
    ADD COLUMN requirement TEXT;

CREATE TABLE user_item_link_counters (
    user_item_link_id UUID         NOT NULL REFERENCES user_item_links(id) ON DELETE CASCADE,
    stat_key          TEXT         NOT NULL,
    value             BIGINT       NOT NULL DEFAULT 0 CHECK (value >= 0),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_item_link_id, stat_key)
);

CREATE INDEX idx_user_item_link_counters_link ON user_item_link_counters(user_item_link_id);

WITH resolved AS (
    SELECT us.id AS us_id,
           (SELECT l.id::text
            FROM user_item_links l
            WHERE l.user_id = us.user_id
              AND l.item_id::text = (us.value #>> '{}')
            ORDER BY l.awarded_at DESC
            LIMIT 1) AS link_id
    FROM user_settings us
    WHERE us.key LIKE 'equipped.%'
      AND us.value IS NOT NULL
      AND us.value <> 'null'::jsonb
)
UPDATE user_settings us
SET value = to_jsonb(r.link_id)
FROM resolved r
WHERE us.id = r.us_id
  AND r.link_id IS NOT NULL;

DELETE FROM user_settings
WHERE key LIKE 'equipped.%'
  AND value IS NOT NULL
  AND value <> 'null'::jsonb
  AND NOT EXISTS (
      SELECT 1 FROM user_item_links l
      WHERE l.id::text = (value #>> '{}')
  );
