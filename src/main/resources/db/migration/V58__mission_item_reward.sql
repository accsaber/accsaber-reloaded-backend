ALTER TABLE user_missions RENAME COLUMN crate_reward_id TO item_reward_id;
ALTER TABLE user_missions RENAME COLUMN crate_awarded   TO item_awarded;

ALTER TABLE mission_templates
    ADD COLUMN awards_item_id UUID REFERENCES items(id) ON DELETE SET NULL;
