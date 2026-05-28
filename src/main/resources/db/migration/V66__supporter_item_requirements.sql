UPDATE items SET requirement = 'Support AccSaber on Ko-fi'           WHERE name = 'Supporter Frame';
UPDATE items SET requirement = 'Become a Bronze supporter on Ko-fi'  WHERE name IN ('Bronze Supporter Color', 'Bronze Helper');
UPDATE items SET requirement = 'Become a Silver supporter on Ko-fi'  WHERE name IN ('Silver Supporter Color', 'Silver Hero');
UPDATE items SET requirement = 'Become a Gold supporter on Ko-fi'    WHERE name IN ('Golden Supporter Color', 'Golden Legend');

UPDATE items SET description = 'Pixel-art frame with a tilted metal sunset ramp. Hearts climb the rim and burst into sparkles at the top corners.'
    WHERE name = 'Supporter Frame';
UPDATE items SET description = 'A glistening pixel-art bronze.' WHERE name = 'Bronze Supporter Color';
UPDATE items SET description = 'A glistening pixel-art silver.' WHERE name = 'Silver Supporter Color';
UPDATE items SET description = 'A glistening pixel-art gold.'   WHERE name = 'Golden Supporter Color';
UPDATE items SET description = 'You showed up.'           WHERE name = 'Bronze Helper';
UPDATE items SET description = 'You went a step further.' WHERE name = 'Silver Hero';
UPDATE items SET description = 'Carried the lights on.'   WHERE name = 'Golden Legend';
