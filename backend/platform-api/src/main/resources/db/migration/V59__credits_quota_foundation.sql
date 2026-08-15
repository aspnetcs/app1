-- Credits quota foundation: accounts, settlement snapshots, model pricing
-- Part of the Credits-based model quota system

-- 1. Model pricing fields on ai_model_metadata
ALTER TABLE IF EXISTS ai_model_metadata
    ADD COLUMN IF NOT EXISTS billing_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS request_price_usd NUMERIC(12,6),
    ADD COLUMN IF NOT EXISTS prompt_price_usd NUMERIC(12,6),
    ADD COLUMN IF NOT EXISTS input_price_usd_per_1m NUMERIC(12,6),
    ADD COLUMN IF NOT EXISTS output_price_usd_per_1m NUMERIC(12,6);

-- 2. Credit accounts table (independent from users table)
CREATE TABLE IF NOT EXISTS credit_accounts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID,
    guest_id        VARCHAR(64),
    role_snapshot   VARCHAR(20)  NOT NULL DEFAULT 'user',
    credit_balance  BIGINT       NOT NULL DEFAULT 0,
    credit_used     BIGINT       NOT NULL DEFAULT 0,
    manual_credit_adjustment BIGINT NOT NULL DEFAULT 0,
    period_credits  BIGINT       NOT NULL DEFAULT 0,
    period_type     VARCHAR(20)  NOT NULL DEFAULT 'monthly',
    period_start_at TIMESTAMPTZ,
    period_end_at   TIMESTAMPTZ,
    last_reset_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_credit_accounts_user_id ON credit_accounts (user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_credit_accounts_guest_id ON credit_accounts (guest_id) WHERE guest_id IS NOT NULL;

-- 3. Credit settlement snapshots (immutable billing ledger)
CREATE TABLE IF NOT EXISTS credit_settlement_snapshots (
    id                              BIGSERIAL PRIMARY KEY,
    request_id                      VARCHAR(64),
    user_id                         UUID,
    guest_id                        VARCHAR(64),
    role_snapshot                   VARCHAR(20),
    model_id                        VARCHAR(150),
    model_name_snapshot             VARCHAR(150),
    free_mode_snapshot              BOOLEAN      NOT NULL DEFAULT FALSE,
    billing_enabled_snapshot        BOOLEAN      NOT NULL DEFAULT FALSE,
    credits_per_usd_snapshot        INTEGER      NOT NULL DEFAULT 1000,
    request_price_usd_snapshot      NUMERIC(12,6),
    prompt_price_usd_snapshot       NUMERIC(12,6),
    input_price_usd_per_1m_snapshot NUMERIC(12,6),
    output_price_usd_per_1m_snapshot NUMERIC(12,6),
    request_count                   INTEGER      NOT NULL DEFAULT 0,
    prompt_count                    INTEGER      NOT NULL DEFAULT 0,
    input_tokens                    INTEGER      NOT NULL DEFAULT 0,
    output_tokens                   INTEGER      NOT NULL DEFAULT 0,
    request_cost_usd                NUMERIC(12,6),
    prompt_cost_usd                 NUMERIC(12,6),
    input_cost_usd                  NUMERIC(12,6),
    output_cost_usd                 NUMERIC(12,6),
    reserved_credits                BIGINT       NOT NULL DEFAULT 0,
    settled_credits                 BIGINT       NOT NULL DEFAULT 0,
    refunded_credits                BIGINT       NOT NULL DEFAULT 0,
    settlement_status               VARCHAR(20)  NOT NULL DEFAULT 'pending',
    failure_reason                  TEXT,
    started_at                      TIMESTAMPTZ,
    settled_at                      TIMESTAMPTZ,
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_css_user_id_created ON credit_settlement_snapshots (user_id, created_at DESC) WHERE user_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_css_guest_id_created ON credit_settlement_snapshots (guest_id, created_at DESC) WHERE guest_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_css_request_id ON credit_settlement_snapshots (request_id) WHERE request_id IS NOT NULL;

-- 4. Link usage log to settlement snapshot
ALTER TABLE IF EXISTS ai_usage_log
    ADD COLUMN IF NOT EXISTS settlement_snapshot_id BIGINT;

-- 5. Seed default credits system config
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'sys_config') THEN
        INSERT INTO sys_config (config_key, config_value, updated_at)
        VALUES ('credits.system.enabled', 'false', NOW())
        ON CONFLICT (config_key) DO NOTHING;

        INSERT INTO sys_config (config_key, config_value, updated_at)
        VALUES ('credits.per.usd', '1000', NOW())
        ON CONFLICT (config_key) DO NOTHING;

        INSERT INTO sys_config (config_key, config_value, updated_at)
        VALUES ('credits.free.mode.enabled', 'false', NOW())
        ON CONFLICT (config_key) DO NOTHING;
    END IF;
END $$;
