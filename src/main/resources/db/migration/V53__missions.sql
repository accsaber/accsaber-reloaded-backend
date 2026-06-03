ALTER TABLE items
    ADD COLUMN mission_poolable BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_items_mission_poolable
    ON items(mission_poolable) WHERE mission_poolable = true;

ALTER TABLE users
    ADD COLUMN mission_xp NUMERIC(20,6) NOT NULL DEFAULT 0;

INSERT INTO curves (id, name, type, scale, shift)
VALUES ('acc00000-0000-0000-0000-000000000010',
        'Mission XP Daily', 'POINT_LOOKUP', 1, 0);

INSERT INTO curves (id, name, type, scale, shift)
VALUES ('acc00000-0000-0000-0000-000000000011',
        'Mission XP Weekly', 'POINT_LOOKUP', 1, 0);

INSERT INTO curve_points (curve_id, x, y) VALUES
    ('acc00000-0000-0000-0000-000000000010', 0,    20),
    ('acc00000-0000-0000-0000-000000000010', 100,  40),
    ('acc00000-0000-0000-0000-000000000010', 250,  65),
    ('acc00000-0000-0000-0000-000000000010', 400,  85),
    ('acc00000-0000-0000-0000-000000000010', 600,  155),
    ('acc00000-0000-0000-0000-000000000010', 700,  185),
    ('acc00000-0000-0000-0000-000000000010', 800,  220),
    ('acc00000-0000-0000-0000-000000000010', 900,  250),
    ('acc00000-0000-0000-0000-000000000010', 1000, 285),
    ('acc00000-0000-0000-0000-000000000010', 1100, 305),
    ('acc00000-0000-0000-0000-000000000010', 1200, 315);

INSERT INTO curve_points (curve_id, x, y) VALUES
    ('acc00000-0000-0000-0000-000000000011', 0,    85),
    ('acc00000-0000-0000-0000-000000000011', 100,  170),
    ('acc00000-0000-0000-0000-000000000011', 250,  320),
    ('acc00000-0000-0000-0000-000000000011', 400,  450),
    ('acc00000-0000-0000-0000-000000000011', 600,  620),
    ('acc00000-0000-0000-0000-000000000011', 800,  760),
    ('acc00000-0000-0000-0000-000000000011', 1000, 870),
    ('acc00000-0000-0000-0000-000000000011', 1100, 930),
    ('acc00000-0000-0000-0000-000000000011', 1200, 970);

CREATE TABLE mission_templates (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    code               TEXT         NOT NULL UNIQUE,
    name               TEXT         NOT NULL,
    description        TEXT         NOT NULL,
    type               TEXT         NOT NULL,
    pool               TEXT         NOT NULL,
    weight             INTEGER      NOT NULL DEFAULT 100 CHECK (weight > 0),
    guaranteed_doable  BOOLEAN      NOT NULL DEFAULT false,
    xp_curve_id        UUID         REFERENCES curves(id),
    xp_multiplier      NUMERIC(8,4) NOT NULL DEFAULT 1.0,
    band_easy          NUMERIC(8,4) NOT NULL DEFAULT 0.92,
    band_medium        NUMERIC(8,4) NOT NULL DEFAULT 1.00,
    band_hard          NUMERIC(8,4) NOT NULL DEFAULT 1.08,
    target_count_min   INTEGER,
    target_count_max   INTEGER,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mission_template_type CHECK (type IN (
        'PLAY_N_MAPS', 'XP_IN_WINDOW',
        'ACC_ON_MAP', 'AP_ON_MAP',
        'PB_SPECIFIC_MAP', 'PB_ABOVE_THRESHOLD',
        'SNIPE_PLAYER_ON_MAP'
    )),
    CONSTRAINT chk_mission_template_pool CHECK (pool IN ('daily', 'weekly', 'event'))
);

CREATE INDEX idx_mission_templates_pool_active
    ON mission_templates(pool, active) WHERE active = true;

CREATE TRIGGER trg_mission_templates_updated_at
    BEFORE UPDATE ON mission_templates
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE user_missions (
    id                       UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id                  BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    template_id              UUID         NOT NULL REFERENCES mission_templates(id) ON DELETE RESTRICT,
    pool                     TEXT         NOT NULL,
    category_id              UUID         REFERENCES categories(id) ON DELETE SET NULL,

    target_map_difficulty_id UUID         REFERENCES map_difficulties(id) ON DELETE SET NULL,
    target_player_id         BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    target_acc               NUMERIC(20,10),
    target_ap                NUMERIC(20,6),
    target_score             INTEGER,
    target_count             INTEGER,
    target_xp                INTEGER,
    target_threshold_ap      NUMERIC(20,6),

    snipe_distance           NUMERIC(20,6),
    progress_count           INTEGER      NOT NULL DEFAULT 0,

    xp_reward                INTEGER      NOT NULL DEFAULT 0,
    crate_reward_id          UUID         REFERENCES items(id) ON DELETE SET NULL,
    crate_awarded            BOOLEAN      NOT NULL DEFAULT false,

    status                   TEXT         NOT NULL DEFAULT 'active',
    assigned_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at               TIMESTAMPTZ  NOT NULL,
    completed_at             TIMESTAMPTZ,

    CONSTRAINT chk_user_missions_pool   CHECK (pool   IN ('daily', 'weekly', 'event')),
    CONSTRAINT chk_user_missions_status CHECK (status IN ('active', 'completed', 'expired', 'voided'))
);

CREATE INDEX idx_user_missions_active
    ON user_missions(user_id, pool, status) WHERE status = 'active';
CREATE INDEX idx_user_missions_expires
    ON user_missions(expires_at) WHERE status = 'active';
CREATE INDEX idx_user_missions_user_completed
    ON user_missions(user_id, completed_at DESC) WHERE status = 'completed';
CREATE INDEX idx_user_missions_target_player
    ON user_missions(target_player_id) WHERE target_player_id IS NOT NULL;
CREATE INDEX idx_user_missions_target_map
    ON user_missions(target_map_difficulty_id) WHERE target_map_difficulty_id IS NOT NULL;

INSERT INTO mission_templates (
    code, name, description, type, pool, weight, guaranteed_doable,
    xp_curve_id, xp_multiplier, band_easy, band_medium, band_hard,
    target_count_min, target_count_max
) VALUES
    ('daily_play_n',     'Daily Player',
     'Play {count} ranked maps.',
     'PLAY_N_MAPS', 'daily', 100, true,
     'acc00000-0000-0000-0000-000000000010', 1.00, 0.92, 1.00, 1.08, 3, 12),

    ('daily_xp_window',  'Daily XP Goal',
     'Earn {xp} XP from any source today.',
     'XP_IN_WINDOW', 'daily', 80, true,
     'acc00000-0000-0000-0000-000000000010', 1.00, 0.80, 1.20, 1.60, NULL, NULL),

    ('daily_acc_on_map', 'Sharpen Your Acc',
     'Hit {acc} accuracy on {map}.',
     'ACC_ON_MAP', 'daily', 100, false,
     'acc00000-0000-0000-0000-000000000010', 1.10, 0.92, 1.00, 1.08, NULL, NULL),

    ('daily_ap_on_map',  'Earn the AP',
     'Score {ap} AP on {map}.',
     'AP_ON_MAP', 'daily', 100, false,
     'acc00000-0000-0000-0000-000000000010', 1.10, 0.92, 1.00, 1.08, NULL, NULL),

    ('daily_pb_specific','Beat Your Best',
     'Set a personal best on {map}.',
     'PB_SPECIFIC_MAP', 'daily', 80, false,
     'acc00000-0000-0000-0000-000000000010', 1.15, 0.95, 1.02, 1.10, NULL, NULL),

    ('daily_pb_above',   'Push the Top',
     'Get a PB on any map where you already have a {threshold} AP play.',
     'PB_ABOVE_THRESHOLD', 'daily', 60, false,
     'acc00000-0000-0000-0000-000000000010', 1.20, 0.90, 1.00, 1.15, 1, 1),

    ('daily_snipe',      'Take Their Score',
     'Beat {player}''s score on {map}.',
     'SNIPE_PLAYER_ON_MAP', 'daily', 70, false,
     'acc00000-0000-0000-0000-000000000010', 1.20, 0.90, 1.05, 1.20, NULL, NULL),

    ('weekly_play_n',    'Weekly Grinder',
     'Play {count} ranked maps in {category} this week.',
     'PLAY_N_MAPS', 'weekly', 100, false,
     'acc00000-0000-0000-0000-000000000011', 1.00, 0.92, 1.00, 1.10, 15, 60),

    ('weekly_ap_on_map', 'Weekly Spotlight',
     'Score {ap} AP on {map}.',
     'AP_ON_MAP', 'weekly', 100, false,
     'acc00000-0000-0000-0000-000000000011', 1.15, 0.92, 1.05, 1.15, NULL, NULL),

    ('weekly_pb_above',  'Stretch Your Top',
     'Get {count} PBs on maps where you already have a {threshold} AP play.',
     'PB_ABOVE_THRESHOLD', 'weekly', 80, false,
     'acc00000-0000-0000-0000-000000000011', 1.20, 0.95, 1.05, 1.20, 2, 4),

    ('weekly_snipe',     'Weekly Snipe',
     'Beat {player}''s score on {map}.',
     'SNIPE_PLAYER_ON_MAP', 'weekly', 80, false,
     'acc00000-0000-0000-0000-000000000011', 1.25, 0.95, 1.10, 1.25, NULL, NULL);
