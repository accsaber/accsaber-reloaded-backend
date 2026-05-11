CREATE TABLE user_item_link_modifiers (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_item_link_id UUID         NOT NULL REFERENCES user_item_links(id) ON DELETE CASCADE,
    modifier_id       UUID         NOT NULL REFERENCES item_modifiers(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_item_link_modifiers UNIQUE (user_item_link_id, modifier_id)
);

CREATE INDEX idx_user_item_link_modifiers_link     ON user_item_link_modifiers(user_item_link_id);
CREATE INDEX idx_user_item_link_modifiers_modifier ON user_item_link_modifiers(modifier_id);

INSERT INTO user_item_link_modifiers (user_item_link_id, modifier_id)
SELECT id, modifier_id FROM user_item_links;

DROP INDEX IF EXISTS idx_user_item_links_modifier;
ALTER TABLE user_item_links DROP COLUMN modifier_id;

DROP INDEX IF EXISTS uq_user_item_links_idempotent;

ALTER TABLE items
    ADD COLUMN stackable BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN worth     NUMERIC;

ALTER TABLE user_item_links
    ADD COLUMN quantity BIGINT NOT NULL DEFAULT 1 CHECK (quantity > 0);

ALTER TABLE user_item_trade_items
    ADD COLUMN quantity BIGINT NOT NULL DEFAULT 1 CHECK (quantity > 0);
