ALTER TABLE cloudmold_promotion_advertising_ledger
    ADD COLUMN revenue_type VARCHAR(32) NULL
        COMMENT 'Controlled platform revenue component; required when entry_type = REVENUE'
        AFTER charge_model,
    ADD CONSTRAINT ck_promotion_ad_ledger_revenue_type
        CHECK (
            revenue_type IS NULL
            OR revenue_type IN ('ADVERTISING', 'COMMISSION', 'FULFILLMENT_SERVICE', 'PAYMENT_SERVICE',
                                'OTHER_PLATFORM_REVENUE')
        );
