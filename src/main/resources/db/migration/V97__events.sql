CREATE TABLE events (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    title          TEXT        NOT NULL,
    description    TEXT,
    background_url TEXT,
    icon_url       TEXT,
    starts_at      TIMESTAMPTZ NOT NULL,
    ends_at        TIMESTAMPTZ NOT NULL,
    bonus_xp       INTEGER     NOT NULL DEFAULT 0 CHECK (bonus_xp >= 0),
    active         BOOLEAN     NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_events_window CHECK (ends_at > starts_at)
);

CREATE INDEX idx_events_active_window
    ON events(starts_at, ends_at) WHERE active = true;

CREATE TRIGGER trg_events_updated_at
    BEFORE UPDATE ON events
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE event_bonus_items (
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    item_id  UUID NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, item_id)
);

ALTER TABLE mission_templates
    ADD COLUMN event_id          UUID REFERENCES events(id) ON DELETE CASCADE,
    ADD COLUMN unlocks_at        TIMESTAMPTZ,
    ADD COLUMN completable_until TIMESTAMPTZ,
    ADD COLUMN repeatable        BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN max_completions   INTEGER CHECK (max_completions > 0),
    ADD COLUMN fixed_xp          INTEGER CHECK (fixed_xp >= 0),
    ADD COLUMN event_targets     JSONB;

ALTER TABLE mission_templates
    ADD CONSTRAINT chk_mission_templates_event_pool
        CHECK (event_id IS NULL OR pool = 'event');

CREATE INDEX idx_mission_templates_event
    ON mission_templates(event_id) WHERE event_id IS NOT NULL;

CREATE TABLE user_event_bonuses (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    event_id   UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    xp_awarded INTEGER     NOT NULL DEFAULT 0,
    awarded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_event_bonus UNIQUE (event_id, user_id)
);

CREATE INDEX idx_user_event_bonuses_user
    ON user_event_bonuses(user_id);

CREATE INDEX idx_user_missions_template_user
    ON user_missions(template_id, user_id, status);
