ALTER TABLE clan_equipped_items
    ADD COLUMN variant_key TEXT;

UPDATE item_types
SET description = 'The shape of the card behind the clan tag next to every member name. The fill is the clan''s own tag colour; the item supplies the silhouette (end caps that stretch to any tag length), ornaments and effects.',
    value_schema = '{
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "properties": {
        "left": {"$ref": "#/$defs/cap"},
        "right": {"$ref": "#/$defs/cap"},
        "decals": {"type": "array", "items": {"$ref": "#/$defs/decal"}},
        "fx": {"type": "object"},
        "variants": {"type": "array"}
      },
      "$defs": {
        "path": {"type": "object", "required": ["d"]},
        "cap": {
          "type": "object",
          "required": ["viewBox", "paths"],
          "properties": {"viewBox": {"type": "string"}, "paths": {"type": "array", "items": {"$ref": "#/$defs/path"}}}
        },
        "decal": {
          "type": "object",
          "required": ["viewBox", "paths", "anchor", "sizeEm"],
          "properties": {
            "viewBox": {"type": "string"},
            "paths": {"type": "array", "items": {"$ref": "#/$defs/path"}},
            "anchor": {"enum": ["left", "center", "right"]},
            "xEm": {"type": "number"},
            "yEm": {"type": "number"},
            "sizeEm": {"type": "number"},
            "rotateDeg": {"type": "number"},
            "swing": {"type": "object"},
            "pulse": {"type": "object"}
          }
        }
      }
    }'::jsonb
WHERE key = 'clan_tag_card';
