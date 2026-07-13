ALTER TABLE events ADD COLUMN slug TEXT;

WITH slugified AS (
    SELECT id,
           NULLIF(trim(both '-' from regexp_replace(lower(title), '[^a-z0-9]+', '-', 'g')), '') AS base,
           row_number() OVER (
               PARTITION BY NULLIF(trim(both '-' from regexp_replace(lower(title), '[^a-z0-9]+', '-', 'g')), '')
               ORDER BY created_at, id) AS rn
    FROM events
)
UPDATE events e
SET slug = CASE
               WHEN s.base IS NULL THEN 'event-' || left(e.id::text, 8)
               WHEN s.rn = 1        THEN s.base
               ELSE s.base || '-' || s.rn
           END
FROM slugified s
WHERE e.id = s.id;

ALTER TABLE events ALTER COLUMN slug SET NOT NULL;

CREATE UNIQUE INDEX ux_events_slug ON events(slug);
