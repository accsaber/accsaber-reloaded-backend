ALTER TABLE item_modifiers
    ADD COLUMN effect_spec JSONB;

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["text","states"],
  "additionalProperties":true,
  "properties":{
    "text":{"type":"string","minLength":1,"maxLength":64},
    "states":{
      "type":"array","minItems":1,
      "items":{
        "type":"object","required":["atMs"],"additionalProperties":true,
        "properties":{
          "atMs":{"type":"integer","minimum":0},
          "color":{"type":"string"},
          "gradient":{
            "type":"object","required":["type","stops"],
            "oneOf":[
              {"properties":{"type":{"const":"linear"},"angleDeg":{"type":"number"},"stops":{"type":"array","minItems":2,"items":{"type":"object","required":["atPct","hex"],"properties":{"atPct":{"type":"number","minimum":0,"maximum":100},"hex":{"type":"string"}},"additionalProperties":true}}},"required":["angleDeg","stops"]},
              {"properties":{"type":{"const":"radial"},"centerXPct":{"type":"number"},"centerYPct":{"type":"number"},"radiusPct":{"type":"number"},"stops":{"type":"array","minItems":2,"items":{"type":"object","required":["atPct","hex"],"properties":{"atPct":{"type":"number","minimum":0,"maximum":100},"hex":{"type":"string"}},"additionalProperties":true}}},"required":["stops"]},
              {"properties":{"type":{"const":"conic"},"centerXPct":{"type":"number"},"centerYPct":{"type":"number"},"angleDeg":{"type":"number"},"stops":{"type":"array","minItems":2,"items":{"type":"object","required":["atPct","hex"],"properties":{"atPct":{"type":"number","minimum":0,"maximum":100},"hex":{"type":"string"}},"additionalProperties":true}}},"required":["stops"]}
            ]
          },
          "fontWeight":{"type":"integer","minimum":100,"maximum":900},
          "fontStyle":{"enum":["normal","italic"]},
          "letterSpacingPx":{"type":"number"},
          "effects":{
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
}'::jsonb WHERE key = 'title';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["states"],
  "additionalProperties":true,
  "properties":{
    "viewBox":{"type":"string","minLength":7},
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

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["states"],
  "additionalProperties":true,
  "properties":{
    "states":{
      "type":"array","minItems":1,
      "items":{
        "type":"object","required":["atMs","fill"],"additionalProperties":true,
        "properties":{
          "atMs":{"type":"integer","minimum":0},
          "fill":{
            "type":"object","required":["type"],
            "oneOf":[
              {"properties":{"type":{"const":"solid"},"hex":{"type":"string"}},"required":["hex"]},
              {"properties":{"type":{"const":"linear"},"angleDeg":{"type":"number"},"stops":{"type":"array","minItems":2,"items":{"type":"object","required":["atPct","hex"],"properties":{"atPct":{"type":"number","minimum":0,"maximum":100},"hex":{"type":"string"}},"additionalProperties":true}}},"required":["angleDeg","stops"]},
              {"properties":{"type":{"const":"radial"},"centerXPct":{"type":"number"},"centerYPct":{"type":"number"},"radiusPct":{"type":"number"},"stops":{"type":"array","minItems":2,"items":{"type":"object","required":["atPct","hex"],"properties":{"atPct":{"type":"number","minimum":0,"maximum":100},"hex":{"type":"string"}},"additionalProperties":true}}},"required":["stops"]},
              {"properties":{"type":{"const":"conic"},"centerXPct":{"type":"number"},"centerYPct":{"type":"number"},"angleDeg":{"type":"number"},"stops":{"type":"array","minItems":2,"items":{"type":"object","required":["atPct","hex"],"properties":{"atPct":{"type":"number","minimum":0,"maximum":100},"hex":{"type":"string"}},"additionalProperties":true}}},"required":["stops"]}
            ]
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
}'::jsonb WHERE key = 'profile_border_color';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["asset"],
  "additionalProperties":true,
  "properties":{
    "asset":{
      "type":"object",
      "required":["altText"],
      "additionalProperties":true,
      "properties":{
        "svg":{"type":"string"},
        "raster":{
          "type":"object",
          "minProperties":1,
          "additionalProperties":{"type":"string"},
          "properties":{
            "1x":{"type":"string"},
            "2x":{"type":"string"},
            "3x":{"type":"string"},
            "4x":{"type":"string"}
          }
        },
        "altText":{"type":"string","minLength":1,"maxLength":120}
      }
    },
    "tint":{"type":"string"},
    "effects":{
      "type":"array",
      "items":{"type":"object","required":["type"],"properties":{"type":{"type":"string","minLength":1}},"additionalProperties":true}
    }
  }
}'::jsonb WHERE key = 'badge';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["asset"],
  "additionalProperties":true,
  "properties":{
    "asset":{
      "type":"object",
      "required":["altText"],
      "additionalProperties":true,
      "properties":{
        "video":{"type":"string"},
        "raster":{"type":"object","minProperties":1,"additionalProperties":{"type":"string"}},
        "altText":{"type":"string","minLength":1,"maxLength":160}
      }
    },
    "fit":{"enum":["cover","contain","tile","center"]},
    "opacity":{"type":"number","minimum":0,"maximum":1},
    "blendMode":{"enum":["normal","multiply","screen","overlay","darken","lighten","color-dodge","color-burn","hard-light","soft-light","difference","exclusion","hue","saturation","color","luminosity"]},
    "filters":{
      "type":"array",
      "items":{"type":"object","required":["type"],"additionalProperties":true}
    },
    "parallax":{"type":"object","additionalProperties":true}
  }
}'::jsonb WHERE key = 'profile_background';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["asset"],
  "additionalProperties":true,
  "properties":{
    "asset":{
      "type":"object",
      "required":["altText"],
      "additionalProperties":true,
      "properties":{
        "raster":{"type":"object","minProperties":1,"additionalProperties":{"type":"string"}},
        "altText":{"type":"string","minLength":1,"maxLength":160}
      }
    },
    "fit":{"enum":["cover","contain","tile","center"]},
    "opacity":{"type":"number","minimum":0,"maximum":1},
    "blendMode":{"enum":["normal","multiply","screen","overlay","darken","lighten"]}
  }
}'::jsonb WHERE key = 'profile_thumbnail_background';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["tokens"],
  "additionalProperties":true,
  "properties":{
    "tokens":{
      "type":"object",
      "minProperties":1,
      "additionalProperties":{"type":"string"}
    },
    "darkMode":{"type":"boolean"}
  }
}'::jsonb WHERE key = 'theme';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["statKey","label"],
  "additionalProperties":true,
  "properties":{
    "statKey":{"type":"string","minLength":1},
    "label":{"type":"string","minLength":1,"maxLength":64},
    "icon":{"type":"string"},
    "format":{
      "type":"object",
      "required":["type"],
      "additionalProperties":true,
      "properties":{
        "type":{"enum":["integer","decimal","duration","percent","custom"]},
        "decimals":{"type":"integer","minimum":0,"maximum":6},
        "suffix":{"type":"string"},
        "prefix":{"type":"string"}
      }
    }
  }
}'::jsonb WHERE key = 'statistic';

UPDATE item_types SET value_schema = '{
  "$schema":"https://json-schema.org/draft/2020-12/schema",
  "contractVersion":1,
  "type":"object",
  "required":["effect"],
  "additionalProperties":true,
  "properties":{
    "effect":{"type":"string","minLength":1},
    "amount":{"type":"number"}
  }
}'::jsonb WHERE key = 'perk';

UPDATE item_modifiers SET effect_spec = '{"contractVersion":1,"compositions":[]}'::jsonb WHERE key = 'normal';
UPDATE item_modifiers SET effect_spec = '{"contractVersion":1,"compositions":[]}'::jsonb WHERE key = 'unique';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"filter","filterType":"sepia","amount":0.6},
    {"type":"label_overlay","position":"top_right","text":"Vintage","color":"#cd7f32","background":"rgba(0,0,0,0.6)"}
  ]
}'::jsonb WHERE key = 'vintage';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"border_outline","color":"#4caf50","widthPx":2,"glow":{"color":"#4caf50","blurPx":6}}
  ]
}'::jsonb WHERE key = 'genuine';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"stat_counter","position":"bottom_right","statKey":"play_count","background":"rgba(0,0,0,0.7)","color":"#ff8800","prefix":"x"}
  ]
}'::jsonb WHERE key = 'strange';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"particle","preset":"sparkles","color":"auto","ratePerSec":8}
  ]
}'::jsonb WHERE key = 'unusual';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"particle","preset":"spooky_smoke","color":"#7a4cff","ratePerSec":5},
    {"type":"filter","filterType":"saturate","amount":1.2}
  ]
}'::jsonb WHERE key = 'haunted';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"particle","preset":"snowflakes","color":"#ffffff","ratePerSec":3},
    {"type":"border_outline","color":"#c8102e","widthPx":1}
  ]
}'::jsonb WHERE key = 'jolly';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"border_outline","color":"#ffd700","widthPx":3,"glow":{"color":"#ffd700","blurPx":8}},
    {"type":"particle","preset":"gold_sparkles","color":"#ffd700","ratePerSec":4}
  ]
}'::jsonb WHERE key = 'collectors';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"shader_overlay","shader":"rainbow_shimmer","speedHz":0.5,"blendMode":"screen","opacity":0.6}
  ]
}'::jsonb WHERE key = 'holographic';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"wear_pattern","pattern":"stickers","wearLevelExpr":"${wearLevel}"}
  ]
}'::jsonb WHERE key = 'decorated';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"border_outline","color":"#ff007a","widthPx":3,"glow":{"color":"#ff007a","blurPx":10}},
    {"type":"particle","preset":"ember","color":"#ff007a","ratePerSec":6}
  ]
}'::jsonb WHERE key = 'ascendant';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"filter","filterType":"noise_overlay","amount":0.3},
    {"type":"filter","filterType":"desaturate","amount":0.4}
  ]
}'::jsonb WHERE key = 'battle_worn';
UPDATE item_modifiers SET effect_spec = '{
  "contractVersion":1,
  "compositions":[
    {"type":"label_overlay","position":"top_left","textExpr":"#${serial}","background":"linear-gradient(45deg,#ffd700,#ff6a00)","color":"#000000","fontWeight":700}
  ]
}'::jsonb WHERE key = 'founders';

ALTER TABLE item_modifiers
    ALTER COLUMN effect_spec SET NOT NULL;

UPDATE items SET value = jsonb_build_object(
    'text', value->>'text',
    'states', jsonb_build_array(
        jsonb_build_object(
            'atMs', 0,
            'color', COALESCE(value->>'color', '#ffffff')
        )
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'title')
  AND value IS NOT NULL
  AND NOT (value ? 'states');

UPDATE items SET value = jsonb_build_object(
    'viewBox', '0 0 100 100',
    'states', jsonb_build_array(
        jsonb_build_object(
            'atMs', 0,
            'paths', jsonb_build_array(
                jsonb_build_object(
                    'd', CASE value->>'shape'
                        WHEN 'hexagon' THEN 'M50,5 L93,27.5 L93,72.5 L50,95 L7,72.5 L7,27.5 Z'
                        WHEN 'circle' THEN 'M50,10 A40,40 0 1,0 50,90 A40,40 0 1,0 50,10 Z'
                        WHEN 'star' THEN 'M50,5 L61,38 L95,38 L67,58 L78,90 L50,70 L22,90 L33,58 L5,38 L39,38 Z'
                        WHEN 'square' THEN 'M5,5 L95,5 L95,95 L5,95 Z'
                        WHEN 'triangle' THEN 'M50,5 L95,90 L5,90 Z'
                        WHEN 'diamond' THEN 'M50,5 L95,50 L50,95 L5,50 Z'
                        ELSE 'M50,5 L93,27.5 L93,72.5 L50,95 L7,72.5 L7,27.5 Z'
                    END,
                    'stroke', 'currentColor',
                    'strokeWidth', 4,
                    'fill', 'none'
                )
            )
        )
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_shape')
  AND value->>'kind' = 'static';

UPDATE items SET value = jsonb_build_object(
    'viewBox', '0 0 100 100',
    'durationMs', COALESCE((value->>'durationMs')::int, 3000),
    'loop', 'pingpong',
    'easing', 'easeInOut',
    'states', jsonb_build_array(
        jsonb_build_object(
            'atMs', 0,
            'paths', jsonb_build_array(
                jsonb_build_object(
                    'd', CASE value->>'from'
                        WHEN 'star' THEN 'M50,5 L61,38 L95,38 L67,58 L78,90 L50,70 L22,90 L33,58 L5,38 L39,38 Z'
                        WHEN 'circle' THEN 'M50,10 A40,40 0 1,0 50,90 A40,40 0 1,0 50,10 Z'
                        WHEN 'hexagon' THEN 'M50,5 L93,27.5 L93,72.5 L50,95 L7,72.5 L7,27.5 Z'
                        ELSE 'M50,5 L93,27.5 L93,72.5 L50,95 L7,72.5 L7,27.5 Z'
                    END,
                    'stroke', 'currentColor', 'strokeWidth', 4, 'fill', 'none'
                )
            )
        ),
        jsonb_build_object(
            'atMs', COALESCE((value->>'durationMs')::int, 3000),
            'paths', jsonb_build_array(
                jsonb_build_object(
                    'd', CASE value->>'to'
                        WHEN 'star' THEN 'M50,5 L61,38 L95,38 L67,58 L78,90 L50,70 L22,90 L33,58 L5,38 L39,38 Z'
                        WHEN 'circle' THEN 'M50,10 A40,40 0 1,0 50,90 A40,40 0 1,0 50,10 Z'
                        WHEN 'hexagon' THEN 'M50,5 L93,27.5 L93,72.5 L50,95 L7,72.5 L7,27.5 Z'
                        ELSE 'M50,10 A40,40 0 1,0 50,90 A40,40 0 1,0 50,10 Z'
                    END,
                    'stroke', 'currentColor', 'strokeWidth', 4, 'fill', 'none'
                )
            )
        )
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_shape')
  AND value->>'kind' = 'morph';

UPDATE items SET value = jsonb_build_object(
    'states', jsonb_build_array(
        jsonb_build_object(
            'atMs', 0,
            'fill', jsonb_build_object('type', 'solid', 'hex', value->>'hex')
        )
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_color')
  AND value->>'kind' = 'solid';

UPDATE items SET value = jsonb_build_object(
    'states', jsonb_build_array(
        jsonb_build_object(
            'atMs', 0,
            'fill', jsonb_build_object(
                'type', 'linear',
                'angleDeg', COALESCE((value->>'angle')::numeric, 90),
                'stops', (
                    SELECT jsonb_agg(jsonb_build_object(
                        'atPct', (s.value->>'at')::numeric * 100,
                        'hex', s.value->>'hex'
                    ))
                    FROM jsonb_array_elements(value->'stops') s
                )
            )
        )
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_color')
  AND value->>'kind' = 'gradient';

UPDATE items SET value = jsonb_build_object(
    'durationMs', COALESCE((value->>'durationMs')::int, 4000),
    'loop', 'loop',
    'easing', 'linear',
    'states', (
        SELECT jsonb_agg(jsonb_build_object(
            'atMs', ((f.ord - 1) * COALESCE((value->>'durationMs')::int, 4000) / GREATEST(jsonb_array_length(value->'frames'), 1))::int,
            'fill', jsonb_build_object('type', 'solid', 'hex', f.frame->>'hex')
        ))
        FROM jsonb_array_elements(value->'frames') WITH ORDINALITY AS f(frame, ord)
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'profile_border_color')
  AND value->>'kind' = 'animated';

UPDATE items SET value = jsonb_build_object(
    'asset', jsonb_build_object(
        'raster', jsonb_build_object('1x', value->>'image_url'),
        'altText', name
    )
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'badge')
  AND value ? 'image_url';

UPDATE items SET value = jsonb_build_object(
    'asset', jsonb_build_object(
        'raster', jsonb_build_object('1x', value->>'image_url'),
        'altText', name
    ),
    'fit', 'cover',
    'opacity', 1.0
)
WHERE type_id IN (
      SELECT id FROM item_types WHERE key IN ('profile_background','profile_thumbnail_background')
  )
  AND value ? 'image_url';

UPDATE items SET value = jsonb_build_object(
    'statKey', value->>'stat_key',
    'label', name,
    'format', jsonb_build_object('type', 'integer')
)
WHERE type_id = (SELECT id FROM item_types WHERE key = 'statistic')
  AND value ? 'stat_key';
