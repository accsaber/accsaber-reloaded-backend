ALTER TABLE user_item_links DROP CONSTRAINT user_item_links_source_check;
ALTER TABLE user_item_links ADD CONSTRAINT user_item_links_source_check
    CHECK (source IN ('milestone', 'milestone_set', 'campaign_milestone', 'level', 'trade', 'manual', 'crate_drop'));

INSERT INTO item_types (key, name, description)
VALUES ('crate', 'Crate', 'Container that drops one item from a weighted pool when opened.')
ON CONFLICT (key) DO NOTHING;

CREATE TABLE crate_contents (
    crate_item_id  UUID         NOT NULL REFERENCES items(id) ON DELETE CASCADE,
    reward_item_id UUID         NOT NULL REFERENCES items(id) ON DELETE RESTRICT,
    drop_weight    INTEGER      NOT NULL CHECK (drop_weight > 0),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (crate_item_id, reward_item_id),
    CONSTRAINT crate_not_self CHECK (crate_item_id <> reward_item_id)
);

CREATE INDEX idx_crate_contents_crate  ON crate_contents(crate_item_id);
CREATE INDEX idx_crate_contents_reward ON crate_contents(reward_item_id);

CREATE TABLE user_crate_opens (
    id               UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id          BIGINT       NOT NULL REFERENCES users(id),
    crate_item_id    UUID         NOT NULL REFERENCES items(id),
    consumed_link_id UUID         NOT NULL,
    reward_link_id   UUID         REFERENCES user_item_links(id) ON DELETE SET NULL,
    reward_item_id   UUID         NOT NULL REFERENCES items(id),
    roll_seed        BIGINT       NOT NULL,
    rolled_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_crate_opens_user   ON user_crate_opens(user_id, rolled_at DESC);
CREATE INDEX idx_user_crate_opens_crate  ON user_crate_opens(crate_item_id, rolled_at DESC);
CREATE INDEX idx_user_crate_opens_reward ON user_crate_opens(reward_item_id, rolled_at DESC);
