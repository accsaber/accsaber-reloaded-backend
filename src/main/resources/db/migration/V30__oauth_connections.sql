CREATE TABLE oauth_connections (
    id                  UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id             BIGINT       NOT NULL,
    provider            VARCHAR(32)  NOT NULL,
    provider_user_id    VARCHAR(64)  NOT NULL,
    provider_username   VARCHAR(255),
    provider_avatar_url TEXT,
    provider_metadata   JSONB,
    linked_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_oauth_connections_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE UNIQUE INDEX unique_oauth_provider_identity
    ON oauth_connections (provider, provider_user_id)
    WHERE active = TRUE;

CREATE UNIQUE INDEX unique_oauth_user_provider
    ON oauth_connections (user_id, provider)
    WHERE active = TRUE;

CREATE INDEX idx_oauth_connections_user_active
    ON oauth_connections (user_id, active);


INSERT INTO oauth_connections (user_id, provider, provider_user_id, linked_at, updated_at, active)
SELECT user_id, 'discord', discord_id, created_at, created_at, TRUE
FROM discord_user_links;

DROP TABLE discord_user_links;
DROP TABLE staff_oauth_links;
