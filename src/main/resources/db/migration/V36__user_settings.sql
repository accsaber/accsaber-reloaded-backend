CREATE TABLE user_settings (
    id         UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key        TEXT         NOT NULL,
    value      JSONB        NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT unique_user_setting UNIQUE (user_id, key)
);

CREATE INDEX idx_user_settings_user ON user_settings (user_id);
CREATE INDEX idx_user_settings_user_key_prefix ON user_settings (user_id, key text_pattern_ops);
