ALTER TABLE users ADD COLUMN cdn_avatar_url TEXT;

UPDATE users
SET avatar_url = COALESCE(
        last_synced_avatar_url,
        'https://cdn.assets.beatleader.com/' || id || '.png'
    )
WHERE avatar_url LIKE 'https://cdn.accsaberreloaded.com/avatars/%';

UPDATE maps
SET cover_url = NULL
WHERE cover_url LIKE 'https://cdn.accsaberreloaded.com/covers/%';
