CREATE TABLE campaign_collaborators (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    campaign_id UUID        NOT NULL REFERENCES campaigns(id),
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    status      TEXT        NOT NULL DEFAULT 'pending'
                            CHECK (status IN ('pending', 'accepted', 'declined')),
    invited_by  BIGINT      REFERENCES users(id),
    active      BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_campaign_collaborators_updated_at
    BEFORE UPDATE ON campaign_collaborators
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE UNIQUE INDEX one_active_campaign_collaborator
    ON campaign_collaborators(campaign_id, user_id) WHERE active = true;

CREATE INDEX idx_campaign_collaborators_user
    ON campaign_collaborators(user_id) WHERE active = true;

CREATE INDEX idx_campaign_collaborators_campaign
    ON campaign_collaborators(campaign_id) WHERE active = true;

ALTER TABLE campaign_difficulties DROP CONSTRAINT IF EXISTS campaign_difficulties_requirement_type_check;

ALTER TABLE campaign_difficulties
    ALTER COLUMN map_difficulty_id DROP NOT NULL,
    ALTER COLUMN requirement_type  DROP NOT NULL,
    ALTER COLUMN requirement_value DROP NOT NULL,
    ADD COLUMN barrier                 BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN barrier_condition_type  TEXT
        CHECK (barrier_condition_type IN ('AVERAGE_ACC', 'AVERAGE_AP', 'AP_MAX', 'ACC_MAX',
            'STREAK_115_AVERAGE', 'STREAK_115_MAX', 'FC', 'AVERAGE_RANK', 'MAX_RANK')),
    ADD COLUMN barrier_condition_value NUMERIC(20,6),
    ADD CONSTRAINT campaign_difficulties_requirement_type_check
        CHECK (requirement_type IN ('ACC', 'AP', 'SCORE', 'STREAK_115', 'FC', 'RANK')),
    ADD CONSTRAINT campaign_difficulties_kind_shape CHECK (
        (barrier = false AND map_difficulty_id IS NOT NULL
            AND requirement_type IS NOT NULL AND requirement_value IS NOT NULL)
        OR (barrier = true AND barrier_condition_type IS NOT NULL));

CREATE INDEX idx_campaign_difficulties_barrier
    ON campaign_difficulties(campaign_id) WHERE barrier = true AND active = true;

CREATE TABLE campaign_barrier_affected_difficulties (
    barrier_id             UUID        NOT NULL REFERENCES campaign_difficulties(id),
    campaign_difficulty_id UUID        NOT NULL REFERENCES campaign_difficulties(id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (barrier_id, campaign_difficulty_id),
    CHECK (barrier_id <> campaign_difficulty_id)
);

CREATE INDEX idx_campaign_barrier_affected_barrier
    ON campaign_barrier_affected_difficulties(barrier_id);

CREATE INDEX idx_campaign_barrier_affected_difficulty
    ON campaign_barrier_affected_difficulties(campaign_difficulty_id);

ALTER TABLE user_campaign_scores ALTER COLUMN score_id DROP NOT NULL;

CREATE TABLE campaign_texts (
    id          UUID          PRIMARY KEY DEFAULT uuidv7(),
    campaign_id UUID          NOT NULL REFERENCES campaigns(id),
    content     TEXT          NOT NULL DEFAULT '',
    position_x  INTEGER       NOT NULL,
    position_y  INTEGER       NOT NULL,
    font        TEXT,
    scale       NUMERIC(6,3),
    color       TEXT,
    effects     TEXT,
    active      BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TRIGGER trg_campaign_texts_updated_at
    BEFORE UPDATE ON campaign_texts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE INDEX idx_campaign_texts_campaign ON campaign_texts(campaign_id) WHERE active = true;
