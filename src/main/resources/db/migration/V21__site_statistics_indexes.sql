CREATE INDEX idx_scores_improvements ON scores(user_id, map_difficulty_id)
    WHERE active = false AND supersedes_reason = 'Score improved';

CREATE INDEX idx_scores_difficulty_active_time ON scores(map_difficulty_id, time_set DESC)
    WHERE active = true;

CREATE INDEX idx_scores_user_active_time ON scores(user_id, time_set DESC)
    WHERE active = true;

CREATE INDEX idx_scores_time_set ON scores(time_set);

CREATE INDEX idx_scores_user_hmd_time ON scores(user_id, time_set DESC)
    WHERE active = true AND hmd IS NOT NULL AND hmd != '' AND hmd != '0';

CREATE INDEX idx_milestone_links_completed ON user_milestone_links(user_id)
    WHERE completed = true;
