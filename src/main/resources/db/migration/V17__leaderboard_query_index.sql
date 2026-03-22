CREATE INDEX idx_ucs_category_ranking
    ON user_category_statistics(category_id, ranking ASC)
    WHERE active = true;
