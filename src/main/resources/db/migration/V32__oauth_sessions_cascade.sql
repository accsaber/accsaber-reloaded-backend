ALTER TABLE oauth_sessions
    DROP CONSTRAINT fk_oauth_sessions_users,
    DROP CONSTRAINT fk_oauth_sessions_connection,
    ADD CONSTRAINT fk_oauth_sessions_users
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_oauth_sessions_connection
        FOREIGN KEY (connection_id) REFERENCES oauth_connections(id) ON DELETE CASCADE;
