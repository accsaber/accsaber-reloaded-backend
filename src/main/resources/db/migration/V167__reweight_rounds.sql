CREATE TABLE reweight_rounds (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID        NOT NULL REFERENCES categories(id),
    reason      TEXT,
    map_count   INTEGER     NOT NULL,
    buffs       INTEGER     NOT NULL,
    nerfs       INTEGER     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reweight_rounds_category_created ON reweight_rounds (category_id, created_at);

ALTER TABLE map_difficulty_complexities
    ADD COLUMN round_id UUID CONSTRAINT fk_map_difficulty_complexities_reweight_rounds REFERENCES reweight_rounds(id);

CREATE INDEX idx_map_difficulty_complexities_round ON map_difficulty_complexities (round_id) WHERE round_id IS NOT NULL;

CREATE TEMP TABLE reweight_round_members ON COMMIT DROP AS
WITH changes AS (
    SELECT c.id, d.category_id, c.supersedes_reason AS reason, c.created_at,
           c.complexity, p.complexity AS previous,
           LAG(c.created_at) OVER w AS previous_at
    FROM map_difficulty_complexities c
    JOIN map_difficulty_complexities p ON p.id = c.supersedes_id
    JOIN map_difficulties d ON d.id = c.map_difficulty_id
    WHERE d.category_id IS NOT NULL
      AND d.ranked_at IS NOT NULL
      AND c.created_at >= d.ranked_at
    WINDOW w AS (PARTITION BY d.category_id, c.supersedes_reason ORDER BY c.created_at, c.id)
)
SELECT id, category_id, reason, created_at, complexity, previous,
       SUM(CASE WHEN previous_at IS NULL OR created_at - previous_at > INTERVAL '10 minutes' THEN 1 ELSE 0 END)
           OVER (PARTITION BY category_id, reason ORDER BY created_at, id) AS grp
FROM changes;

CREATE TEMP TABLE reweight_round_groups ON COMMIT DROP AS
SELECT gen_random_uuid() AS round_id, category_id, reason, grp,
       MIN(created_at) AS created_at,
       COUNT(*) AS map_count,
       COUNT(*) FILTER (WHERE complexity > previous) AS buffs,
       COUNT(*) FILTER (WHERE complexity < previous) AS nerfs
FROM reweight_round_members
GROUP BY category_id, reason, grp;

INSERT INTO reweight_rounds (id, category_id, reason, map_count, buffs, nerfs, created_at)
SELECT round_id, category_id, reason, map_count, buffs, nerfs, created_at
FROM reweight_round_groups;

UPDATE map_difficulty_complexities c
SET round_id = g.round_id
FROM reweight_round_members m
JOIN reweight_round_groups g
  ON g.category_id = m.category_id
 AND g.reason IS NOT DISTINCT FROM m.reason
 AND g.grp = m.grp
WHERE c.id = m.id;
