ALTER TABLE items
    ALTER COLUMN worth TYPE BIGINT USING ROUND(worth)::BIGINT;

ALTER TABLE users
    ALTER COLUMN item_essence DROP DEFAULT,
    ALTER COLUMN item_essence TYPE BIGINT USING ROUND(item_essence)::BIGINT,
    ALTER COLUMN item_essence SET DEFAULT 0;

ALTER TABLE user_item_disintegrations
    ALTER COLUMN essence_gained TYPE BIGINT USING ROUND(essence_gained)::BIGINT;

ALTER TABLE items
    ADD CONSTRAINT items_worth_non_negative CHECK (worth IS NULL OR worth >= 0);

ALTER TABLE users
    ADD COLUMN reserved_essence BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT users_item_essence_non_negative CHECK (item_essence >= 0),
    ADD CONSTRAINT users_reserved_essence_non_negative CHECK (reserved_essence >= 0);

ALTER TABLE user_item_links
    ADD COLUMN escrowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_item_links DROP CONSTRAINT user_item_links_source_check;
ALTER TABLE user_item_links ADD CONSTRAINT user_item_links_source_check
    CHECK (source IN ('milestone', 'milestone_set', 'campaign_milestone', 'campaign_difficulty',
                      'campaign_completion', 'level', 'trade', 'manual', 'crate_drop',
                      'supporter_tier', 'market'));

CREATE INDEX idx_user_item_links_user_available
    ON user_item_links (user_id) WHERE escrowed = FALSE;

ALTER TABLE user_item_trades
    ADD COLUMN offered_essence   BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN requested_essence BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT trade_offered_essence_non_negative   CHECK (offered_essence >= 0),
    ADD CONSTRAINT trade_requested_essence_non_negative CHECK (requested_essence >= 0);

CREATE TABLE market_listings (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    seller_id         BIGINT       NOT NULL REFERENCES users(id),
    item_id           UUID         NOT NULL REFERENCES items(id),
    user_item_link_id UUID         REFERENCES user_item_links(id) ON DELETE SET NULL,
    title             TEXT         NOT NULL,
    quantity          BIGINT       NOT NULL CHECK (quantity > 0),
    starting_bid      BIGINT,
    buyout_price      BIGINT,
    min_increment     BIGINT       NOT NULL DEFAULT 1 CHECK (min_increment > 0),
    current_bid       BIGINT,
    current_bidder_id BIGINT       REFERENCES users(id),
    winner_id         BIGINT       REFERENCES users(id),
    final_price       BIGINT,
    status            TEXT         NOT NULL DEFAULT 'active'
                                   CHECK (status IN ('active', 'sold', 'expired', 'cancelled')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    ends_at           TIMESTAMPTZ  NOT NULL,
    settled_at        TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT market_listing_priced
        CHECK (starting_bid IS NOT NULL OR buyout_price IS NOT NULL),
    CONSTRAINT market_listing_starting_bid_positive
        CHECK (starting_bid IS NULL OR starting_bid > 0),
    CONSTRAINT market_listing_buyout_positive
        CHECK (buyout_price IS NULL OR buyout_price > 0),
    CONSTRAINT market_listing_buyout_not_below_start
        CHECK (starting_bid IS NULL OR buyout_price IS NULL OR buyout_price >= starting_bid),
    CONSTRAINT market_listing_bid_pair
        CHECK ((current_bid IS NULL) = (current_bidder_id IS NULL)),
    CONSTRAINT market_listing_seller_not_bidder
        CHECK (current_bidder_id IS NULL OR current_bidder_id <> seller_id)
);

CREATE UNIQUE INDEX uq_market_listings_active_link
    ON market_listings (user_item_link_id) WHERE status = 'active';

CREATE INDEX idx_market_listings_settlement
    ON market_listings (ends_at) WHERE status = 'active';

CREATE INDEX idx_market_listings_browse
    ON market_listings (status, ends_at);

CREATE INDEX idx_market_listings_item
    ON market_listings (item_id, status);

CREATE INDEX idx_market_listings_seller
    ON market_listings (seller_id, status);

CREATE INDEX idx_market_listings_bidder
    ON market_listings (current_bidder_id) WHERE current_bidder_id IS NOT NULL;

CREATE TRIGGER trg_market_listings_updated_at
    BEFORE UPDATE ON market_listings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE market_bids (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    listing_id UUID        NOT NULL REFERENCES market_listings(id) ON DELETE CASCADE,
    bidder_id  BIGINT      NOT NULL REFERENCES users(id),
    amount     BIGINT      NOT NULL CHECK (amount > 0),
    buyout     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_market_bids_listing ON market_bids (listing_id, amount DESC);
CREATE INDEX idx_market_bids_bidder  ON market_bids (bidder_id, created_at DESC);

CREATE TABLE essence_transactions (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    amount     BIGINT      NOT NULL,
    reason     TEXT        NOT NULL
                           CHECK (reason IN ('disintegration', 'bid_reserve', 'bid_refund',
                                             'purchase', 'sale', 'trade_reserve', 'trade_refund',
                                             'trade_payment', 'trade_receipt', 'admin_adjustment')),
    ref_id     UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_essence_transactions_user ON essence_transactions (user_id, created_at DESC);
CREATE INDEX idx_essence_transactions_ref  ON essence_transactions (ref_id) WHERE ref_id IS NOT NULL;
