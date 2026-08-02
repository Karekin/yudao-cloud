ALTER TABLE cloudmold_finance_posting_rule
    DROP CHECK ck_finance_posting_rule_source,
    ADD CONSTRAINT ck_finance_posting_rule_source
        CHECK (source_type IN ('QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT','SUPPLIER_RETURN'));

ALTER TABLE cloudmold_finance_journal_entry
    DROP CHECK ck_finance_journal_source,
    ADD CONSTRAINT ck_finance_journal_source CHECK (source_type IN
        ('SETTLEMENT_BATCH','QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT','SUPPLIER_RETURN','JOURNAL_REVERSAL'));

ALTER TABLE cloudmold_finance_ap_application
    DROP CHECK ck_finance_ap_application_source,
    ADD CONSTRAINT ck_finance_ap_application_source
        CHECK (source_type IN ('PAYMENT_SETTLEMENT','PAYMENT_RETURN','CREDIT_NOTE','JOURNAL_REVERSAL','SUPPLIER_RETURN'));

ALTER TABLE cloudmold_finance_journal_source_effect
    DROP CHECK ck_finance_journal_effect_type,
    ADD CONSTRAINT ck_finance_journal_effect_type
        CHECK (effect_type IN ('QUALIFIED_RECEIPT','INVOICE_POSTING','PAYMENT_SETTLEMENT','SUPPLIER_RETURN','REVERSAL'));

CREATE TABLE IF NOT EXISTS cloudmold_finance_supplier_return_reversal_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status INT NOT NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_finance_supplier_return_reversal_op (tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_finance_supplier_debit_adjustment (
    supplier_debit_adjustment_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    adjustment_code VARCHAR(64) NOT NULL,
    supplier_return_id VARCHAR(128) NOT NULL,
    supplier_return_version BIGINT NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    accounting_period_id VARCHAR(128) NOT NULL,
    accounting_date DATE NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    ap_reversal_amount_minor BIGINT NOT NULL,
    tax_reversal_amount_minor BIGINT NOT NULL,
    valuation_reversal_amount_minor BIGINT NOT NULL,
    purchase_price_variance_amount_minor BIGINT NOT NULL,
    posting_rule_id VARCHAR(128) NOT NULL,
    posting_rule_version BIGINT NOT NULL,
    prepared_journal_entry_id VARCHAR(128) NOT NULL,
    reversal_evidence_sha256 CHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    prepared_by_principal_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (supplier_debit_adjustment_id),
    UNIQUE KEY uk_finance_supplier_debit_adjustment_tenant_id (tenant_id, supplier_debit_adjustment_id),
    UNIQUE KEY uk_finance_supplier_debit_adjustment_code (tenant_id, adjustment_code),
    UNIQUE KEY uk_finance_supplier_debit_adjustment_return (tenant_id, supplier_return_id, supplier_return_version),
    CONSTRAINT fk_finance_supplier_debit_adjustment_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT fk_finance_supplier_debit_adjustment_period FOREIGN KEY (tenant_id, accounting_period_id)
        REFERENCES cloudmold_finance_accounting_period (tenant_id, period_id),
    CONSTRAINT fk_finance_supplier_debit_adjustment_rule FOREIGN KEY
        (tenant_id, posting_rule_id, posting_rule_version, ledger_id)
        REFERENCES cloudmold_finance_posting_rule (tenant_id, posting_rule_id, rule_version, ledger_id),
    CONSTRAINT fk_finance_supplier_debit_adjustment_journal FOREIGN KEY (tenant_id, prepared_journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT ck_finance_supplier_debit_adjustment_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_supplier_debit_adjustment_amounts CHECK (
        ap_reversal_amount_minor > 0
        AND tax_reversal_amount_minor >= 0
        AND valuation_reversal_amount_minor > 0
        AND status IN ('PREPARED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_finance_supplier_debit_adjustment_line (
    adjustment_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_debit_adjustment_id VARCHAR(128) NOT NULL,
    supplier_return_line_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    quality_disposition_id VARCHAR(128) NOT NULL,
    quality_disposition_version BIGINT NOT NULL,
    purchase_order_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    purchase_order_schedule_id VARCHAR(128) NOT NULL,
    valuation_layer_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    reversal_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    valuation_reversal_amount_minor BIGINT NOT NULL,
    net_reversal_amount_minor BIGINT NOT NULL,
    tax_reversal_amount_minor BIGINT NOT NULL,
    gross_reversal_amount_minor BIGINT NOT NULL,
    purchase_price_variance_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (adjustment_line_id),
    UNIQUE KEY uk_finance_supplier_debit_adjustment_line_return
        (tenant_id, supplier_debit_adjustment_id, supplier_return_line_id),
    CONSTRAINT fk_finance_supplier_debit_adjustment_line_header FOREIGN KEY
        (tenant_id, supplier_debit_adjustment_id)
        REFERENCES cloudmold_finance_supplier_debit_adjustment
        (tenant_id, supplier_debit_adjustment_id),
    CONSTRAINT fk_finance_supplier_debit_adjustment_line_layer FOREIGN KEY
        (tenant_id, valuation_layer_id)
        REFERENCES cloudmold_finance_inventory_valuation_layer
        (tenant_id, valuation_layer_id),
    CONSTRAINT ck_finance_supplier_debit_adjustment_line_qty CHECK (reversal_quantity > 0),
    CONSTRAINT ck_finance_supplier_debit_adjustment_line_amounts CHECK (
        valuation_reversal_amount_minor > 0
        AND gross_reversal_amount_minor > 0
        AND tax_reversal_amount_minor >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_finance_supplier_return_ap_reversal (
    ap_reversal_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_debit_adjustment_id VARCHAR(128) NOT NULL,
    supplier_return_line_id VARCHAR(128) NOT NULL,
    supplier_invoice_id VARCHAR(128) NOT NULL,
    invoice_line_id VARCHAR(128) NOT NULL,
    ap_open_item_id VARCHAR(128) NOT NULL,
    reversal_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    net_reversal_amount_minor BIGINT NOT NULL,
    tax_reversal_amount_minor BIGINT NOT NULL,
    gross_reversal_amount_minor BIGINT NOT NULL,
    journal_entry_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ap_reversal_id),
    UNIQUE KEY uk_finance_supplier_return_ap_reversal_src
        (tenant_id, supplier_debit_adjustment_id, supplier_return_line_id, ap_open_item_id),
    CONSTRAINT fk_finance_supplier_return_ap_reversal_header FOREIGN KEY
        (tenant_id, supplier_debit_adjustment_id)
        REFERENCES cloudmold_finance_supplier_debit_adjustment
        (tenant_id, supplier_debit_adjustment_id),
    CONSTRAINT fk_finance_supplier_return_ap_reversal_ap FOREIGN KEY
        (tenant_id, ap_open_item_id)
        REFERENCES cloudmold_finance_ap_open_item
        (tenant_id, ap_open_item_id),
    CONSTRAINT ck_finance_supplier_return_ap_reversal_qty CHECK (reversal_quantity > 0),
    CONSTRAINT ck_finance_supplier_return_ap_reversal_amount CHECK (
        net_reversal_amount_minor >= 0
        AND tax_reversal_amount_minor >= 0
        AND gross_reversal_amount_minor > 0
        AND gross_reversal_amount_minor = net_reversal_amount_minor + tax_reversal_amount_minor
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
