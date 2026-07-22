ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('trade_offer', 'trade_accepted', 'trade_declined',
                    'market_sold', 'market_bid', 'market_outbid', 'market_won',
                    'item_earned', 'server'));
