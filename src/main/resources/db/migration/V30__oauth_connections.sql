CREATE TABLE oauth_connections (
    id                  UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id             BIGINT       NOT NULL,
    provider            VARCHAR(32)  NOT NULL,
    provider_user_id    VARCHAR(64)  NOT NULL,
    provider_username   VARCHAR(255),
    provider_avatar_url TEXT,
    linked_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_oauth_connections_users FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE oauth_sessions (
    id                        UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id                   BIGINT      NOT NULL,
    connection_id             UUID        NOT NULL,
    refresh_token             TEXT        NOT NULL,
    refresh_token_expires_at  TIMESTAMPTZ NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_oauth_sessions_users       FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_oauth_sessions_connection  FOREIGN KEY (connection_id) REFERENCES oauth_connections(id),
    CONSTRAINT unique_oauth_sessions_refresh_token UNIQUE (refresh_token)
);

CREATE INDEX idx_oauth_sessions_user       ON oauth_sessions(user_id);
CREATE INDEX idx_oauth_sessions_connection ON oauth_sessions(connection_id);

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
