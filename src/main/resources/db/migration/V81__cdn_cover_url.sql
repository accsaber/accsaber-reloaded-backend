ALTER TABLE maps ADD COLUMN cdn_cover_url TEXT;

UPDATE maps
SET cdn_cover_url = cover_url,
    cover_url = 'https://eu.cdn.beatsaver.com/' || song_hash || '.jpg'
WHERE cover_url LIKE 'https://cdn.accsaberreloaded.com/covers/%';
