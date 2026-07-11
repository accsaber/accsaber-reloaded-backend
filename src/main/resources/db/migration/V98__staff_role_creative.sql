ALTER TABLE staff_users DROP CONSTRAINT staff_users_role_check;
ALTER TABLE staff_users ADD CONSTRAINT staff_users_role_check
    CHECK (role IN ('moderator', 'ranking', 'ranking_head', 'developer', 'campaign_curator', 'creative', 'admin'));
