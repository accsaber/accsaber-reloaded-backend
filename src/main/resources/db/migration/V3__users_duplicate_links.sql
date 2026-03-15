CREATE TABLE users_duplicate_links (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    secondary_user_id   BIGINT      NOT NULL REFERENCES users(id),
    primary_user_id     BIGINT      NOT NULL REFERENCES users(id),
    merged              BOOLEAN     NOT NULL DEFAULT false,
    merged_at           TIMESTAMPTZ,
    merged_by           UUID        REFERENCES staff_users(id),
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_different_users CHECK (secondary_user_id <> primary_user_id)
);

CREATE TRIGGER trg_users_duplicate_links_updated_at
    BEFORE UPDATE ON users_duplicate_links
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE UNIQUE INDEX unique_secondary_user ON users_duplicate_links(secondary_user_id);
CREATE INDEX idx_users_duplicate_links_primary ON users_duplicate_links(primary_user_id);
