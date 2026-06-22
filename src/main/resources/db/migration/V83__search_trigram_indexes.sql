CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_users_name_trgm
    ON users USING gin (LOWER(name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_maps_song_name_trgm
    ON maps USING gin (LOWER(song_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_maps_song_author_trgm
    ON maps USING gin (LOWER(song_author) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_maps_map_author_trgm
    ON maps USING gin (LOWER(map_author) gin_trgm_ops);
