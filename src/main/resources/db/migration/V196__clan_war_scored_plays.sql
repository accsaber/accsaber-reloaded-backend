CREATE TABLE clan_war_scored_plays (
    war_id    UUID NOT NULL REFERENCES clan_wars(id),
    score_id  UUID NOT NULL REFERENCES scores(id),
    PRIMARY KEY (war_id, score_id)
);

INSERT INTO clan_war_scored_plays (war_id, score_id)
SELECT DISTINCT w.id, s.id
FROM clan_wars w
JOIN clan_war_participants p ON p.war_id = w.id
JOIN clan_war_pool pool ON pool.war_id = w.id
JOIN scores s ON s.user_id = p.user_id AND s.map_difficulty_id = pool.map_difficulty_id
WHERE w.status = 'active'
  AND COALESCE(s.time_set, s.created_at) >= w.starts_at;
