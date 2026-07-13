CREATE TABLE user_event_profiles (
    id                 UUID        PRIMARY KEY DEFAULT uuidv7(),
    event_id           UUID        NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id            BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    unlocked_week      INTEGER     NOT NULL DEFAULT 1 CHECK (unlocked_week >= 1),
    missions_completed INTEGER     NOT NULL DEFAULT 0 CHECK (missions_completed >= 0),
    started_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ,
    bonus_xp           INTEGER     NOT NULL DEFAULT 0 CHECK (bonus_xp >= 0),
    bonus_awarded_at   TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_user_event_profile UNIQUE (event_id, user_id)
);

CREATE INDEX idx_user_event_profiles_user
    ON user_event_profiles(user_id);

CREATE TRIGGER trg_user_event_profiles_updated_at
    BEFORE UPDATE ON user_event_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TABLE user_event_bonuses;
