UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["states"],
  "additionalProperties":true,
  "properties":{
    "viewBox":{"type":"string","minLength":7},
    "avatarMask":{"type":"string","minLength":1},
    "states":{
      "type":"array","minItems":1,
      "items":{
        "type":"object","required":["atMs","paths"],"additionalProperties":true,
        "properties":{
          "atMs":{"type":"integer","minimum":0},
          "paths":{
            "type":"array","minItems":1,
            "items":{
              "type":"object","required":["d"],"additionalProperties":true,
              "properties":{
                "d":{"type":"string","minLength":1},
                "stroke":{"type":"string"},
                "strokeWidth":{"type":"number","minimum":0},
                "fill":{"type":"string"},
                "strokeLinecap":{"enum":["butt","round","square"]},
                "strokeLinejoin":{"enum":["miter","round","bevel","arcs"]},
                "strokeDasharray":{"type":"string"},
                "strokeOpacity":{"type":"number","minimum":0,"maximum":1},
                "fillOpacity":{"type":"number","minimum":0,"maximum":1},
                "transform":{"type":"string"}
              }
            }
          },
          "filters":{
            "type":"array",
            "items":{"type":"object","required":["type"],"properties":{"type":{"type":"string","minLength":1}},"additionalProperties":true}
          }
        }
      }
    },
    "durationMs":{"type":"integer","minimum":0},
    "loop":{"enum":["loop","pingpong","once"]},
    "easing":{"type":"string"}
  }
}'::jsonb WHERE key = 'profile_border_shape';

UPDATE items
SET value = value || jsonb_build_object('avatarMask', 'M14,0 L86,0 Q100,0 100,14 L100,86 Q100,100 86,100 L14,100 Q0,100 0,86 L0,14 Q0,0 14,0 Z')
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_shape')
  AND name = 'Default Frame';

UPDATE items
SET value = value || jsonb_build_object('avatarMask', 'M50,5 L93,27.5 L93,72.5 L50,95 L7,72.5 L7,27.5 Z')
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_shape')
  AND name = 'Hexagon Border';
