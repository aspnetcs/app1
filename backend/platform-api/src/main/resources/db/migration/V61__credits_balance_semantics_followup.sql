-- Follow-up for Stage 4 credits accounting semantics.
-- Split reserved credits by source and migrate existing accounts so
-- periodic usage and durable balance no longer share the same spend bucket.

ALTER TABLE IF EXISTS credit_settlement_snapshots
    ADD COLUMN IF NOT EXISTS reserved_period_credits BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reserved_balance_credits BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'credit_settlement_snapshots') THEN
        UPDATE credit_settlement_snapshots
        SET reserved_period_credits = reserved_credits,
            reserved_balance_credits = 0
        WHERE reserved_credits > 0
          AND reserved_period_credits = 0
          AND reserved_balance_credits = 0;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'credit_accounts') THEN
        UPDATE credit_accounts
        SET credit_balance = CASE
                WHEN period_credits < 0 THEN GREATEST(0, credit_balance + manual_credit_adjustment)
                ELSE GREATEST(
                    0,
                    credit_balance + manual_credit_adjustment - GREATEST(credit_used - GREATEST(period_credits, 0), 0)
                )
            END,
            credit_used = CASE
                WHEN period_credits < 0 THEN credit_used
                ELSE LEAST(GREATEST(credit_used, 0), GREATEST(period_credits, 0))
            END
        WHERE manual_credit_adjustment <> 0
           OR credit_used <> 0
           OR credit_balance <> 0;
    END IF;
END $$;
