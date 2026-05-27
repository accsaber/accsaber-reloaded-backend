CREATE TABLE supporter_tiers (
    tier_key           VARCHAR(32)  PRIMARY KEY,
    display_name       VARCHAR(64)  NOT NULL,
    monthly_cost_cents INTEGER      NOT NULL CHECK (monthly_cost_cents > 0),
    sort_order         INTEGER      NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO supporter_tiers (tier_key, display_name, monthly_cost_cents, sort_order) VALUES
    ('bronze', 'Bronze',  399, 1),
    ('silver', 'Silver',  799, 2),
    ('gold',   'Gold',   1099, 3);

CREATE TRIGGER trg_supporter_tiers_updated_at
    BEFORE UPDATE ON supporter_tiers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE supporter_accounts (
    user_id                  BIGINT       PRIMARY KEY REFERENCES users(id),
    balance_cents            INTEGER      NOT NULL DEFAULT 0 CHECK (balance_cents >= 0),
    current_tier             VARCHAR(32)           REFERENCES supporter_tiers(tier_key) ON DELETE RESTRICT,
    tier_started_at          TIMESTAMPTZ,
    last_debit_at            TIMESTAMPTZ,
    lifetime_supported_cents BIGINT       NOT NULL DEFAULT 0 CHECK (lifetime_supported_cents >= 0),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT supporter_account_tier_consistency CHECK (
        (current_tier IS NULL AND tier_started_at IS NULL)
        OR (current_tier IS NOT NULL AND tier_started_at IS NOT NULL)
    )
);

CREATE INDEX idx_supporter_accounts_current_tier
    ON supporter_accounts(current_tier)
    WHERE current_tier IS NOT NULL;

CREATE INDEX idx_supporter_accounts_lifetime
    ON supporter_accounts(lifetime_supported_cents DESC)
    WHERE lifetime_supported_cents > 0;

CREATE TRIGGER trg_supporter_accounts_updated_at
    BEFORE UPDATE ON supporter_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE kofi_events (
    id                    UUID         PRIMARY KEY DEFAULT uuidv7(),
    kofi_transaction_id   VARCHAR(128) NOT NULL UNIQUE,
    type                  VARCHAR(32)  NOT NULL,
    email                 VARCHAR(255),
    from_name             VARCHAR(128),
    amount_cents          INTEGER      NOT NULL CHECK (amount_cents >= 0),
    currency              VARCHAR(8)   NOT NULL,
    tier_name             VARCHAR(64),
    is_subscription       BOOLEAN      NOT NULL DEFAULT false,
    is_first_subscription BOOLEAN      NOT NULL DEFAULT false,
    received_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    payload               JSONB        NOT NULL,
    claimed_user_id       BIGINT                REFERENCES users(id) ON DELETE SET NULL,
    claimed_at            TIMESTAMPTZ,
    claim_source          VARCHAR(32),
    CONSTRAINT kofi_event_claim_consistency CHECK (
        (claimed_user_id IS NULL AND claimed_at IS NULL AND claim_source IS NULL)
        OR (claimed_user_id IS NOT NULL AND claimed_at IS NOT NULL AND claim_source IS NOT NULL)
    )
);

CREATE INDEX idx_kofi_events_unclaimed
    ON kofi_events(received_at DESC)
    WHERE claimed_user_id IS NULL;

CREATE INDEX idx_kofi_events_email_unclaimed
    ON kofi_events(LOWER(email))
    WHERE claimed_user_id IS NULL AND email IS NOT NULL;

CREATE INDEX idx_kofi_events_claimed_user
    ON kofi_events(claimed_user_id, claimed_at DESC)
    WHERE claimed_user_id IS NOT NULL;

ALTER TABLE user_item_links DROP CONSTRAINT user_item_links_source_check;
ALTER TABLE user_item_links ADD CONSTRAINT user_item_links_source_check
    CHECK (source IN ('milestone', 'milestone_set', 'campaign_milestone', 'level', 'trade', 'manual', 'crate_drop', 'supporter_tier'));

INSERT INTO items (type_id, name, description, value, rarity, tradeable, visible) VALUES
((SELECT id FROM item_types WHERE key = 'profile_border_shape'),
 'Supporter Frame',
 'Plus-signs climb the rim from the bottom corners and pop into sparkles at the top of this pixel frame. Granted to Ko-fi supporters.',
 '{"viewBox":"0 0 100 100","renderMode":"pixel","pixelSize":4,"motif":"plus_climb","sparkles":{"enabled":true,"perSecond":0.8,"sizePx":1,"fadeMs":600},"glisten":{"enabled":true,"intervalMs":5000,"durationMs":800,"bandPctOfDiagonal":30},"states":[{"atMs":0}]}'::jsonb,
 'legendary', false, true),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Bronze Supporter Color',
 'A glistening pixel-art bronze. Awarded to Bronze supporters.',
 '{"states":[{"atMs":0,"fill":{"type":"pixel_metal","base":"#c47a3a","highlight":"#f0c47a","shadow":"#6f3f1c"}}]}'::jsonb,
 'epic', false, true),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Silver Supporter Color',
 'A glistening pixel-art silver. Awarded to Silver supporters.',
 '{"states":[{"atMs":0,"fill":{"type":"pixel_metal","base":"#b8bcc5","highlight":"#e1e3e9","shadow":"#7c818a"}}]}'::jsonb,
 'legendary', false, true),

((SELECT id FROM item_types WHERE key = 'profile_border_color'),
 'Golden Supporter Color',
 'A glistening pixel-art gold. Awarded to Gold supporters.',
 '{"states":[{"atMs":0,"fill":{"type":"pixel_metal","base":"#d4b042","highlight":"#f4e08c","shadow":"#876a14"}}]}'::jsonb,
 'mythic', false, true),

((SELECT id FROM item_types WHERE key = 'title'),
 'Bronze Helper',
 'Carried the lights on. Awarded to Bronze supporters.',
 '{"text":"Bronze Helper","font":"pixel_8bit","states":[{"atMs":0,"color":"#c47a3a","glisten":{"enabled":true,"highlight":"#f0c47a","intervalMs":5000,"durationMs":800}}]}'::jsonb,
 'epic', false, true),

((SELECT id FROM item_types WHERE key = 'title'),
 'Silver Hero',
 'Carried the lights on. Awarded to Silver supporters.',
 '{"text":"Silver Hero","font":"pixel_8bit","states":[{"atMs":0,"color":"#b8bcc5","glisten":{"enabled":true,"highlight":"#e1e3e9","intervalMs":5000,"durationMs":800}}]}'::jsonb,
 'legendary', false, true),

((SELECT id FROM item_types WHERE key = 'title'),
 'Golden Legend',
 'Carried the lights on. Awarded to Gold supporters.',
 '{"text":"Golden Legend","font":"pixel_8bit","states":[{"atMs":0,"color":"#d4b042","glisten":{"enabled":true,"highlight":"#f4e08c","intervalMs":5000,"durationMs":800}}]}'::jsonb,
 'mythic', false, true)
ON CONFLICT (type_id, name) DO NOTHING;
