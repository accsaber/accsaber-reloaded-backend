-- ---------------------------------------------------------------------------
-- Utility: updated_at trigger function
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- curves
-- ---------------------------------------------------------------------------
CREATE TABLE curves (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               TEXT         NOT NULL UNIQUE,
    type               TEXT         NOT NULL DEFAULT 'FORMULA'
                                    CHECK (type IN ('FORMULA', 'POINT_LOOKUP')),
    formula            TEXT,
    x_parameter_name   TEXT,
    x_parameter_value  NUMERIC,
    y_parameter_name   TEXT,
    y_parameter_value  NUMERIC,
    z_parameter_name   TEXT,
    z_parameter_value  NUMERIC,
    scale              NUMERIC,
    shift              NUMERIC,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_curves_updated_at
    BEFORE UPDATE ON curves
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ---------------------------------------------------------------------------
-- curve_points
-- ---------------------------------------------------------------------------
CREATE TABLE curve_points (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    curve_id   UUID        NOT NULL REFERENCES curves(id),
    x          NUMERIC     NOT NULL,
    y          NUMERIC     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (curve_id, x)
);

CREATE TRIGGER trg_curve_points_updated_at
    BEFORE UPDATE ON curve_points
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_curve_points_lookup ON curve_points(curve_id, x);

-- ---------------------------------------------------------------------------
-- categories
-- ---------------------------------------------------------------------------
CREATE TABLE categories (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code              TEXT        NOT NULL,
    name              TEXT        NOT NULL,
    description       TEXT,
    score_curve_id    UUID        REFERENCES curves(id),
    weight_curve_id   UUID        REFERENCES curves(id),
    count_for_overall BOOLEAN     NOT NULL DEFAULT false,
    active            BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE UNIQUE INDEX unique_category_code_active ON categories(code) WHERE active = true;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id         BIGINT      PRIMARY KEY,
    name       TEXT        NOT NULL,
    avatar_url TEXT,
    country    TEXT,
    total_xp   NUMERIC     NOT NULL DEFAULT 0,
    active     BOOLEAN     NOT NULL DEFAULT true,
    banned     BOOLEAN     NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_users_active     ON users(id)        WHERE active = true;
CREATE INDEX idx_users_country    ON users(country)   WHERE active = true;
CREATE INDEX idx_users_created_at ON users(created_at DESC);

-- ---------------------------------------------------------------------------
-- staff_users
-- ---------------------------------------------------------------------------
CREATE TABLE staff_users (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          BIGINT      REFERENCES users(id),
    username         TEXT,
    email            TEXT,
    password         TEXT        NOT NULL,
    role             TEXT        NOT NULL CHECK (role IN ('ranking', 'ranking_head', 'admin')),
    status           TEXT        NOT NULL DEFAULT 'accepted'
                                 CHECK (status IN ('requested', 'accepted', 'denied')),
    refresh_token    TEXT,
    token_expires_at TIMESTAMPTZ,
    active           BOOLEAN     NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT staff_users_identifier_required CHECK (username IS NOT NULL OR email IS NOT NULL)
);

CREATE TRIGGER trg_staff_users_updated_at
    BEFORE UPDATE ON staff_users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX        idx_staff_users_user               ON staff_users(user_id)  WHERE user_id IS NOT NULL;
CREATE INDEX        idx_staff_users_role               ON staff_users(role)     WHERE active = true;
CREATE INDEX        idx_staff_users_status             ON staff_users(status)   WHERE active = true;
CREATE UNIQUE INDEX unique_staff_users_username_active ON staff_users(username) WHERE active = true AND username IS NOT NULL;
CREATE UNIQUE INDEX unique_staff_users_email_active    ON staff_users(email)    WHERE active = true AND email IS NOT NULL;

-- ---------------------------------------------------------------------------
-- staff_oauth_links
-- ---------------------------------------------------------------------------
CREATE TABLE staff_oauth_links (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_user_id       UUID         NOT NULL REFERENCES staff_users(id),
    provider            VARCHAR(50)  NOT NULL,
    provider_user_id    VARCHAR(255) NOT NULL,
    provider_username   VARCHAR(255),
    provider_avatar_url TEXT,
    linked_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    linked_by_staff_id  UUID         NOT NULL REFERENCES staff_users(id),
    CONSTRAINT unique_provider_user  UNIQUE (provider, provider_user_id),
    CONSTRAINT unique_staff_provider UNIQUE (staff_user_id, provider)
);

CREATE INDEX idx_staff_oauth_links_staff_user_id ON staff_oauth_links(staff_user_id);
CREATE INDEX idx_staff_oauth_links_provider_user ON staff_oauth_links(provider, provider_user_id);

-- ---------------------------------------------------------------------------
-- maps
-- ---------------------------------------------------------------------------
CREATE TABLE maps (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    song_name     TEXT        NOT NULL,
    song_author   TEXT        NOT NULL,
    song_hash     TEXT        NOT NULL,
    map_author    TEXT        NOT NULL,
    beatsaver_code TEXT,
    cover_url     TEXT,
    active        BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_maps_updated_at
    BEFORE UPDATE ON maps
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_maps_song_hash ON maps(song_hash);

-- ---------------------------------------------------------------------------
-- batches
-- ---------------------------------------------------------------------------
CREATE TABLE batches (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    description TEXT,
    status      TEXT        NOT NULL DEFAULT 'draft'
                            CHECK (status IN ('draft', 'release_ready', 'released')),
    created_by  UUID        REFERENCES staff_users(id),
    released_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_batches_updated_at
    BEFORE UPDATE ON batches
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_batches_status ON batches(status);

-- ---------------------------------------------------------------------------
-- map_difficulties
-- ---------------------------------------------------------------------------
CREATE TABLE map_difficulties (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    map_id              UUID        NOT NULL REFERENCES maps(id),
    category_id         UUID        NOT NULL REFERENCES categories(id),
    difficulty          TEXT        NOT NULL,
    characteristic      TEXT        NOT NULL,
    previous_version_id UUID        REFERENCES map_difficulties(id),
    ss_leaderboard_id   TEXT,
    bl_leaderboard_id   TEXT,
    status              TEXT        NOT NULL CHECK (status IN ('queue', 'qualified', 'ranked')),
    criteria_status     TEXT        NOT NULL DEFAULT 'pending'
                                    CHECK (criteria_status IN ('pending', 'passed', 'failed')),
    max_score           INTEGER,
    batch_id            UUID        REFERENCES batches(id),
    ranked_at           TIMESTAMPTZ,
    last_updated_by     UUID        REFERENCES staff_users(id),
    active              BOOLEAN     NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_map_difficulties_updated_at
    BEFORE UPDATE ON map_difficulties
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_map_difficulties_map      ON map_difficulties(map_id)            WHERE active = true;
CREATE INDEX idx_map_difficulties_category ON map_difficulties(category_id)       WHERE active = true;
CREATE INDEX idx_map_difficulties_ss       ON map_difficulties(ss_leaderboard_id) WHERE active = true;
CREATE INDEX idx_map_difficulties_bl       ON map_difficulties(bl_leaderboard_id) WHERE active = true;
CREATE INDEX idx_map_difficulties_batch    ON map_difficulties(batch_id)          WHERE batch_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- map_difficulty_complexities  (versioned)
-- ---------------------------------------------------------------------------
CREATE TABLE map_difficulty_complexities (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    map_difficulty_id UUID        NOT NULL REFERENCES map_difficulties(id),
    complexity        NUMERIC     NOT NULL,
    supersedes_id     UUID        REFERENCES map_difficulty_complexities(id),
    supersedes_reason TEXT,
    supersedes_author BIGINT      REFERENCES users(id),
    active            BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_map_difficulty_complexities_updated_at
    BEFORE UPDATE ON map_difficulty_complexities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_map_difficulty_complexities_map_difficulty ON map_difficulty_complexities(map_difficulty_id) WHERE active = true;

CREATE UNIQUE INDEX one_active_complexity_per_map_difficulty
    ON map_difficulty_complexities(map_difficulty_id)
    WHERE active = true;

-- ---------------------------------------------------------------------------
-- map_difficulty_statistics  (versioned)
-- ---------------------------------------------------------------------------
CREATE TABLE map_difficulty_statistics (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    map_difficulty_id UUID        NOT NULL REFERENCES map_difficulties(id),
    max_ap            NUMERIC     NOT NULL,
    min_ap            NUMERIC     NOT NULL,
    average_ap        NUMERIC     NOT NULL,
    total_scores      INTEGER     NOT NULL DEFAULT 0,
    supersedes_id     UUID        REFERENCES map_difficulty_statistics(id),
    supersedes_reason TEXT,
    supersedes_author BIGINT      REFERENCES users(id),
    active            BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_map_difficulty_statistics_updated_at
    BEFORE UPDATE ON map_difficulty_statistics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_map_difficulty_stats_map_difficulty ON map_difficulty_statistics(map_difficulty_id) WHERE active = true;

CREATE UNIQUE INDEX one_active_stat_per_map_difficulty
    ON map_difficulty_statistics(map_difficulty_id)
    WHERE active = true;

-- ---------------------------------------------------------------------------
-- modifiers
-- ---------------------------------------------------------------------------
CREATE TABLE modifiers (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL UNIQUE,
    code       TEXT        NOT NULL UNIQUE,
    multiplier NUMERIC     NOT NULL DEFAULT 1.0,
    active     BOOLEAN     NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_modifiers_updated_at
    BEFORE UPDATE ON modifiers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ---------------------------------------------------------------------------
-- badges
-- ---------------------------------------------------------------------------
CREATE TABLE badges (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL UNIQUE,
    description TEXT,
    image_url   TEXT,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_badges_updated_at
    BEFORE UPDATE ON badges
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ---------------------------------------------------------------------------
-- user_badge_links
-- ---------------------------------------------------------------------------
CREATE TABLE user_badge_links (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    badge_id   UUID        NOT NULL REFERENCES badges(id),
    awarded_by UUID        REFERENCES staff_users(id), -- NULL = system/campaign awarded
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason     TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, badge_id)
);

CREATE TRIGGER trg_user_badge_links_updated_at
    BEFORE UPDATE ON user_badge_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_user_badge_links_user  ON user_badge_links(user_id);
CREATE INDEX idx_user_badge_links_badge ON user_badge_links(badge_id);

-- ---------------------------------------------------------------------------
-- scores  (versioned)
-- ---------------------------------------------------------------------------
CREATE TABLE scores (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             BIGINT      NOT NULL REFERENCES users(id),
    map_difficulty_id   UUID        NOT NULL REFERENCES map_difficulties(id),
    score               INTEGER     NOT NULL,
    score_no_mods       INTEGER     NOT NULL,
    rank                INTEGER     NOT NULL,
    rank_when_set       INTEGER     NOT NULL,
    ap                  NUMERIC     NOT NULL,
    weighted_ap         NUMERIC     NOT NULL,
    bl_score_id         BIGINT,
    max_combo           INTEGER,
    bad_cuts            INTEGER,
    misses              INTEGER,
    wall_hits           INTEGER,
    bomb_hits           INTEGER,
    pauses              INTEGER,
    streak_115          INTEGER,
    play_count          INTEGER,
    hmd                 TEXT,
    time_set            TIMESTAMPTZ,
    reweight_derivative BOOLEAN     DEFAULT false,
    xp_gained           NUMERIC,
    supersedes_id       UUID        REFERENCES scores(id),
    supersedes_reason   TEXT,
    supersedes_author   BIGINT      REFERENCES users(id),
    active              BOOLEAN     NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_scores_updated_at
    BEFORE UPDATE ON scores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_scores_user               ON scores(user_id)                      WHERE active = true;
CREATE INDEX idx_scores_map_difficulty     ON scores(map_difficulty_id)            WHERE active = true;
CREATE INDEX idx_scores_ap_desc            ON scores(ap DESC)                      WHERE active = true;
CREATE INDEX idx_scores_user_map_difficulty ON scores(user_id, map_difficulty_id)  WHERE active = true;
CREATE INDEX idx_scores_created_at         ON scores(created_at DESC);

CREATE UNIQUE INDEX one_active_score_per_user_difficulty
    ON scores(user_id, map_difficulty_id)
    WHERE active = true;

-- ---------------------------------------------------------------------------
-- score_modifier_links
-- ---------------------------------------------------------------------------
CREATE TABLE score_modifier_links (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    score_id    UUID        NOT NULL REFERENCES scores(id) ON DELETE CASCADE,
    modifier_id UUID        NOT NULL REFERENCES modifiers(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (score_id, modifier_id)
);

CREATE TRIGGER trg_score_modifier_links_updated_at
    BEFORE UPDATE ON score_modifier_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_score_modifier_links_score ON score_modifier_links(score_id);

-- ---------------------------------------------------------------------------
-- user_category_statistics  (versioned)
-- ---------------------------------------------------------------------------
CREATE TABLE user_category_statistics (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           BIGINT      NOT NULL REFERENCES users(id),
    category_id       UUID        NOT NULL REFERENCES categories(id),
    ranking           INTEGER,
    country_ranking   INTEGER,
    ap                NUMERIC     NOT NULL DEFAULT 0,
    average_acc       NUMERIC,
    average_ap        NUMERIC,
    ranked_plays      INTEGER     NOT NULL DEFAULT 0,
    top_play_id       UUID        REFERENCES scores(id),
    supersedes_id     UUID        REFERENCES user_category_statistics(id),
    supersedes_reason TEXT,
    supersedes_author BIGINT      REFERENCES users(id),
    active            BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_user_category_statistics_updated_at
    BEFORE UPDATE ON user_category_statistics
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_user_cat_stats_user     ON user_category_statistics(user_id)    WHERE active = true;
CREATE INDEX idx_user_cat_stats_category ON user_category_statistics(category_id) WHERE active = true;
CREATE INDEX idx_user_cat_stats_ranking  ON user_category_statistics(ranking ASC) WHERE active = true;
CREATE INDEX idx_user_cat_stats_ap       ON user_category_statistics(ap DESC)     WHERE active = true;

CREATE UNIQUE INDEX one_active_stat_per_user_category
    ON user_category_statistics(user_id, category_id)
    WHERE active = true;

-- ---------------------------------------------------------------------------
-- staff_map_votes
-- ---------------------------------------------------------------------------
CREATE TABLE staff_map_votes (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    map_difficulty_id       UUID           NOT NULL REFERENCES map_difficulties(id),
    staff_id                UUID           NOT NULL REFERENCES staff_users(id),
    vote                    TEXT           NOT NULL CHECK (vote IN ('upvote', 'downvote', 'neutral')),
    type                    TEXT           NOT NULL CHECK (type IN ('rank', 'reweight', 'unrank')),
    suggested_complexity    NUMERIC(10,6),
    criteria_vote           TEXT           CHECK (criteria_vote IN ('pass', 'fail')),
    criteria_vote_override  BOOLEAN        NOT NULL DEFAULT false,
    reason                  TEXT,
    active                  BOOLEAN        NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_staff_map_votes_updated_at
    BEFORE UPDATE ON staff_map_votes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX        idx_staff_map_votes_map_difficulty ON staff_map_votes(map_difficulty_id);
CREATE INDEX        idx_staff_map_votes_staff          ON staff_map_votes(staff_id);
CREATE UNIQUE INDEX unique_staff_map_votes_active      ON staff_map_votes(map_difficulty_id, staff_id) WHERE active = true;

-- ---------------------------------------------------------------------------
-- milestone_sets
-- ---------------------------------------------------------------------------
CREATE TABLE milestone_sets (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    title        TEXT        NOT NULL,
    description  TEXT,
    set_bonus_xp NUMERIC     NOT NULL DEFAULT 0,
    active       BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_milestone_sets_updated_at
    BEFORE UPDATE ON milestone_sets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ---------------------------------------------------------------------------
-- level_thresholds
-- ---------------------------------------------------------------------------
CREATE TABLE level_thresholds (
    level       INTEGER     PRIMARY KEY,
    xp_required NUMERIC     NOT NULL,
    title       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_level_thresholds_updated_at
    BEFORE UPDATE ON level_thresholds
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ---------------------------------------------------------------------------
-- milestones
-- ---------------------------------------------------------------------------
CREATE TABLE milestones (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    set_id           UUID        NOT NULL REFERENCES milestone_sets(id),
    category_id      UUID        REFERENCES categories(id),
    title            TEXT        NOT NULL,
    description      TEXT,
    type             TEXT        NOT NULL CHECK (type IN ('milestone', 'achievement')),
    tier             TEXT        NOT NULL DEFAULT 'bronze'
                                 CHECK (tier IN ('bronze', 'silver', 'gold', 'platinum', 'diamond')),
    xp               NUMERIC     NOT NULL DEFAULT 0,
    query_spec       JSONB       NOT NULL,
    target_value     NUMERIC     NOT NULL,
    comparison       TEXT        NOT NULL DEFAULT 'GTE' CHECK (comparison IN ('GTE', 'LTE')),
    bl_exclusive     BOOLEAN     NOT NULL DEFAULT false,
    active           BOOLEAN     NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_milestones_updated_at
    BEFORE UPDATE ON milestones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_milestones_set       ON milestones(set_id) WHERE active = true;
CREATE INDEX idx_milestones_category  ON milestones(category_id) WHERE active = true AND category_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- map_difficulty_milestone_links
-- ---------------------------------------------------------------------------
CREATE TABLE map_difficulty_milestone_links (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    map_difficulty_id UUID        NOT NULL REFERENCES map_difficulties(id),
    milestone_id      UUID        NOT NULL REFERENCES milestones(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (map_difficulty_id, milestone_id)
);

CREATE TRIGGER trg_map_difficulty_milestone_links_updated_at
    BEFORE UPDATE ON map_difficulty_milestone_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_mdml_map_difficulty ON map_difficulty_milestone_links(map_difficulty_id);
CREATE INDEX idx_mdml_milestone     ON map_difficulty_milestone_links(milestone_id);

-- ---------------------------------------------------------------------------
-- user_milestone_links
-- ---------------------------------------------------------------------------
CREATE TABLE user_milestone_links (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                BIGINT      NOT NULL REFERENCES users(id),
    milestone_id           UUID        NOT NULL REFERENCES milestones(id),
    achieved_with_score_id UUID        REFERENCES scores(id),
    progress               NUMERIC,
    completed              BOOLEAN     NOT NULL DEFAULT false,
    completed_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, milestone_id)
);

CREATE TRIGGER trg_user_milestone_links_updated_at
    BEFORE UPDATE ON user_milestone_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_user_milestone_links_user      ON user_milestone_links(user_id);
CREATE INDEX idx_user_milestone_links_milestone ON user_milestone_links(milestone_id);
CREATE INDEX idx_user_milestone_links_completed ON user_milestone_links(user_id) WHERE completed = true;

-- ---------------------------------------------------------------------------
-- user_milestone_set_bonuses
-- ---------------------------------------------------------------------------
CREATE TABLE user_milestone_set_bonuses (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          BIGINT      NOT NULL REFERENCES users(id),
    milestone_set_id UUID        NOT NULL REFERENCES milestone_sets(id),
    claimed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, milestone_set_id)
);

CREATE INDEX idx_user_milestone_set_bonuses_user ON user_milestone_set_bonuses(user_id);

-- ---------------------------------------------------------------------------
-- campaigns
-- ---------------------------------------------------------------------------
CREATE TABLE campaigns (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id  BIGINT      NOT NULL REFERENCES users(id),
    name        TEXT        NOT NULL,
    description TEXT,
    difficulty  TEXT,
    verified    BOOLEAN     NOT NULL DEFAULT false,
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_campaigns_updated_at
    BEFORE UPDATE ON campaigns
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_campaigns_creator ON campaigns(creator_id);

-- ---------------------------------------------------------------------------
-- campaign_milestones
-- ---------------------------------------------------------------------------
CREATE TABLE campaign_milestones (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID        NOT NULL REFERENCES campaigns(id),
    title           TEXT        NOT NULL,
    description     TEXT,
    avatar_url      TEXT,
    xp              NUMERIC     NOT NULL DEFAULT 0,
    awards_badge_id UUID        REFERENCES badges(id), -- badge automatically awarded on completion; NULL if no badge
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_campaign_milestones_updated_at
    BEFORE UPDATE ON campaign_milestones
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_campaign_milestones_campaign ON campaign_milestones(campaign_id);

-- ---------------------------------------------------------------------------
-- campaign_maps
-- ---------------------------------------------------------------------------
CREATE TABLE campaign_maps (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id          UUID        NOT NULL REFERENCES campaigns(id),
    map_difficulty_id    UUID        NOT NULL REFERENCES map_difficulties(id),
    milestone_for_id     UUID        REFERENCES campaign_milestones(id),
    accuracy_requirement NUMERIC     NOT NULL,
    xp                   NUMERIC     NOT NULL DEFAULT 0,
    active               BOOLEAN     NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (campaign_id, map_difficulty_id)
);

CREATE TRIGGER trg_campaign_maps_updated_at
    BEFORE UPDATE ON campaign_maps
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_campaign_maps_campaign       ON campaign_maps(campaign_id);
CREATE INDEX idx_campaign_maps_map_difficulty ON campaign_maps(map_difficulty_id);

-- ---------------------------------------------------------------------------
-- campaign_map_paths
-- ---------------------------------------------------------------------------
CREATE TABLE campaign_map_paths (
    id                         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_map_id            UUID        NOT NULL REFERENCES campaign_maps(id),
    comes_from_campaign_map_id UUID        NOT NULL REFERENCES campaign_maps(id),
    active                     BOOLEAN     NOT NULL DEFAULT true,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (campaign_map_id, comes_from_campaign_map_id)
);

CREATE TRIGGER trg_campaign_map_paths_updated_at
    BEFORE UPDATE ON campaign_map_paths
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_campaign_map_paths_map  ON campaign_map_paths(campaign_map_id);
CREATE INDEX idx_campaign_map_paths_from ON campaign_map_paths(comes_from_campaign_map_id);

-- ---------------------------------------------------------------------------
-- user_campaign_scores
-- ---------------------------------------------------------------------------
CREATE TABLE user_campaign_scores (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         BIGINT      NOT NULL REFERENCES users(id),
    campaign_id     UUID        NOT NULL REFERENCES campaigns(id),
    campaign_map_id UUID        NOT NULL REFERENCES campaign_maps(id),
    score_id        UUID        NOT NULL REFERENCES scores(id),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_user_campaign_scores_updated_at
    BEFORE UPDATE ON user_campaign_scores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_user_campaign_scores_user     ON user_campaign_scores(user_id);
CREATE INDEX idx_user_campaign_scores_campaign ON user_campaign_scores(campaign_id);

CREATE UNIQUE INDEX one_active_campaign_score_per_user_map
    ON user_campaign_scores(user_id, campaign_map_id)
    WHERE active = true;

-- ---------------------------------------------------------------------------
-- admin_actions
-- ---------------------------------------------------------------------------
CREATE TABLE admin_actions (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    staff_user_id UUID        NOT NULL REFERENCES staff_users(id),
    action_type   TEXT        NOT NULL,
    target_table  TEXT        NOT NULL,
    target_id     TEXT        NOT NULL,
    reason        TEXT,
    metadata      JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_actions_staff       ON admin_actions(staff_user_id);
CREATE INDEX idx_admin_actions_created_at  ON admin_actions(created_at DESC);
CREATE INDEX idx_admin_actions_action_type ON admin_actions(action_type);
CREATE INDEX idx_admin_actions_target      ON admin_actions(target_table, target_id);

-- ---------------------------------------------------------------------------
-- discord_user_links
-- ---------------------------------------------------------------------------

CREATE TABLE discord_user_links (
    discord_id   TEXT        NOT NULL,
    user_id      BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_discord_user_links       PRIMARY KEY (discord_id),
    CONSTRAINT unique_discord_user          UNIQUE (user_id),
    CONSTRAINT fk_discord_user_links_users  FOREIGN KEY (user_id) REFERENCES users (id)
);


-- =============================================================================
-- SEED DATA
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Curves
-- ---------------------------------------------------------------------------
INSERT INTO curves (id, name, type, scale, shift, formula, x_parameter_name, x_parameter_value, y_parameter_name, y_parameter_value, z_parameter_name, z_parameter_value)
VALUES
    (
        'acc00000-0000-0000-0000-000000000001',
        'AccSaber Score Curve',
        'POINT_LOOKUP',
        61,
        -18,
        NULL, NULL, NULL, NULL, NULL, NULL, NULL
    ),
    (
        'acc00000-0000-0000-0000-000000000002',
        'AccSaber Weight Curve',
        'FORMULA',
        NULL, NULL,
        'LOGISTIC_SIGMOID',
        'k', 0.4,
        'y1', 0.1,
        'x1', 15
    ),
    (
        'acc00000-0000-0000-0000-000000000003',
        'AccSaber XP Curve',
        'POINT_LOOKUP',
        NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
    );
-- XP Curve points are seeded at the bottom of this file alongside level thresholds and milestones.

-- ---------------------------------------------------------------------------
-- Curve Points (AccSaber Score Curve - 57 points)
-- ---------------------------------------------------------------------------
INSERT INTO curve_points (curve_id, x, y) VALUES
    ('acc00000-0000-0000-0000-000000000001', 0.0000000000000000, 0.0000000000000000),
    ('acc00000-0000-0000-0000-000000000001', 0.9349050106584025, 0.1995400780527346),
    ('acc00000-0000-0000-0000-000000000001', 0.9361096414526044, 0.2053726914033911),
    ('acc00000-0000-0000-0000-000000000001', 0.9373168654378441, 0.2112057332052981),
    ('acc00000-0000-0000-0000-000000000001', 0.9385245851320524, 0.2170333579843166),
    ('acc00000-0000-0000-0000-000000000001', 0.9397223493859982, 0.2228095289081693),
    ('acc00000-0000-0000-0000-000000000001', 0.9409324581850277, 0.2286461774619347),
    ('acc00000-0000-0000-0000-000000000001', 0.9421364984358537, 0.2344589535514457),
    ('acc00000-0000-0000-0000-000000000001', 0.9433408588769010, 0.2402832027223785),
    ('acc00000-0000-0000-0000-000000000001', 0.9445528451823000, 0.2461590540114391),
    ('acc00000-0000-0000-0000-000000000001', 0.9457521950057954, 0.2519929536323373),
    ('acc00000-0000-0000-0000-000000000001', 0.9469652757511278, 0.2579181890854542),
    ('acc00000-0000-0000-0000-000000000001', 0.9481613689691947, 0.2637897134830433),
    ('acc00000-0000-0000-0000-000000000001', 0.9493682127874609, 0.2697488203344885),
    ('acc00000-0000-0000-0000-000000000001', 0.9505744372202971, 0.2757454294108473),
    ('acc00000-0000-0000-0000-000000000001', 0.9517783524884541, 0.2817769019690621),
    ('acc00000-0000-0000-0000-000000000001', 0.9529892330175649, 0.2878962698161590),
    ('acc00000-0000-0000-0000-000000000001', 0.9541947185853665, 0.2940478274623703),
    ('acc00000-0000-0000-0000-000000000001', 0.9554044516127758, 0.3002878186971881),
    ('acc00000-0000-0000-0000-000000000001', 0.9566054381494079, 0.3065563770511072),
    ('acc00000-0000-0000-0000-000000000001', 0.9578076986986650, 0.3129131797734578),
    ('acc00000-0000-0000-0000-000000000001', 0.9590221672423604, 0.3194262585871035),
    ('acc00000-0000-0000-0000-000000000001', 0.9602231628864696, 0.3259672058569638),
    ('acc00000-0000-0000-0000-000000000001', 0.9614339985634710, 0.3326729095043305),
    ('acc00000-0000-0000-0000-000000000001', 0.9626279572859802, 0.3394055069640740),
    ('acc00000-0000-0000-0000-000000000001', 0.9638423428271630, 0.3463882776436415),
    ('acc00000-0000-0000-0000-000000000001', 0.9650501030404470, 0.3534815900715788),
    ('acc00000-0000-0000-0000-000000000001', 0.9662496093570300, 0.3606879994900724),
    ('acc00000-0000-0000-0000-000000000001', 0.9674529869368587, 0.3680959084197356),
    ('acc00000-0000-0000-0000-000000000001', 0.9686591348667645, 0.3757183982279203),
    ('acc00000-0000-0000-0000-000000000001', 0.9698668993297000, 0.3835699059837689),
    ('acc00000-0000-0000-0000-000000000001', 0.9710750806787853, 0.3916664197351599),
    ('acc00000-0000-0000-0000-000000000001', 0.9722824425660789, 0.4000257103100814),
    ('acc00000-0000-0000-0000-000000000001', 0.9734877233230004, 0.4086676081620450),
    ('acc00000-0000-0000-0000-000000000001', 0.9746896497445529, 0.4176143361918557),
    ('acc00000-0000-0000-0000-000000000001', 0.9759017934808459, 0.4270083485660384),
    ('acc00000-0000-0000-0000-000000000001', 0.9771082808681040, 0.4367729548697090),
    ('acc00000-0000-0000-0000-000000000001', 0.9783078742661878, 0.4469421675377316),
    ('acc00000-0000-0000-0000-000000000001', 0.9795145289674262, 0.4576932612610576),
    ('acc00000-0000-0000-0000-000000000001', 0.9807121922419519, 0.4689499882135975),
    ('acc00000-0000-0000-0000-000000000001', 0.9819304015430030, 0.4810807109121709),
    ('acc00000-0000-0000-0000-000000000001', 0.9831227248036967, 0.4937134311802671),
    ('acc00000-0000-0000-0000-000000000001', 0.9843344315883069, 0.5074369975183908),
    ('acc00000-0000-0000-0000-000000000001', 0.9855345565106794, 0.5220472890164811),
    ('acc00000-0000-0000-0000-000000000001', 0.9867538435462135, 0.5381018792987169),
    ('acc00000-0000-0000-0000-000000000001', 0.9879462160499057, 0.5551918317559756),
    ('acc00000-0000-0000-0000-000000000001', 0.9891587675835430, 0.5742496799950565),
    ('acc00000-0000-0000-0000-000000000001', 0.9903616429313051, 0.5951727896238259),
    ('acc00000-0000-0000-0000-000000000001', 0.9915723216173138, 0.6187204908473445),
    ('acc00000-0000-0000-0000-000000000001', 0.9927779343719173, 0.6452713618738384),
    ('acc00000-0000-0000-0000-000000000001', 0.9939826353978779, 0.6757582832177143),
    ('acc00000-0000-0000-0000-000000000001', 0.9951928260723995, 0.7116318568448161),
    ('acc00000-0000-0000-0000-000000000001', 0.9963839136271500, 0.7539893920553304),
    ('acc00000-0000-0000-0000-000000000001', 0.9975978174482817, 0.8078649708462118),
    ('acc00000-0000-0000-0000-000000000001', 0.9988016676122579, 0.8810362590039038),
    ('acc00000-0000-0000-0000-000000000001', 0.9997988680153226, 1.0000000000000000),
    ('acc00000-0000-0000-0000-000000000001', 1.0000000000000000, 1.0000000000000000);

-- ---------------------------------------------------------------------------
-- Categories
-- ---------------------------------------------------------------------------
INSERT INTO categories (id, code, name, description, score_curve_id, weight_curve_id, count_for_overall)
VALUES
    (
        'b0000000-0000-0000-0000-000000000001',
        'true_acc', 'True Acc', 'True Acc Category',
        'acc00000-0000-0000-0000-000000000001',
        'acc00000-0000-0000-0000-000000000002',
        true
    ),
    (
        'b0000000-0000-0000-0000-000000000002',
        'standard_acc', 'Standard Acc', 'Standard Acc Category',
        'acc00000-0000-0000-0000-000000000001',
        'acc00000-0000-0000-0000-000000000002',
        true
    ),
    (
        'b0000000-0000-0000-0000-000000000003',
        'tech_acc', 'Tech Acc', 'Tech Acc Category',
        'acc00000-0000-0000-0000-000000000001',
        'acc00000-0000-0000-0000-000000000002',
        true
    ),
    (
        'b0000000-0000-0000-0000-000000000004',
        'low_mid', 'Low Mid', 'Low Mid Category',
        'acc00000-0000-0000-0000-000000000001',
        'acc00000-0000-0000-0000-000000000002',
        true
    ),
    (
        'b0000000-0000-0000-0000-000000000005',
        'overall', 'Overall', 'Aggregate AP across all ranked categories',
        NULL, NULL,
        false
    );

-- ---------------------------------------------------------------------------
-- Modifiers
-- ---------------------------------------------------------------------------
INSERT INTO modifiers (name, code, multiplier) VALUES
    ('No Fail',             'NF', 0.50),
    ('No Obstacles',        'NO', 1.00),
    ('No Bombs',            'NB', 1.00),
    ('Slower Song',         'SS', 1.00),
    ('Faster Song',         'FS', 1.00),
    ('Ghost Notes',         'GN', 1.00),
    ('Disappearing Arrows', 'DA', 1.00),
    ('Pro Mode',            'PM', 1.00),
    ('Small Notes',         'SN', 1.00),
    ('Super Fast Song',     'SF', 1.00);

-- ---------------------------------------------------------------------------
-- XP Curve Points
-- ---------------------------------------------------------------------------
INSERT INTO curve_points (curve_id, x, y) VALUES
    ('acc00000-0000-0000-0000-000000000003', 0.00, 0.000),
    ('acc00000-0000-0000-0000-000000000003', 0.10, 0.001),
    ('acc00000-0000-0000-0000-000000000003', 0.20, 0.003),
    ('acc00000-0000-0000-0000-000000000003', 0.30, 0.006),
    ('acc00000-0000-0000-0000-000000000003', 0.40, 0.010),
    ('acc00000-0000-0000-0000-000000000003', 0.50, 0.018),
    ('acc00000-0000-0000-0000-000000000003', 0.55, 0.025),
    ('acc00000-0000-0000-0000-000000000003', 0.60, 0.032),
    ('acc00000-0000-0000-0000-000000000003', 0.65, 0.042),
    ('acc00000-0000-0000-0000-000000000003', 0.70, 0.055),
    ('acc00000-0000-0000-0000-000000000003', 0.75, 0.072),
    ('acc00000-0000-0000-0000-000000000003', 0.78, 0.088),
    ('acc00000-0000-0000-0000-000000000003', 0.80, 0.100),
    ('acc00000-0000-0000-0000-000000000003', 0.82, 0.115),
    ('acc00000-0000-0000-0000-000000000003', 0.84, 0.133),
    ('acc00000-0000-0000-0000-000000000003', 0.86, 0.155),
    ('acc00000-0000-0000-0000-000000000003', 0.88, 0.180),
    ('acc00000-0000-0000-0000-000000000003', 0.90, 0.180),
    ('acc00000-0000-0000-0000-000000000003', 0.91, 0.210),
    ('acc00000-0000-0000-0000-000000000003', 0.92, 0.245),
    ('acc00000-0000-0000-0000-000000000003', 0.93, 0.285),
    ('acc00000-0000-0000-0000-000000000003', 0.94, 0.330),
    ('acc00000-0000-0000-0000-000000000003', 0.95, 0.380),
    ('acc00000-0000-0000-0000-000000000003', 0.96, 0.440),
    ('acc00000-0000-0000-0000-000000000003', 0.965, 0.480),
    ('acc00000-0000-0000-0000-000000000003', 0.97, 0.520),
    ('acc00000-0000-0000-0000-000000000003', 0.975, 0.570),
    ('acc00000-0000-0000-0000-000000000003', 0.98, 0.620),
    ('acc00000-0000-0000-0000-000000000003', 0.985, 0.680),
    ('acc00000-0000-0000-0000-000000000003', 0.99, 0.750),
    ('acc00000-0000-0000-0000-000000000003', 0.993, 0.810),
    ('acc00000-0000-0000-0000-000000000003', 0.995, 0.860),
    ('acc00000-0000-0000-0000-000000000003', 0.997, 0.920),
    ('acc00000-0000-0000-0000-000000000003', 0.999, 0.970),
    ('acc00000-0000-0000-0000-000000000003', 1.00, 1.000);

-- ---------------------------------------------------------------------------
-- Level Thresholds (named titles at milestone levels; levels are infinite via formula)
-- ---------------------------------------------------------------------------
INSERT INTO level_thresholds (level, xp_required, title) VALUES
    (1,   30,      'Newcomer'),
    (5,   544,     'Apprentice'),
    (10,  2100,    'Adept'),
    (15,  4800,    'Skilled'),
    (20,  8800,    'Expert'),
    (25,  14200,   'Master'),
    (30,  21100,   'Grandmaster'),
    (40,  40000,   'Legend'),
    (50,  51000,   'Transcendent'),
    (75,  140000,  'Mythic'),
    (100, 278000,  'Ascendant');

-- ---------------------------------------------------------------------------
-- Materialized View: milestone completion stats
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW milestone_completion_stats AS
SELECT
    m.id AS milestone_id,
    COUNT(uml.id) FILTER (WHERE uml.completed = true) AS completions,
    (SELECT COUNT(*) FROM users WHERE active = true AND banned = false) AS total_players,
    CASE WHEN (SELECT COUNT(*) FROM users WHERE active = true AND banned = false) = 0 THEN 0
         ELSE ROUND(COUNT(uml.id) FILTER (WHERE uml.completed = true) * 100.0 /
              (SELECT COUNT(*) FROM users WHERE active = true AND banned = false), 2)
    END AS completion_percentage
FROM milestones m
LEFT JOIN user_milestone_links uml ON uml.milestone_id = m.id
WHERE m.active = true
GROUP BY m.id;

CREATE UNIQUE INDEX idx_milestone_completion_stats_id ON milestone_completion_stats(milestone_id);
