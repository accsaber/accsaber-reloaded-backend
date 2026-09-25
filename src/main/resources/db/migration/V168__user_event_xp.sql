ALTER TABLE users
    ADD COLUMN event_xp DOUBLE PRECISION NOT NULL DEFAULT 0;

WITH sources AS (
    SELECT um.user_id, CAST(um.xp_reward AS DOUBLE PRECISION) AS xp
    FROM user_missions um
    JOIN mission_templates mt ON mt.id = um.template_id
    WHERE um.status = 'completed'
      AND um.user_id IS NOT NULL
      AND mt.event_id IS NOT NULL
    UNION ALL
    SELECT cmc.user_id, CAST(cm.xp_reward AS DOUBLE PRECISION)
    FROM community_mission_contributions cmc
    JOIN user_missions cm ON cm.id = cmc.user_mission_id
    JOIN mission_templates mt ON mt.id = cm.template_id
    WHERE cmc.rewarded_at IS NOT NULL
      AND mt.event_id IS NOT NULL
    UNION ALL
    SELECT uep.user_id, CAST(uep.bonus_xp AS DOUBLE PRECISION)
    FROM user_event_profiles uep
    WHERE uep.bonus_awarded_at IS NOT NULL
),
totals AS (
    SELECT user_id, SUM(xp) AS event_xp
    FROM sources
    GROUP BY user_id
    HAVING SUM(xp) <> 0
)
UPDATE users u
SET event_xp = t.event_xp,
    mission_xp = u.mission_xp - t.event_xp,
    updated_at = NOW()
FROM totals t
WHERE u.id = t.user_id;
