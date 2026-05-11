CREATE TABLE user_item_trade_items (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    trade_id          UUID         NOT NULL REFERENCES user_item_trades(id) ON DELETE CASCADE,
    user_item_link_id UUID         NOT NULL REFERENCES user_item_links(id) ON DELETE CASCADE,
    side              TEXT         NOT NULL CHECK (side IN ('offered', 'requested')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_item_trade_items_trade_link UNIQUE (trade_id, user_item_link_id)
);

CREATE INDEX idx_user_item_trade_items_link  ON user_item_trade_items (user_item_link_id);
CREATE INDEX idx_user_item_trade_items_trade ON user_item_trade_items (trade_id);

INSERT INTO user_item_trade_items (trade_id, user_item_link_id, side)
SELECT id, user_item_link_id, 'offered'
FROM user_item_trades;

DROP INDEX IF EXISTS uq_user_item_trades_pending_link;
DROP INDEX IF EXISTS idx_user_item_trades_link;

ALTER TABLE user_item_trades DROP COLUMN user_item_link_id;

CREATE OR REPLACE FUNCTION enforce_one_pending_trade_per_link() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_item_trade_items ti
        JOIN user_item_trades t ON t.id = ti.trade_id
        WHERE ti.user_item_link_id = NEW.user_item_link_id
          AND ti.trade_id <> NEW.trade_id
          AND t.status = 'pending'
    ) THEN
        RAISE EXCEPTION 'item link % is already in another pending trade', NEW.user_item_link_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_item_trade_items_no_double_pending
    BEFORE INSERT OR UPDATE ON user_item_trade_items
    FOR EACH ROW EXECUTE FUNCTION enforce_one_pending_trade_per_link();
