UPDATE users
SET avatar_url = regexp_replace(avatar_url, '\.avif(\?|$)', '.png\1')
WHERE avatar_url LIKE '%.avif%';

UPDATE maps
SET cover_url = regexp_replace(cover_url, '\.avif(\?|$)', '.png\1')
WHERE cover_url LIKE '%.avif%';

UPDATE campaigns
SET background_url = regexp_replace(background_url, '\.avif(\?|$)', '.png\1')
WHERE background_url LIKE '%.avif%';

UPDATE campaigns
SET icon_url = regexp_replace(icon_url, '\.avif(\?|$)', '.png\1')
WHERE icon_url LIKE '%.avif%';

UPDATE items
SET icon_url = regexp_replace(icon_url, '\.avif(\?|$)', '.png\1')
WHERE icon_url LIKE '%.avif%';
