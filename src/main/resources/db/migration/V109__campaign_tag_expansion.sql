UPDATE campaign_tags SET name = 'Hip Hop & Rap'
WHERE kind = 'genre' AND LOWER(name) = 'hip-hop' AND active = true
  AND NOT EXISTS (
      SELECT 1 FROM campaign_tags t
      WHERE t.kind = 'genre' AND LOWER(t.name) = 'hip hop & rap' AND t.active = true);

INSERT INTO campaign_tags (kind, name, category_id, system)
VALUES ('category', 'Low Mid',
        (SELECT id FROM categories WHERE code = 'low_mid' AND active = true), true)
ON CONFLICT (kind, LOWER(name)) WHERE active = true DO NOTHING;

INSERT INTO campaign_tags (kind, name, system) VALUES
    ('category', 'Midspeed',   true),
    ('category', 'Low Speed',  true),
    ('category', 'Speed',      true),
    ('category', 'High Speed', true),
    ('category', 'Low Tech',   true),
    ('category', 'Tech',       true),
    ('category', 'Extreme',    true),
    ('category', 'Balanced',   true),
    ('category', 'Challenge',  true)
ON CONFLICT (kind, LOWER(name)) WHERE active = true DO NOTHING;

INSERT INTO campaign_tags (kind, name, system) VALUES
    ('theme', 'Poodles',      true),
    ('theme', 'Vivify',       true),
    ('theme', 'Just For Fun', true)
ON CONFLICT (kind, LOWER(name)) WHERE active = true DO NOTHING;

INSERT INTO campaign_tags (kind, name, system) VALUES
    ('genre', 'Alternative',            true),
    ('genre', 'Ambient',                true),
    ('genre', 'Classical & Orchestral', true),
    ('genre', 'Comedy & Meme',          true),
    ('genre', 'Dance',                  true),
    ('genre', 'Drum and Bass',          true),
    ('genre', 'Dubstep',                true),
    ('genre', 'Folk & Acoustic',        true),
    ('genre', 'Funk & Disco',           true),
    ('genre', 'Hardcore',               true),
    ('genre', 'Holiday',                true),
    ('genre', 'House',                  true),
    ('genre', 'Indie',                  true),
    ('genre', 'Instrumental',           true),
    ('genre', 'J-Rock',                 true),
    ('genre', 'Jazz',                   true),
    ('genre', 'Kids & Family',          true),
    ('genre', 'Nightcore',              true),
    ('genre', 'Punk',                   true),
    ('genre', 'R&B',                    true),
    ('genre', 'Soul',                   true),
    ('genre', 'Speedcore',              true),
    ('genre', 'Swing',                  true),
    ('genre', 'TV & Film',              true),
    ('genre', 'Techno',                 true),
    ('genre', 'Trance',                 true),
    ('genre', 'Vocaloid',               true)
ON CONFLICT (kind, LOWER(name)) WHERE active = true DO NOTHING;
