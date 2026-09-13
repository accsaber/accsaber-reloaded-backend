CREATE TABLE clans (
    id                  UUID             PRIMARY KEY DEFAULT uuidv7(),
    name                TEXT             NOT NULL,
    tag                 TEXT             NOT NULL,
    slug                TEXT             NOT NULL,
    description         TEXT,
    accepting_requests  BOOLEAN          NOT NULL DEFAULT TRUE,
    total_xp            DOUBLE PRECISION NOT NULL DEFAULT 0,
    roster_strength     DOUBLE PRECISION NOT NULL DEFAULT 0,
    ally_strength       DOUBLE PRECISION NOT NULL DEFAULT 0,
    active              BOOLEAN          NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clans_tag CHECK (tag ~ '^[A-Z0-9]{2,5}$'),
    CONSTRAINT chk_clans_totals CHECK (total_xp >= 0 AND roster_strength >= 0 AND ally_strength >= 0)
);

CREATE UNIQUE INDEX uq_clans_active_name ON clans (search_normalize(name)) WHERE active;
CREATE UNIQUE INDEX uq_clans_active_tag ON clans (tag) WHERE active;
CREATE UNIQUE INDEX uq_clans_active_slug ON clans (slug) WHERE active;
CREATE INDEX idx_clans_name_trgm ON clans USING gin (search_normalize(name) gin_trgm_ops);
CREATE INDEX idx_clans_tag_trgm ON clans USING gin (tag gin_trgm_ops);

CREATE TABLE clan_members (
    id            UUID        PRIMARY KEY DEFAULT uuidv7(),
    clan_id       UUID        NOT NULL REFERENCES clans(id),
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    role          TEXT        NOT NULL DEFAULT 'member',
    joined_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    left_at       TIMESTAMPTZ,
    leave_reason  TEXT,
    CONSTRAINT chk_clan_members_role CHECK (role IN ('member', 'officer', 'commander', 'founder')),
    CONSTRAINT chk_clan_members_leave_reason
        CHECK (leave_reason IN ('left', 'kicked', 'disbanded', 'banned', 'merged')),
    CONSTRAINT chk_clan_members_left CHECK ((left_at IS NULL) = (leave_reason IS NULL))
);

CREATE UNIQUE INDEX uq_clan_members_open_user ON clan_members (user_id) WHERE left_at IS NULL;
CREATE UNIQUE INDEX uq_clan_members_open_founder ON clan_members (clan_id)
    WHERE role = 'founder' AND left_at IS NULL;
CREATE INDEX idx_clan_members_open_clan ON clan_members (clan_id) WHERE left_at IS NULL;
CREATE INDEX idx_clan_members_user_joined ON clan_members (user_id, joined_at DESC);

CREATE TABLE clan_join_requests (
    id           UUID        PRIMARY KEY DEFAULT uuidv7(),
    clan_id      UUID        NOT NULL REFERENCES clans(id),
    user_id      BIGINT      NOT NULL REFERENCES users(id),
    direction    TEXT        NOT NULL,
    status       TEXT        NOT NULL DEFAULT 'pending',
    created_by   BIGINT      NOT NULL REFERENCES users(id),
    resolved_by  BIGINT      REFERENCES users(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at  TIMESTAMPTZ,
    CONSTRAINT chk_clan_join_requests_direction CHECK (direction IN ('invite', 'request')),
    CONSTRAINT chk_clan_join_requests_status
        CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled', 'expired')),
    CONSTRAINT chk_clan_join_requests_resolution CHECK ((status = 'pending') = (resolved_at IS NULL))
);

CREATE UNIQUE INDEX uq_clan_join_requests_pending ON clan_join_requests (clan_id, user_id) WHERE status = 'pending';
CREATE INDEX idx_clan_join_requests_user_pending ON clan_join_requests (user_id) WHERE status = 'pending';

CREATE TABLE clan_audit_log (
    id              UUID        PRIMARY KEY DEFAULT uuidv7(),
    clan_id         UUID        NOT NULL REFERENCES clans(id),
    actor_id        BIGINT      REFERENCES users(id),
    action          TEXT        NOT NULL,
    target_user_id  BIGINT      REFERENCES users(id),
    details         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clan_audit_log_action CHECK (action IN ('profile_updated', 'cosmetic_equipped', 'role_changed',
        'founder_transferred', 'founder_claimed', 'member_kicked', 'disbanded'))
);

CREATE INDEX idx_clan_audit_log_clan ON clan_audit_log (clan_id, created_at DESC);

CREATE TABLE clan_xp_grants (
    id             UUID             PRIMARY KEY DEFAULT uuidv7(),
    clan_id        UUID             NOT NULL REFERENCES clans(id),
    source         TEXT             NOT NULL,
    source_id      TEXT             NOT NULL,
    raw_amount     DOUBLE PRECISION NOT NULL,
    roster_factor  DOUBLE PRECISION NOT NULL,
    amount         DOUBLE PRECISION NOT NULL,
    created_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clan_xp_grants_source
        CHECK (source IN ('daily_play', 'mission', 'war_break', 'war_win', 'war_loan')),
    CONSTRAINT chk_clan_xp_grants_amounts CHECK (raw_amount >= 0 AND roster_factor > 0 AND amount >= 0),
    CONSTRAINT uq_clan_xp_grants_source UNIQUE (clan_id, source, source_id)
);

CREATE INDEX idx_clan_xp_grants_clan ON clan_xp_grants (clan_id, created_at DESC);

CREATE TABLE clan_level_capacities (
    level     INTEGER NOT NULL,
    capacity  TEXT    NOT NULL,
    amount    INTEGER NOT NULL,
    PRIMARY KEY (level, capacity),
    CONSTRAINT chk_clan_level_capacities_level CHECK (level >= 0),
    CONSTRAINT chk_clan_level_capacities_capacity CHECK (capacity IN ('member_slots', 'mission_slots', 'ally_slots',
        'lend_slots', 'receive_slots', 'officer_slots', 'commander_slots')),
    CONSTRAINT chk_clan_level_capacities_amount CHECK (amount > 0)
);

CREATE TABLE clan_level_war_modes (
    axis   TEXT    NOT NULL,
    mode   TEXT    NOT NULL,
    level  INTEGER NOT NULL,
    PRIMARY KEY (axis, mode),
    CONSTRAINT chk_clan_level_war_modes_level CHECK (level >= 0),
    CONSTRAINT chk_clan_level_war_modes_mode CHECK (
        (axis = 'arena' AND mode IN ('mixed', 'random', 'category_turf', 'complexity_turf'))
        OR (axis = 'ruleset' AND mode IN ('duel', 'berserker')))
);

CREATE TABLE clan_level_items (
    item_id  UUID    PRIMARY KEY REFERENCES items(id),
    level    INTEGER NOT NULL,
    CONSTRAINT chk_clan_level_items_level CHECK (level >= 0)
);

CREATE INDEX idx_clan_level_items_level ON clan_level_items (level);

CREATE TABLE clan_items (
    clan_id      UUID        NOT NULL REFERENCES clans(id),
    item_id      UUID        NOT NULL REFERENCES items(id),
    source       TEXT        NOT NULL,
    source_id    TEXT,
    acquired_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (clan_id, item_id),
    CONSTRAINT chk_clan_items_source CHECK (source IN ('level', 'season', 'war', 'manual'))
);

ALTER TABLE items ADD CONSTRAINT uq_items_id_type UNIQUE (id, type_id);

CREATE TABLE clan_equipped_items (
    clan_id       UUID NOT NULL REFERENCES clans(id),
    item_type_id  UUID NOT NULL REFERENCES item_types(id),
    item_id       UUID NOT NULL,
    PRIMARY KEY (clan_id, item_type_id),
    CONSTRAINT fk_clan_equipped_items_owned FOREIGN KEY (clan_id, item_id) REFERENCES clan_items (clan_id, item_id),
    CONSTRAINT fk_clan_equipped_items_type FOREIGN KEY (item_id, item_type_id) REFERENCES items (id, type_id)
);

CREATE TABLE clan_seasons (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    name        TEXT        NOT NULL,
    slug        TEXT        NOT NULL UNIQUE,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL,
    closed_at   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clan_seasons_window CHECK (ends_at > starts_at),
    CONSTRAINT excl_clan_seasons_overlap EXCLUDE USING gist (tstzrange(starts_at, ends_at) WITH &&)
);

CREATE TABLE clan_standing_events (
    id          UUID             PRIMARY KEY DEFAULT uuidv7(),
    season_id   UUID             NOT NULL REFERENCES clan_seasons(id),
    clan_id     UUID             NOT NULL REFERENCES clans(id),
    amount      DOUBLE PRECISION NOT NULL,
    source      TEXT             NOT NULL,
    source_id   TEXT             NOT NULL,
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clan_standing_events_source CHECK (source IN ('war_break', 'mission')),
    CONSTRAINT uq_clan_standing_events_source UNIQUE (season_id, clan_id, source, source_id)
);

CREATE INDEX idx_clan_standing_events_clan ON clan_standing_events (season_id, clan_id, created_at DESC);

CREATE TABLE clan_season_standings (
    season_id   UUID             NOT NULL REFERENCES clan_seasons(id),
    clan_id     UUID             NOT NULL REFERENCES clans(id),
    earned      DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    PRIMARY KEY (season_id, clan_id),
    CONSTRAINT chk_clan_season_standings_earned CHECK (earned >= 0)
);

CREATE TABLE clan_season_results (
    season_id      UUID             NOT NULL REFERENCES clan_seasons(id),
    clan_id        UUID             NOT NULL REFERENCES clans(id),
    rank           INTEGER          NOT NULL,
    base_standing  DOUBLE PRECISION NOT NULL,
    earned         DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (season_id, clan_id),
    CONSTRAINT chk_clan_season_results_rank CHECK (rank > 0),
    CONSTRAINT uq_clan_season_results_rank UNIQUE (season_id, rank)
);

CREATE TABLE clan_season_rewards (
    id         UUID    PRIMARY KEY DEFAULT uuidv7(),
    season_id  UUID    NOT NULL REFERENCES clan_seasons(id),
    rank_from  INTEGER NOT NULL,
    rank_to    INTEGER NOT NULL,
    item_id    UUID    NOT NULL REFERENCES items(id),
    quantity   INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT chk_clan_season_rewards_band CHECK (rank_from > 0 AND rank_to >= rank_from),
    CONSTRAINT chk_clan_season_rewards_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_clan_season_rewards_season ON clan_season_rewards (season_id);

CREATE TABLE clan_alliances (
    id                   UUID        PRIMARY KEY DEFAULT uuidv7(),
    clan_a_id            UUID        NOT NULL REFERENCES clans(id),
    clan_b_id            UUID        NOT NULL REFERENCES clans(id),
    proposed_by_clan_id  UUID        NOT NULL REFERENCES clans(id),
    proposed_by_user_id  BIGINT      NOT NULL REFERENCES users(id),
    status               TEXT        NOT NULL DEFAULT 'pending',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accepted_at          TIMESTAMPTZ,
    ended_at             TIMESTAMPTZ,
    ended_by_user_id     BIGINT      REFERENCES users(id),
    CONSTRAINT chk_clan_alliances_status CHECK (status IN ('pending', 'active', 'declined', 'ended')),
    CONSTRAINT chk_clan_alliances_order CHECK (clan_a_id < clan_b_id),
    CONSTRAINT chk_clan_alliances_proposer CHECK (proposed_by_clan_id IN (clan_a_id, clan_b_id))
);

CREATE UNIQUE INDEX uq_clan_alliances_open ON clan_alliances (clan_a_id, clan_b_id)
    WHERE status IN ('pending', 'active');
CREATE INDEX idx_clan_alliances_b_open ON clan_alliances (clan_b_id) WHERE status IN ('pending', 'active');

CREATE TABLE clan_wars (
    id                UUID        PRIMARY KEY DEFAULT uuidv7(),
    season_id         UUID        NOT NULL REFERENCES clan_seasons(id),
    attacker_clan_id  UUID        NOT NULL REFERENCES clans(id),
    defender_clan_id  UUID        NOT NULL REFERENCES clans(id),
    declared_by       BIGINT      NOT NULL REFERENCES users(id),
    arena             TEXT        NOT NULL,
    arena_spec        JSONB       NOT NULL,
    ruleset           TEXT        NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'picking',
    outcome           TEXT,
    declared_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    picks_due_at      TIMESTAMPTZ,
    starts_at         TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    CONSTRAINT chk_clan_wars_arena CHECK (arena IN ('mixed', 'random', 'category_turf', 'complexity_turf')),
    CONSTRAINT chk_clan_wars_ruleset CHECK (ruleset IN ('duel', 'berserker')),
    CONSTRAINT chk_clan_wars_status CHECK (status IN ('picking', 'preparing', 'active', 'ended')),
    CONSTRAINT chk_clan_wars_outcome CHECK (outcome IN ('attacker_won', 'defender_won', 'drawn', 'retreated',
        'forfeited', 'season_ended')),
    CONSTRAINT chk_clan_wars_sides CHECK (attacker_clan_id <> defender_clan_id),
    CONSTRAINT chk_clan_wars_ended CHECK ((status = 'ended') = (outcome IS NOT NULL)
        AND (status = 'ended') = (ended_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_clan_wars_open_attack ON clan_wars (attacker_clan_id) WHERE status <> 'ended';
CREATE INDEX idx_clan_wars_open ON clan_wars (status, starts_at) WHERE status <> 'ended';
CREATE INDEX idx_clan_wars_attacker ON clan_wars (attacker_clan_id, declared_at DESC);
CREATE INDEX idx_clan_wars_defender ON clan_wars (defender_clan_id, declared_at DESC);
CREATE INDEX idx_clan_wars_season ON clan_wars (season_id, declared_at DESC);

CREATE TABLE clan_war_sides (
    war_id               UUID             NOT NULL REFERENCES clan_wars(id),
    clan_id              UUID             NOT NULL REFERENCES clans(id),
    lead_user_id         BIGINT           REFERENCES users(id),
    stake                DOUBLE PRECISION NOT NULL,
    stake_remaining      DOUBLE PRECISION NOT NULL,
    standing_at_declare  DOUBLE PRECISION NOT NULL,
    picks_submitted_at   TIMESTAMPTZ,
    PRIMARY KEY (war_id, clan_id),
    CONSTRAINT chk_clan_war_sides_stake CHECK (stake >= 0 AND stake_remaining >= 0 AND stake_remaining <= stake)
);

CREATE INDEX idx_clan_war_sides_clan ON clan_war_sides (clan_id);

CREATE TABLE clan_war_pool (
    war_id             UUID NOT NULL REFERENCES clan_wars(id),
    map_difficulty_id  UUID NOT NULL REFERENCES map_difficulties(id),
    picked_by_clan_id  UUID REFERENCES clans(id),
    source             TEXT NOT NULL,
    PRIMARY KEY (war_id, map_difficulty_id),
    CONSTRAINT chk_clan_war_pool_source CHECK (source IN ('pick', 'replacement', 'random')),
    CONSTRAINT chk_clan_war_pool_picker CHECK ((source = 'random') = (picked_by_clan_id IS NULL))
);

CREATE TABLE clan_war_participants (
    war_id               UUID             NOT NULL REFERENCES clan_wars(id),
    user_id              BIGINT           NOT NULL REFERENCES users(id),
    clan_id              UUID             NOT NULL,
    lent_by_clan_id      UUID             REFERENCES clans(id),
    duel_target_user_id  BIGINT           REFERENCES users(id),
    standing_weight      DOUBLE PRECISION NOT NULL,
    guard                DOUBLE PRECISION NOT NULL,
    guard_cycle          INTEGER          NOT NULL DEFAULT 0,
    breaks_suffered      INTEGER          NOT NULL DEFAULT 0,
    broken_at            TIMESTAMPTZ,
    contribution         DOUBLE PRECISION NOT NULL DEFAULT 0,
    joined_at            TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    left_at              TIMESTAMPTZ,
    xp_awarded           DOUBLE PRECISION,
    rewarded_at          TIMESTAMPTZ,
    PRIMARY KEY (war_id, user_id),
    CONSTRAINT fk_clan_war_participants_side FOREIGN KEY (war_id, clan_id) REFERENCES clan_war_sides (war_id, clan_id),
    CONSTRAINT chk_clan_war_participants_values CHECK (standing_weight >= 0 AND guard >= 0 AND guard_cycle >= 0
        AND breaks_suffered >= 0 AND contribution >= 0),
    CONSTRAINT chk_clan_war_participants_reward CHECK ((xp_awarded IS NULL) = (rewarded_at IS NULL))
);

CREATE INDEX idx_clan_war_participants_user ON clan_war_participants (user_id);
CREATE INDEX idx_clan_war_participants_contribution ON clan_war_participants (war_id, clan_id, contribution DESC);

CREATE TABLE clan_war_hits (
    id                 UUID             PRIMARY KEY DEFAULT uuidv7(),
    war_id             UUID             NOT NULL,
    attacker_user_id   BIGINT           NOT NULL,
    victim_user_id     BIGINT           NOT NULL,
    victim_cycle       INTEGER          NOT NULL,
    map_difficulty_id  UUID             NOT NULL REFERENCES map_difficulties(id),
    attacker_score_id  UUID             NOT NULL REFERENCES scores(id),
    victim_score_id    UUID             REFERENCES scores(id),
    damage             DOUBLE PRECISION NOT NULL,
    guard_after        DOUBLE PRECISION NOT NULL,
    broke              BOOLEAN          NOT NULL DEFAULT FALSE,
    standing_moved     DOUBLE PRECISION NOT NULL DEFAULT 0,
    xp_awarded         DOUBLE PRECISION,
    created_at         TIMESTAMPTZ      NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_clan_war_hits_attacker FOREIGN KEY (war_id, attacker_user_id)
        REFERENCES clan_war_participants (war_id, user_id),
    CONSTRAINT fk_clan_war_hits_victim FOREIGN KEY (war_id, victim_user_id)
        REFERENCES clan_war_participants (war_id, user_id),
    CONSTRAINT chk_clan_war_hits_values CHECK (damage > 0 AND guard_after >= 0 AND standing_moved >= 0),
    CONSTRAINT chk_clan_war_hits_break CHECK (broke OR standing_moved = 0),
    CONSTRAINT chk_clan_war_hits_distinct CHECK (attacker_user_id <> victim_user_id)
);

CREATE UNIQUE INDEX uq_clan_war_hits_victim_score ON clan_war_hits (war_id, attacker_user_id, victim_score_id)
    WHERE victim_score_id IS NOT NULL;
CREATE UNIQUE INDEX uq_clan_war_hits_missing_score
    ON clan_war_hits (war_id, attacker_user_id, victim_user_id, map_difficulty_id)
    WHERE victim_score_id IS NULL;
CREATE INDEX idx_clan_war_hits_war ON clan_war_hits (war_id, created_at DESC);
CREATE INDEX idx_clan_war_hits_victim_cycle ON clan_war_hits (war_id, victim_user_id, victim_cycle);
CREATE INDEX idx_clan_war_hits_attacker ON clan_war_hits (attacker_user_id);
CREATE INDEX idx_clan_war_hits_victim ON clan_war_hits (victim_user_id);

CREATE TABLE clan_war_loans (
    id               UUID        PRIMARY KEY DEFAULT uuidv7(),
    war_id           UUID        NOT NULL,
    clan_id          UUID        NOT NULL,
    lending_clan_id  UUID        NOT NULL REFERENCES clans(id),
    user_id          BIGINT      NOT NULL REFERENCES users(id),
    offered_by       BIGINT      NOT NULL REFERENCES users(id),
    status           TEXT        NOT NULL DEFAULT 'pending',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMPTZ,
    ended_at         TIMESTAMPTZ,
    CONSTRAINT fk_clan_war_loans_side FOREIGN KEY (war_id, clan_id) REFERENCES clan_war_sides (war_id, clan_id),
    CONSTRAINT chk_clan_war_loans_status
        CHECK (status IN ('pending', 'accepted', 'declined', 'cancelled', 'ended')),
    CONSTRAINT chk_clan_war_loans_clans CHECK (clan_id <> lending_clan_id)
);

CREATE UNIQUE INDEX uq_clan_war_loans_open_user ON clan_war_loans (user_id) WHERE status IN ('pending', 'accepted');
CREATE INDEX idx_clan_war_loans_war ON clan_war_loans (war_id);
CREATE INDEX idx_clan_war_loans_pair ON clan_war_loans (lending_clan_id, clan_id);
CREATE INDEX idx_clan_war_loans_user_ended ON clan_war_loans (user_id, ended_at DESC);

CREATE TABLE clan_war_reward_items (
    id                UUID    PRIMARY KEY DEFAULT uuidv7(),
    item_id           UUID    NOT NULL REFERENCES items(id),
    quantity          INTEGER NOT NULL DEFAULT 1,
    top_contributors  INTEGER,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_clan_war_reward_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_clan_war_reward_items_top CHECK (top_contributors IS NULL OR top_contributors > 0)
);

INSERT INTO curves (id, name, type, scale, shift, formula,
                    x_parameter_name, x_parameter_value,
                    y_parameter_name, y_parameter_value,
                    z_parameter_name, z_parameter_value)
VALUES
    ('acc00000-0000-0000-0000-000000000030', 'AccSaber Clan Level Curve', 'FORMULA', NULL, NULL,
     'POWER_FLOOR', 'base', 500, 'exponent', 1.35, NULL, NULL),
    ('acc00000-0000-0000-0000-000000000031', 'AccSaber Clan Roster Weight Curve', 'FORMULA', NULL, NULL,
     'LOGISTIC_SIGMOID', 'k', 0.25, 'y1', 0.1, 'x1', 20);

INSERT INTO item_types (key, name, description) VALUES
    ('clan_cosmetic', 'Clan Cosmetic', 'Parent grouping for everything a clan owns and equips.');

INSERT INTO item_types (parent_type_id, key, name, description, value_schema)
SELECT parent.id, 'clan_emblem', 'Clan Emblem', 'The icon shown next to a clan everywhere it appears.', contract.value_schema
FROM item_types parent, item_types contract
WHERE parent.key = 'clan_cosmetic' AND contract.key = 'badge';

INSERT INTO item_types (parent_type_id, key, name, description, value_schema)
SELECT parent.id, 'clan_banner', 'Clan Banner', 'The wide artwork at the top of a clan page.', contract.value_schema
FROM item_types parent, item_types contract
WHERE parent.key = 'clan_cosmetic' AND contract.key = 'profile_background';

INSERT INTO item_types (parent_type_id, key, name, description, value_schema)
SELECT parent.id, 'clan_tag_effect', 'Clan Tag Effect',
       'How the clan tag renders next to every member name. The title contract without text, since the text is the tag.',
       jsonb_set(contract.value_schema #- '{properties,text}', '{required}', '["states"]')
FROM item_types parent, item_types contract
WHERE parent.key = 'clan_cosmetic' AND contract.key = 'title';

INSERT INTO clan_level_capacities (level, capacity, amount) VALUES
    (0, 'member_slots', 10),
    (0, 'mission_slots', 1);

INSERT INTO clan_level_war_modes (axis, mode, level) VALUES
    ('arena', 'mixed', 0),
    ('ruleset', 'duel', 0);
