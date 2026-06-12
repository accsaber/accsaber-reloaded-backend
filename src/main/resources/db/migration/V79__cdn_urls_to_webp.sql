UPDATE users
SET avatar_url = regexp_replace(avatar_url, '\.(png|gif|avif)(\?|$)', '.webp\2')
WHERE avatar_url ~ '\.(png|gif|avif)(\?|$)';

UPDATE maps
SET cover_url = regexp_replace(cover_url, '\.(png|gif|avif)(\?|$)', '.webp\2')
WHERE cover_url ~ '\.(png|gif|avif)(\?|$)';

UPDATE campaigns
SET background_url = regexp_replace(background_url, '\.(png|gif|avif)(\?|$)', '.webp\2')
WHERE background_url ~ '\.(png|gif|avif)(\?|$)';

UPDATE campaigns
SET icon_url = regexp_replace(icon_url, '\.(png|gif|avif)(\?|$)', '.webp\2')
WHERE icon_url ~ '\.(png|gif|avif)(\?|$)';

UPDATE items
SET icon_url = regexp_replace(icon_url, '\.(png|gif|avif)(\?|$)', '.webp\2')
WHERE icon_url ~ '\.(png|gif|avif)(\?|$)';
