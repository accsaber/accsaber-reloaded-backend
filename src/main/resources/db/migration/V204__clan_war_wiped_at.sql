ALTER TABLE clan_wars ADD COLUMN wiped_at TIMESTAMPTZ;

UPDATE clan_wars w
SET wiped_at = t.wiped_at
FROM (
    SELECT war_id, MIN(last_break) AS wiped_at
    FROM (
        SELECT p.war_id, s.team, MAX(p.broken_at) AS last_break
        FROM clan_war_participants p
        JOIN clan_war_sides s ON s.war_id = p.war_id AND s.clan_id = p.clan_id
        JOIN clan_wars cw ON cw.id = p.war_id AND cw.status = 'active'
        WHERE p.left_at IS NULL AND s.left_at IS NULL AND NOT (s.stake > 0 AND s.stake_remaining <= 0)
        GROUP BY p.war_id, s.team
        HAVING bool_and(p.broken_at IS NOT NULL)
    ) teams
    GROUP BY war_id
) t
WHERE w.id = t.war_id;
