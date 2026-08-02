SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE cloudmold_finance_posting_rule
    DROP CHECK ck_finance_posting_rule_source,
    ADD CONSTRAINT ck_finance_posting_rule_source CHECK (source_type IN (
        'QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT',
        'STOCK_COUNT_ADJUSTMENT','INVENTORY_SCRAP'));

ALTER TABLE cloudmold_finance_posting_rule_line
    DROP CHECK ck_finance_posting_rule_role,
    ADD CONSTRAINT ck_finance_posting_rule_role CHECK (account_role IN (
        'INVENTORY','GRIR','INPUT_TAX','PURCHASE_PRICE_VARIANCE','AP','BANK_CLEARING',
        'INVENTORY_LOSS','SCRAP_EXPENSE','INVENTORY_GAIN'));

ALTER TABLE cloudmold_finance_journal_entry
    DROP CHECK ck_finance_journal_source,
    ADD CONSTRAINT ck_finance_journal_source CHECK (source_type IN (
        'SETTLEMENT_BATCH','QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT',
        'JOURNAL_REVERSAL','STOCK_COUNT_ADJUSTMENT','INVENTORY_SCRAP'));

CREATE TABLE IF NOT EXISTS cloudmold_finance_inventory_control_posting (
    inventory_control_posting_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_document_id VARCHAR(128) NOT NULL,
    source_line_id VARCHAR(128) NOT NULL,
    source_reference_id VARCHAR(128) NOT NULL,
    source_document_version BIGINT NOT NULL,
    source_line_version BIGINT NOT NULL,
    inventory_ledger_transaction_id BIGINT NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    accounting_period_id VARCHAR(128) NOT NULL,
    accounting_date DATE NOT NULL,
    posting_rule_id VARCHAR(128) NOT NULL,
    posting_rule_version BIGINT NOT NULL,
    quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    journal_entry_id VARCHAR(128) NOT NULL,
    journal_code VARCHAR(64) NOT NULL,
    reversal_journal_entry_id VARCHAR(128) NULL,
    posting_evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    reversed_by_principal_id VARCHAR(128) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (inventory_control_posting_id),
    UNIQUE KEY uk_finance_invctl_posting_tenant_id (tenant_id, inventory_control_posting_id),
    UNIQUE KEY uk_finance_invctl_posting_source (tenant_id, source_type, source_reference_id),
    UNIQUE KEY uk_finance_invctl_posting_journal (tenant_id, journal_entry_id),
    UNIQUE KEY uk_finance_invctl_posting_reversal (tenant_id, reversal_journal_entry_id),
    KEY idx_finance_invctl_posting_page (tenant_id, source_type, status, updated_at),
    CONSTRAINT fk_finance_invctl_posting_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT fk_finance_invctl_posting_period FOREIGN KEY (tenant_id, accounting_period_id)
        REFERENCES cloudmold_finance_accounting_period (tenant_id, period_id),
    CONSTRAINT fk_finance_invctl_posting_rule FOREIGN KEY (tenant_id, posting_rule_id, posting_rule_version, ledger_id)
        REFERENCES cloudmold_finance_posting_rule (tenant_id, posting_rule_id, rule_version, ledger_id),
    CONSTRAINT fk_finance_invctl_posting_journal_entry FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT fk_finance_invctl_posting_reversal_journal FOREIGN KEY (tenant_id, reversal_journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT ck_finance_invctl_posting_source CHECK (source_type IN ('STOCK_COUNT_ADJUSTMENT','INVENTORY_SCRAP')),
    CONSTRAINT ck_finance_invctl_posting_status CHECK (status IN ('POSTED','REVERSED')),
    CONSTRAINT ck_finance_invctl_posting_qty CHECK (quantity > 0 AND total_amount_minor > 0),
    CONSTRAINT ck_finance_invctl_posting_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_invctl_posting_hash CHECK (posting_evidence_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Finance-owned exact inventory-control posting authority';

CREATE TABLE IF NOT EXISTS cloudmold_finance_stock_count_gain_basis (
    stock_count_gain_basis_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    basis_code VARCHAR(64) NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    stock_count_line_id VARCHAR(128) NOT NULL,
    stock_count_version BIGINT NOT NULL,
    stock_count_line_version BIGINT NOT NULL,
    valuation_policy_id VARCHAR(128) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    valuation_policy_hash CHAR(64) NOT NULL,
    gain_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    total_cost_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    submitted_by_principal_id VARCHAR(128) NOT NULL,
    approved_by_principal_id VARCHAR(128) NULL,
    approved_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (stock_count_gain_basis_id),
    UNIQUE KEY uk_finance_stock_count_gain_basis_tenant_id (tenant_id, stock_count_gain_basis_id),
    UNIQUE KEY uk_finance_stock_count_gain_basis_code (tenant_id, basis_code),
    UNIQUE KEY uk_finance_stock_count_gain_basis_line (tenant_id, stock_count_line_id, stock_count_line_version),
    CONSTRAINT ck_finance_stock_count_gain_basis_qty CHECK (gain_quantity > 0 AND unit_cost_amount_minor >= 0 AND total_cost_amount_minor > 0),
    CONSTRAINT ck_finance_stock_count_gain_basis_cost CHECK (currency_code REGEXP '^[A-Z]{3}$' AND evidence_sha256 REGEXP '^[0-9a-f]{64}$' AND valuation_policy_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_finance_stock_count_gain_basis_status CHECK (status IN ('SUBMITTED','APPROVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Finance-owned immutable gain valuation basis for stock-count surplus';

CREATE TABLE IF NOT EXISTS cloudmold_finance_stock_count_gain_basis_history (
    history_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    stock_count_gain_basis_id VARCHAR(128) NOT NULL,
    previous_status VARCHAR(16) NULL,
    new_status VARCHAR(16) NOT NULL,
    changed_by_principal_id VARCHAR(128) NOT NULL,
    note VARCHAR(255) NULL,
    version BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    KEY idx_finance_stock_count_gain_basis_history (tenant_id, stock_count_gain_basis_id, changed_at),
    CONSTRAINT fk_finance_stock_count_gain_basis_history FOREIGN KEY (tenant_id, stock_count_gain_basis_id)
        REFERENCES cloudmold_finance_stock_count_gain_basis (tenant_id, stock_count_gain_basis_id),
    CONSTRAINT ck_finance_stock_count_gain_basis_history_status CHECK (new_status IN ('SUBMITTED','APPROVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only stock-count gain valuation basis history';

CREATE TABLE IF NOT EXISTS cloudmold_finance_inventory_control_valuation_layer (
    valuation_layer_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_document_id VARCHAR(128) NOT NULL,
    source_line_id VARCHAR(128) NOT NULL,
    source_reference_id VARCHAR(128) NOT NULL,
    inventory_ledger_transaction_id BIGINT NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    valuation_policy_id VARCHAR(128) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    valuation_policy_hash CHAR(64) NOT NULL,
    quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    total_cost_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    remaining_quantity DECIMAL(24,8) NOT NULL,
    remaining_cost_amount_minor BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (valuation_layer_id),
    UNIQUE KEY uk_finance_invctl_layer_source (tenant_id, source_type, source_reference_id),
    KEY idx_finance_invctl_layer_lookup (tenant_id, ledger_id, owner_type, owner_id, canonical_sku_id, status, created_at),
    CONSTRAINT fk_finance_invctl_layer_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT ck_finance_invctl_layer_source CHECK (source_type IN ('STOCK_COUNT_GAIN')),
    CONSTRAINT ck_finance_invctl_layer_qty CHECK (quantity > 0 AND remaining_quantity >= 0 AND remaining_quantity <= quantity),
    CONSTRAINT ck_finance_invctl_layer_cost CHECK (unit_cost_amount_minor >= 0 AND total_cost_amount_minor > 0 AND remaining_cost_amount_minor >= 0 AND remaining_cost_amount_minor <= total_cost_amount_minor),
    CONSTRAINT ck_finance_invctl_layer_status CHECK (status IN ('OPEN','CONSUMED','REVERSED')),
    CONSTRAINT ck_finance_invctl_layer_hash CHECK (valuation_policy_hash REGEXP '^[0-9a-f]{64}$' AND currency_code REGEXP '^[A-Z]{3}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Finance-owned valuation layer created from approved stock-count gain basis';

CREATE TABLE IF NOT EXISTS cloudmold_finance_inventory_control_posting_allocation (
    allocation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inventory_control_posting_id VARCHAR(128) NOT NULL,
    sequence_no INT NOT NULL,
    valuation_layer_source_type VARCHAR(32) NOT NULL,
    valuation_layer_id VARCHAR(128) NOT NULL,
    allocated_quantity DECIMAL(24,8) NOT NULL,
    allocated_cost_amount_minor BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (allocation_id),
    UNIQUE KEY uk_finance_invctl_alloc_seq (tenant_id, inventory_control_posting_id, sequence_no),
    UNIQUE KEY uk_finance_invctl_alloc_layer (tenant_id, inventory_control_posting_id, valuation_layer_source_type, valuation_layer_id),
    CONSTRAINT fk_finance_invctl_alloc_posting FOREIGN KEY (tenant_id, inventory_control_posting_id)
        REFERENCES cloudmold_finance_inventory_control_posting (tenant_id, inventory_control_posting_id),
    CONSTRAINT ck_finance_invctl_alloc_source CHECK (valuation_layer_source_type IN ('PROCUREMENT_RECEIPT','INVENTORY_CONTROL_GAIN')),
    CONSTRAINT ck_finance_invctl_alloc_qty CHECK (allocated_quantity > 0 AND allocated_cost_amount_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Exact valuation-layer allocation for inventory-control finance posting';
