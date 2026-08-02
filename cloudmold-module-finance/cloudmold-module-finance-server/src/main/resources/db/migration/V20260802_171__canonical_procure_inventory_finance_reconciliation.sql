CREATE TABLE cloudmold_finance_procure_inventory_reconciliation_run (
    run_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    run_code VARCHAR(64) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    reconciliation_policy_version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    line_count INT NOT NULL,
    matched_count INT NOT NULL,
    different_count INT NOT NULL,
    missing_count INT NOT NULL,
    uncomparable_count INT NOT NULL,
    requested_by_principal_id VARCHAR(128) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_finance_pifr_run_tenant_id (tenant_id, run_id),
    UNIQUE KEY uk_finance_pifr_run_code (tenant_id, run_code),
    KEY idx_finance_pifr_run_scope (tenant_id, legal_entity_id, currency_code, created_at),
    CONSTRAINT ck_finance_pifr_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_pifr_status CHECK (status='COMPLETED'),
    CONSTRAINT ck_finance_pifr_counts CHECK (
        line_count >= 0 AND matched_count >= 0 AND different_count >= 0
        AND missing_count >= 0 AND uncomparable_count >= 0
        AND line_count = matched_count + different_count + missing_count + uncomparable_count
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable procurement-inventory-finance reconciliation run';

CREATE TABLE cloudmold_finance_procure_inventory_reconciliation_watermark (
    watermark_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    domain_code VARCHAR(64) NOT NULL,
    source_table VARCHAR(128) NOT NULL,
    max_aggregate_version BIGINT NULL,
    max_observed_at DATETIME(6) NULL,
    record_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (watermark_id),
    UNIQUE KEY uk_finance_pifr_watermark_tenant_id (tenant_id, watermark_id),
    UNIQUE KEY uk_finance_pifr_watermark_domain (tenant_id, run_id, domain_code),
    CONSTRAINT fk_finance_pifr_watermark_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES cloudmold_finance_procure_inventory_reconciliation_run (tenant_id, run_id),
    CONSTRAINT ck_finance_pifr_watermark_record_count CHECK (record_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Frozen domain watermarks for a reconciliation run';

CREATE TABLE cloudmold_finance_procure_inventory_reconciliation_line (
    line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    line_type VARCHAR(32) NOT NULL,
    line_key VARCHAR(255) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    purchase_order_id VARCHAR(128) NULL,
    purchase_order_item_id VARCHAR(128) NULL,
    delivery_schedule_id VARCHAR(128) NULL,
    receipt_line_id VARCHAR(128) NULL,
    inventory_movement_id VARCHAR(128) NULL,
    supplier_return_id VARCHAR(128) NULL,
    supplier_return_line_id VARCHAR(128) NULL,
    supplier_invoice_id VARCHAR(128) NULL,
    supplier_invoice_line_id VARCHAR(128) NULL,
    ap_open_item_id VARCHAR(128) NULL,
    journal_entry_id VARCHAR(128) NULL,
    procurement_version BIGINT NULL,
    inventory_version BIGINT NULL,
    finance_version BIGINT NULL,
    procurement_quantity DECIMAL(24,6) NULL,
    inventory_quantity DECIMAL(24,6) NULL,
    finance_quantity DECIMAL(24,6) NULL,
    procurement_amount_minor BIGINT NULL,
    inventory_amount_minor BIGINT NULL,
    finance_amount_minor BIGINT NULL,
    quantity_difference DECIMAL(24,6) NULL,
    amount_difference_minor BIGINT NULL,
    primary_difference_code VARCHAR(64) NULL,
    responsibility_domain VARCHAR(16) NOT NULL,
    match_status VARCHAR(16) NOT NULL,
    difference_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (line_id),
    UNIQUE KEY uk_finance_pifr_line_tenant_id (tenant_id, line_id),
    UNIQUE KEY uk_finance_pifr_line_key (tenant_id, run_id, line_key),
    KEY idx_finance_pifr_line_status (tenant_id, run_id, match_status, line_type),
    KEY idx_finance_pifr_line_po (tenant_id, purchase_order_item_id),
    KEY idx_finance_pifr_line_receipt (tenant_id, receipt_line_id),
    KEY idx_finance_pifr_line_invoice (tenant_id, supplier_invoice_line_id),
    CONSTRAINT fk_finance_pifr_line_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES cloudmold_finance_procure_inventory_reconciliation_run (tenant_id, run_id),
    CONSTRAINT ck_finance_pifr_line_type CHECK (line_type IN ('QUALIFIED_RECEIPT','SUPPLIER_RETURN','SUPPLIER_INVOICE')),
    CONSTRAINT ck_finance_pifr_line_status CHECK (match_status IN ('MATCHED','DIFFERENT','MISSING','UNCOMPARABLE')),
    CONSTRAINT ck_finance_pifr_line_responsibility CHECK (responsibility_domain IN ('NONE','PROCUREMENT','INVENTORY','FINANCE')),
    CONSTRAINT ck_finance_pifr_line_difference_count CHECK (difference_count >= 0),
    CONSTRAINT ck_finance_pifr_line_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_pifr_line_primary_difference CHECK (
        (match_status='MATCHED' AND primary_difference_code IS NULL AND responsibility_domain='NONE')
        OR (match_status<>'MATCHED' AND primary_difference_code IS NOT NULL AND responsibility_domain<>'NONE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable reconciliation line by stable procurement/inventory/finance key';

CREATE TABLE cloudmold_finance_procure_inventory_reconciliation_difference (
    difference_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    line_id VARCHAR(128) NOT NULL,
    difference_code VARCHAR(64) NOT NULL,
    source_domain VARCHAR(16) NOT NULL,
    expected_value VARCHAR(255) NULL,
    actual_value VARCHAR(255) NULL,
    blocking BIT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (difference_id),
    UNIQUE KEY uk_finance_pifr_diff_tenant_id (tenant_id, difference_id),
    KEY idx_finance_pifr_diff_line (tenant_id, run_id, line_id),
    CONSTRAINT fk_finance_pifr_diff_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES cloudmold_finance_procure_inventory_reconciliation_run (tenant_id, run_id),
    CONSTRAINT fk_finance_pifr_diff_line FOREIGN KEY (tenant_id, line_id)
        REFERENCES cloudmold_finance_procure_inventory_reconciliation_line (tenant_id, line_id),
    CONSTRAINT ck_finance_pifr_diff_domain CHECK (source_domain IN ('PROCUREMENT','INVENTORY','FINANCE','CROSS_DOMAIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Atomic reconciliation differences captured for immutable audit';
