DROP MATERIALIZED VIEW IF EXISTS milestone_completion_stats;

CREATE MATERIALIZED VIEW milestone_completion_stats AS
SELECT
    m.id AS milestone_id,
    COUNT(uml.id) FILTER (WHERE uml.completed = true) AS completions,
    (SELECT COUNT(*) FROM users WHERE active = true AND banned = false) AS total_players,
    CASE WHEN (SELECT COUNT(*) FROM users WHERE active = true AND banned = false) = 0 THEN 0
         ELSE ROUND(COUNT(uml.id) FILTER (WHERE uml.completed = true) * 100.0 /
              (SELECT COUNT(*) FROM users WHERE active = true AND banned = false), 6)
    END AS completion_percentage
FROM milestones m
LEFT JOIN user_milestone_links uml ON uml.milestone_id = m.id
WHERE m.active = true
GROUP BY m.id;

CREATE UNIQUE INDEX idx_milestone_completion_stats_id ON milestone_completion_stats(milestone_id);
