INSERT INTO clan_war_reward_items (item_id, quantity, top_contributors)
SELECT i.id, 1, NULL
FROM items i
JOIN item_types t ON t.id = i.type_id AND t.key = 'crate'
WHERE i.active AND i.value ->> 'grant' = 'active_crate'
  AND NOT EXISTS (SELECT 1 FROM clan_war_reward_items r WHERE r.item_id = i.id AND r.active)
ORDER BY i.created_at
LIMIT 1;
