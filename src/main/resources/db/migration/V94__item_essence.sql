ALTER TABLE users
    ADD COLUMN item_essence NUMERIC(20, 6) NOT NULL DEFAULT 0;

CREATE TABLE user_item_disintegrations (
    id               UUID          PRIMARY KEY DEFAULT uuidv7(),
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    item_id          UUID          NOT NULL REFERENCES items(id),
    quantity         BIGINT        NOT NULL CHECK (quantity > 0),
    essence_gained   NUMERIC(20, 6) NOT NULL,
    disintegrated_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_item_disintegrations_user ON user_item_disintegrations(user_id, disintegrated_at DESC);
CREATE INDEX idx_user_item_disintegrations_item ON user_item_disintegrations(item_id);
