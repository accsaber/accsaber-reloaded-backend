UPDATE items SET
    description = 'Pixel-art frame with a tilted metal sunset ramp. Hearts climb the rim and burst into sparkles at the top corners. Granted to Ko-fi supporters.',
    value = '{"viewBox":"0 0 100 100","renderMode":"pixel","pixelSize":4,"motif":"heart_climb","frame":{"thicknessProportional":0.057,"thicknessMinPx":4,"thicknessMaxPx":8,"cornerRadiusProportional":0.13,"cornerRadiusMinPx":6,"outlineWidthPx":1,"ramp":{"angleDeg":168,"bands":[{"upToPct":6,"stop":"apexHighlight"},{"upToPct":15,"stop":"highlight"},{"upToPct":28,"stop":"midHighlight"},{"upToPct":45,"stop":"base"},{"upToPct":60,"stop":"midShadow"},{"upToPct":78,"stop":"shadow"},{"upToPct":92,"stop":"deepShadow"},{"upToPct":100,"stop":"outline"}]},"streaks":{"angleDeg":11,"blendMode":"overlay","pattern":[{"stop":null,"lengthPx":11},{"stop":"apexHighlight","lengthPx":1},{"stop":null,"lengthPx":15},{"stop":"highlight","lengthPx":1},{"stop":null,"lengthPx":15}]}},"paletteDerivation":{"outline":{"fn":"darken","of":"shadow","amount":0.5},"deepShadow":{"fn":"darken","of":"shadow","amount":0.35},"midShadow":{"fn":"lerp","from":"shadow","to":"base","at":0.45},"midHighlight":{"fn":"lerp","from":"base","to":"highlight","at":0.55},"apexHighlight":{"fn":"lighten","of":"highlight","amount":0.45}},"sparkles":{"enabled":true,"perSecond":0.8,"sizePx":1,"fadeMs":600},"glisten":{"enabled":true,"intervalMs":5000,"durationMs":800,"bandPctOfDiagonal":30},"states":[{"atMs":0}]}'::jsonb
WHERE name = 'Supporter Frame';

UPDATE items SET
    value = '{"states":[{"atMs":0,"fill":{"type":"pixel_metal","shadow":"#5a1d0a","base":"#d96a2c","highlight":"#ffd6a8"}}]}'::jsonb
WHERE name = 'Bronze Supporter Color';

UPDATE items SET
    value = '{"states":[{"atMs":0,"fill":{"type":"pixel_metal","shadow":"#252948","base":"#8a96b0","highlight":"#f5efe2"}}]}'::jsonb
WHERE name = 'Silver Supporter Color';

UPDATE items SET
    value = '{"states":[{"atMs":0,"fill":{"type":"pixel_metal","shadow":"#6a2f0a","base":"#e8b020","highlight":"#fff0b0"}}]}'::jsonb
WHERE name = 'Golden Supporter Color';

UPDATE items SET
    description = 'You showed up. Awarded to Bronze supporters.',
    value = '{"text":"Bronze Helper","font":"pixel_8bit","states":[{"atMs":0,"color":"#d96a2c","glisten":{"enabled":true,"highlight":"#ffd6a8","intervalMs":5000,"durationMs":800}}]}'::jsonb
WHERE name = 'Bronze Helper';

UPDATE items SET
    description = 'You went a step further. Awarded to Silver supporters.',
    value = '{"text":"Silver Hero","font":"pixel_8bit","states":[{"atMs":0,"color":"#8a96b0","glisten":{"enabled":true,"highlight":"#f5efe2","intervalMs":5000,"durationMs":800}}]}'::jsonb
WHERE name = 'Silver Hero';

UPDATE items SET
    description = 'Carried the lights on. Awarded to Gold supporters.',
    value = '{"text":"Golden Legend","font":"pixel_8bit","states":[{"atMs":0,"color":"#e8b020","glisten":{"enabled":true,"highlight":"#fff0b0","intervalMs":5000,"durationMs":800}}]}'::jsonb
WHERE name = 'Golden Legend';
