ALTER TABLE market_listings
    ALTER COLUMN ends_at DROP NOT NULL,
    ADD COLUMN description TEXT,
    ADD CONSTRAINT market_listing_endless_is_buyout_only
        CHECK (ends_at IS NOT NULL OR starting_bid IS NULL);

DROP INDEX IF EXISTS idx_market_listings_settlement;

CREATE INDEX idx_market_listings_settlement
    ON market_listings (ends_at) WHERE status = 'active' AND ends_at IS NOT NULL;
