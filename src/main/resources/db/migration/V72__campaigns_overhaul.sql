ALTER TABLE campaigns
    ALTER COLUMN creator_id DROP NOT NULL,
    ADD COLUMN creator_alias           TEXT,
    ADD COLUMN status                  TEXT          NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'editing', 'curated')),
    ADD COLUMN seeking_curation        BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN progression_agnostic    BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN completion_mode         TEXT          NOT NULL DEFAULT 'terminal' CHECK (completion_mode IN ('terminal', 'all')),
    ADD COLUMN legacy                  BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN completion_xp           NUMERIC(20,6) NOT NULL DEFAULT 0,
    ADD COLUMN summary                 TEXT,
    ADD COLUMN slug                    TEXT,
    ADD COLUMN curator_notes           TEXT,
    ADD COLUMN playlist_export_enabled BOOLEAN       NOT NULL DEFAULT false,
    ADD COLUMN submitted_at            TIMESTAMPTZ,
    ADD COLUMN curated_at              TIMESTAMPTZ,
    ADD COLUMN curated_by              UUID          REFERENCES staff_users(id);

UPDATE campaigns SET status = 'curated', curated_at = updated_at WHERE verified = true;

ALTER TABLE campaigns DROP COLUMN verified;
ALTER TABLE campaigns DROP COLUMN difficulty;

WITH base AS (
    SELECT id,
           trim(both '-' from regexp_replace(lower(name), '[^a-z0-9]+', '-', 'g')) AS s
    FROM campaigns
),
normalized AS (
    SELECT id, CASE WHEN s = '' THEN 'campaign' ELSE s END AS base_slug
    FROM base
),
numbered AS (
    SELECT id, base_slug,
           row_number() OVER (PARTITION BY base_slug ORDER BY id) AS rn
    FROM normalized
)
UPDATE campaigns c
SET slug = CASE WHEN n.rn = 1 THEN n.base_slug ELSE n.base_slug || '-' || n.rn END
FROM numbered n
WHERE c.id = n.id;

ALTER TABLE campaigns
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT campaigns_slug_unique UNIQUE (slug),
    ADD CONSTRAINT campaigns_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    ADD CONSTRAINT campaigns_creator_present CHECK (creator_id IS NOT NULL OR creator_alias IS NOT NULL);

CREATE INDEX idx_campaigns_status           ON campaigns(status);
CREATE INDEX idx_campaigns_seeking_curation ON campaigns(seeking_curation) WHERE seeking_curation = true;
CREATE INDEX idx_campaigns_legacy           ON campaigns(legacy)           WHERE legacy = true;

ALTER TABLE campaign_maps RENAME TO campaign_difficulties;

ALTER INDEX idx_campaign_maps_campaign       RENAME TO idx_campaign_difficulties_campaign;
ALTER INDEX idx_campaign_maps_map_difficulty RENAME TO idx_campaign_difficulties_map_difficulty;

DROP TRIGGER IF EXISTS trg_campaign_maps_updated_at ON campaign_difficulties;
CREATE TRIGGER trg_campaign_difficulties_updated_at
    BEFORE UPDATE ON campaign_difficulties
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE campaign_difficulties
    ADD COLUMN requirement_type      TEXT
                                     CHECK (requirement_type IN ('ACC', 'AP', 'SCORE', 'STREAK_115', 'FC')),
    ADD COLUMN requirement_value     NUMERIC(20,6),
    ADD COLUMN description           TEXT,
    ADD COLUMN checkpoint_label      TEXT,
    ADD COLUMN checkpoint_avatar_url TEXT,
    ADD COLUMN position_x            INTEGER,
    ADD COLUMN position_y            INTEGER;

UPDATE campaign_difficulties
SET requirement_type  = 'ACC',
    requirement_value = accuracy_requirement
WHERE requirement_type IS NULL;

WITH numbered AS (
    SELECT id, row_number() OVER (PARTITION BY campaign_id ORDER BY created_at, id) AS rn
    FROM campaign_difficulties
)
UPDATE campaign_difficulties c
SET position_x = (n.rn - 1)::int,
    position_y = 0
FROM numbered n
WHERE c.id = n.id;

ALTER TABLE campaign_difficulties
    ALTER COLUMN requirement_type  SET NOT NULL,
    ALTER COLUMN requirement_value SET NOT NULL,
    ALTER COLUMN position_x        SET NOT NULL,
    ALTER COLUMN position_y        SET NOT NULL,
    DROP COLUMN accuracy_requirement,
    ADD CONSTRAINT campaign_difficulties_position_unique UNIQUE (campaign_id, position_x, position_y);

UPDATE campaign_difficulties cd
SET checkpoint_label      = m.title,
    checkpoint_avatar_url = m.avatar_url,
    xp                    = cd.xp + COALESCE(m.xp, 0)
FROM campaign_milestones m
WHERE cd.milestone_for_id = m.id AND m.active = true;

CREATE TABLE campaign_difficulty_items (
    campaign_difficulty_id UUID        NOT NULL REFERENCES campaign_difficulties(id),
    item_id                UUID        NOT NULL REFERENCES items(id),
    quantity               INTEGER     NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (campaign_difficulty_id, item_id)
);

CREATE INDEX idx_campaign_difficulty_items_item ON campaign_difficulty_items(item_id);

INSERT INTO campaign_difficulty_items (campaign_difficulty_id, item_id, quantity)
SELECT cd.id, m.awards_item_id, 1
FROM campaign_difficulties cd
JOIN campaign_milestones m ON cd.milestone_for_id = m.id
WHERE m.awards_item_id IS NOT NULL AND m.active = true
ON CONFLICT DO NOTHING;

ALTER TABLE campaign_difficulties DROP COLUMN milestone_for_id;
DROP TABLE campaign_milestones;

ALTER TABLE campaign_map_paths RENAME TO campaign_difficulty_paths;
ALTER TABLE campaign_difficulty_paths RENAME COLUMN campaign_map_id            TO campaign_difficulty_id;
ALTER TABLE campaign_difficulty_paths RENAME COLUMN comes_from_campaign_map_id TO comes_from_campaign_difficulty_id;

ALTER INDEX idx_campaign_map_paths_map  RENAME TO idx_campaign_difficulty_paths_difficulty;
ALTER INDEX idx_campaign_map_paths_from RENAME TO idx_campaign_difficulty_paths_from;

DROP TRIGGER IF EXISTS trg_campaign_map_paths_updated_at ON campaign_difficulty_paths;
CREATE TRIGGER trg_campaign_difficulty_paths_updated_at
    BEFORE UPDATE ON campaign_difficulty_paths
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

ALTER TABLE user_campaign_scores RENAME COLUMN campaign_map_id TO campaign_difficulty_id;
ALTER INDEX one_active_campaign_score_per_user_map RENAME TO one_active_campaign_score_per_user_difficulty;

CREATE TABLE campaign_completion_items (
    campaign_id UUID        NOT NULL REFERENCES campaigns(id),
    item_id     UUID        NOT NULL REFERENCES items(id),
    quantity    INTEGER     NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (campaign_id, item_id)
);

CREATE INDEX idx_campaign_completion_items_item ON campaign_completion_items(item_id);

CREATE TABLE user_campaigns (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    campaign_id  UUID        NOT NULL REFERENCES campaigns(id),
    status       TEXT        NOT NULL DEFAULT 'in_progress'
                             CHECK (status IN ('in_progress', 'completed', 'abandoned')),
    started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    active       BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_user_campaigns_updated_at
    BEFORE UPDATE ON user_campaigns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE UNIQUE INDEX one_active_user_campaign
    ON user_campaigns(user_id, campaign_id) WHERE active = true;

CREATE INDEX idx_user_campaigns_user_in_progress
    ON user_campaigns(user_id) WHERE active = true AND status = 'in_progress';

CREATE INDEX idx_user_campaigns_campaign ON user_campaigns(campaign_id);

CREATE TABLE campaign_tags (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    kind        TEXT        NOT NULL CHECK (kind IN ('category', 'difficulty', 'theme', 'genre')),
    name        TEXT        NOT NULL,
    category_id UUID        REFERENCES categories(id),
    system      BOOLEAN     NOT NULL DEFAULT false,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_campaign_tags_updated_at
    BEFORE UPDATE ON campaign_tags
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE UNIQUE INDEX uq_campaign_tags_kind_name
    ON campaign_tags(kind, LOWER(name)) WHERE active = true;

CREATE INDEX idx_campaign_tags_kind     ON campaign_tags(kind);
CREATE INDEX idx_campaign_tags_category ON campaign_tags(category_id) WHERE category_id IS NOT NULL;

CREATE TABLE campaign_tag_links (
    campaign_id     UUID        NOT NULL REFERENCES campaigns(id),
    campaign_tag_id UUID        NOT NULL REFERENCES campaign_tags(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (campaign_id, campaign_tag_id)
);

CREATE INDEX idx_campaign_tag_links_tag ON campaign_tag_links(campaign_tag_id);

INSERT INTO campaign_tags (kind, name, category_id, system)
SELECT 'category', c.name, c.id, true
FROM categories c
WHERE c.active = true AND c.code <> 'low_mid';

INSERT INTO campaign_tags (kind, name, system) VALUES
    ('difficulty', 'Beginner',     true),
    ('difficulty', 'Apprentice',   true),
    ('difficulty', 'Intermediate', true),
    ('difficulty', 'Advanced',     true),
    ('difficulty', 'Expert',       true),
    ('difficulty', 'Impossible',   true),
    ('difficulty', 'Progressive',  true);

INSERT INTO campaign_tags (kind, name, system) VALUES
    ('theme', 'Streak Challenge',  true),
    ('theme', 'Tech Showcase',     true),
    ('theme', 'Endurance',         true),
    ('theme', 'Beginner-Friendly', true),
    ('theme', 'Community Pick',    true),
    ('theme', 'Themed Event',      true);

INSERT INTO campaign_tags (kind, name, system) VALUES
    ('genre', 'Anime',      true),
    ('genre', 'Metal',      true),
    ('genre', 'Electronic', true),
    ('genre', 'K-Pop',      true),
    ('genre', 'Rock',       true),
    ('genre', 'Pop',        true),
    ('genre', 'Hip-Hop',    true),
    ('genre', 'Video Game', true),
    ('genre', 'J-Pop',      true);

ALTER TABLE staff_users DROP CONSTRAINT staff_users_role_check;
ALTER TABLE staff_users ADD CONSTRAINT staff_users_role_check
    CHECK (role IN ('moderator', 'ranking', 'ranking_head', 'developer', 'campaign_curator', 'admin'));

ALTER TABLE user_item_links DROP CONSTRAINT user_item_links_source_check;
ALTER TABLE user_item_links ADD CONSTRAINT user_item_links_source_check
    CHECK (source IN ('milestone', 'milestone_set', 'campaign_milestone', 'campaign_difficulty', 'campaign_completion', 'level', 'trade', 'manual', 'crate_drop', 'supporter_tier'));
