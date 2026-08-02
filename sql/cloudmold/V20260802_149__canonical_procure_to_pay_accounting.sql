CREATE TABLE cloudmold_finance_ledger (
    ledger_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    ledger_code VARCHAR(64) NOT NULL,
    functional_currency_code CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ledger_id),
    UNIQUE KEY uk_finance_ledger_tenant_id (tenant_id, ledger_id),
    UNIQUE KEY uk_finance_ledger_code (tenant_id, ledger_code),
    CONSTRAINT ck_finance_ledger_currency CHECK (functional_currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_ledger_status CHECK (status IN ('ACTIVE','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Finance ledger authority by legal entity';

CREATE TABLE cloudmold_finance_account (
    account_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    account_name VARCHAR(128) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    normal_balance VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_finance_account_tenant_id (tenant_id, account_id),
    UNIQUE KEY uk_finance_account_ledger_id (tenant_id, ledger_id, account_id),
    UNIQUE KEY uk_finance_account_code (tenant_id, ledger_id, account_code),
    CONSTRAINT fk_finance_account_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT ck_finance_account_type CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    CONSTRAINT ck_finance_account_normal CHECK (normal_balance IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_finance_account_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical chart of accounts';

CREATE TABLE cloudmold_finance_supplier_payee_instrument (
    payee_instrument_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    instrument_token VARCHAR(256) NOT NULL,
    masked_account VARCHAR(64) NOT NULL,
    bank_country_code CHAR(2) NOT NULL,
    bank_code VARCHAR(64) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    verification_status VARCHAR(16) NOT NULL,
    verification_evidence_sha256 CHAR(64) NOT NULL,
    valid_from DATE NOT NULL,
    valid_until DATE NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payee_instrument_id),
    UNIQUE KEY uk_finance_payee_tenant_id (tenant_id, payee_instrument_id),
    UNIQUE KEY uk_finance_payee_token (tenant_id, instrument_token),
    KEY idx_finance_payee_supplier (tenant_id, legal_entity_id, supplier_id, status),
    CONSTRAINT ck_finance_payee_masked CHECK (masked_account REGEXP '^[*Xx0-9 -]{4,64}$'),
    CONSTRAINT ck_finance_payee_verification CHECK (verification_status='VERIFIED'),
    CONSTRAINT ck_finance_payee_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT ck_finance_payee_dates CHECK (valid_until IS NULL OR valid_until >= valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Verified tokenized supplier payment instrument; no plaintext account data';

CREATE TABLE cloudmold_finance_match_policy (
    match_policy_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    policy_version BIGINT NOT NULL,
    price_tolerance_amount_minor BIGINT NOT NULL,
    tax_tolerance_amount_minor BIGINT NOT NULL,
    quantity_tolerance DECIMAL(24,8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, match_policy_id, policy_version),
    UNIQUE KEY uk_finance_match_policy_code (tenant_id, policy_code, policy_version),
    CONSTRAINT ck_finance_match_policy_tolerance CHECK (price_tolerance_amount_minor >= 0
        AND tax_tolerance_amount_minor >= 0 AND quantity_tolerance >= 0),
    CONSTRAINT ck_finance_match_policy_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable versioned three-way match policy';

CREATE TABLE cloudmold_finance_payment_term (
    payment_term_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    term_code VARCHAR(64) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    term_version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, payment_term_id, term_version),
    UNIQUE KEY uk_finance_payment_term_code (tenant_id, term_code, term_version),
    CONSTRAINT ck_finance_payment_term_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable versioned supplier payment term';

CREATE TABLE cloudmold_finance_payment_term_installment_rule (
    installment_rule_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_term_id VARCHAR(128) NOT NULL,
    term_version BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_days_after_issue INT NOT NULL,
    allocation_basis_points INT NOT NULL,
    PRIMARY KEY (installment_rule_id),
    UNIQUE KEY uk_finance_payment_term_rule_tenant (tenant_id, installment_rule_id),
    UNIQUE KEY uk_finance_payment_term_installment
        (tenant_id, payment_term_id, term_version, installment_number),
    CONSTRAINT fk_finance_payment_term_rule FOREIGN KEY (tenant_id, payment_term_id, term_version)
        REFERENCES cloudmold_finance_payment_term (tenant_id, payment_term_id, term_version),
    CONSTRAINT ck_finance_payment_term_days CHECK (due_days_after_issue >= 0),
    CONSTRAINT ck_finance_payment_term_basis CHECK (allocation_basis_points > 0 AND allocation_basis_points <= 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Typed installment schedule rule; all rules per term must sum to 10000 basis points';

CREATE TABLE cloudmold_finance_posting_rule (
    posting_rule_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    rule_version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, posting_rule_id, rule_version),
    UNIQUE KEY uk_finance_posting_rule_ledger_id (tenant_id, posting_rule_id, rule_version, ledger_id),
    UNIQUE KEY uk_finance_posting_rule_code (tenant_id, ledger_id, rule_code, rule_version),
    CONSTRAINT fk_finance_posting_rule_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT ck_finance_posting_rule_source CHECK (source_type IN ('QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT')),
    CONSTRAINT ck_finance_posting_rule_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable versioned accounting posting rule';

CREATE TABLE cloudmold_finance_posting_rule_line (
    posting_rule_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    posting_rule_id VARCHAR(128) NOT NULL,
    rule_version BIGINT NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    account_role VARCHAR(32) NOT NULL,
    account_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (posting_rule_line_id),
    UNIQUE KEY uk_finance_posting_rule_line_tenant (tenant_id, posting_rule_line_id),
    UNIQUE KEY uk_finance_posting_rule_role (tenant_id, posting_rule_id, rule_version, account_role),
    CONSTRAINT fk_finance_posting_rule_line_header FOREIGN KEY (tenant_id, posting_rule_id, rule_version, ledger_id)
        REFERENCES cloudmold_finance_posting_rule (tenant_id, posting_rule_id, rule_version, ledger_id),
    CONSTRAINT fk_finance_posting_rule_line_account FOREIGN KEY (tenant_id, ledger_id, account_id)
        REFERENCES cloudmold_finance_account (tenant_id, ledger_id, account_id),
    CONSTRAINT ck_finance_posting_rule_role CHECK (account_role IN
        ('INVENTORY','GRIR','INPUT_TAX','PURCHASE_PRICE_VARIANCE','AP','BANK_CLEARING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Typed posting role to canonical account mapping';

CREATE TABLE cloudmold_finance_dimension_type (
    dimension_type_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    dimension_code VARCHAR(64) NOT NULL,
    dimension_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (dimension_type_id),
    UNIQUE KEY uk_finance_dimension_type_tenant (tenant_id, dimension_type_id),
    UNIQUE KEY uk_finance_dimension_type_code (tenant_id, dimension_code),
    CONSTRAINT ck_finance_dimension_type_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical journal dimension type';

CREATE TABLE cloudmold_finance_dimension_value (
    dimension_value_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    dimension_type_id VARCHAR(128) NOT NULL,
    value_code VARCHAR(128) NOT NULL,
    value_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (dimension_value_id),
    UNIQUE KEY uk_finance_dimension_value_tenant (tenant_id, dimension_value_id),
    UNIQUE KEY uk_finance_dimension_value_type_id (tenant_id, dimension_type_id, dimension_value_id),
    UNIQUE KEY uk_finance_dimension_value_code (tenant_id, dimension_type_id, value_code),
    CONSTRAINT fk_finance_dimension_value_type FOREIGN KEY (tenant_id, dimension_type_id)
        REFERENCES cloudmold_finance_dimension_type (tenant_id, dimension_type_id),
    CONSTRAINT ck_finance_dimension_value_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical journal dimension value';

CREATE TABLE cloudmold_finance_inventory_valuation_policy (
    valuation_policy_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    cost_method VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, valuation_policy_id, policy_version),
    UNIQUE KEY uk_finance_valuation_policy_code (tenant_id, ledger_id, policy_code, policy_version),
    CONSTRAINT fk_finance_valuation_policy_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT ck_finance_valuation_cost_method CHECK (cost_method IN ('FROZEN_RECEIPT_COST')),
    CONSTRAINT ck_finance_valuation_policy_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable versioned inventory valuation policy';

ALTER TABLE cloudmold_finance_journal_entry
    DROP CHECK ck_finance_journal_source,
    ADD COLUMN legal_entity_id VARCHAR(128) NULL AFTER tenant_id,
    ADD COLUMN ledger_id VARCHAR(128) NULL AFTER legal_entity_id,
    ADD COLUMN accounting_date DATE NULL AFTER period_id,
    ADD COLUMN document_currency_code CHAR(3) NULL AFTER currency_code,
    ADD UNIQUE KEY uk_finance_journal_ledger_id (tenant_id, journal_entry_id, ledger_id),
    ADD CONSTRAINT fk_finance_journal_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    ADD CONSTRAINT fk_finance_journal_period FOREIGN KEY (tenant_id, period_id)
        REFERENCES cloudmold_finance_accounting_period (tenant_id, period_id),
    ADD CONSTRAINT ck_finance_journal_source CHECK (source_type IN
        ('SETTLEMENT_BATCH','QUALIFIED_RECEIPT','SUPPLIER_INVOICE','SUPPLIER_PAYMENT','JOURNAL_REVERSAL')),
    ADD CONSTRAINT ck_finance_journal_p2p_context CHECK (source_type='SETTLEMENT_BATCH'
        OR (legal_entity_id IS NOT NULL AND ledger_id IS NOT NULL AND accounting_date IS NOT NULL
            AND document_currency_code REGEXP '^[A-Z]{3}$'));

CREATE TABLE cloudmold_finance_event_inbox (
    inbox_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    source_event_type VARCHAR(128) NOT NULL,
    source_schema_version INT NOT NULL,
    source_aggregate_id VARCHAR(128) NOT NULL,
    source_aggregate_version BIGINT NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    source_occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (inbox_id),
    UNIQUE KEY uk_finance_event_inbox_tenant_id (tenant_id, inbox_id),
    UNIQUE KEY uk_finance_event_inbox (tenant_id, source_event_id),
    UNIQUE KEY uk_finance_event_inbox_attempt (tenant_id, attempt_token),
    CONSTRAINT ck_finance_event_hash CHECK (evidence_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable cross-domain event identity and evidence envelope';

CREATE TABLE cloudmold_finance_po_line_evidence (
    po_line_evidence_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inbox_id BIGINT NOT NULL,
    purchase_order_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    delivery_schedule_id VARCHAR(128) NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    ordered_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_net_price DECIMAL(24,8) NOT NULL,
    net_amount_minor BIGINT NOT NULL,
    tax_amount_minor BIGINT NOT NULL,
    gross_amount_minor BIGINT NOT NULL,
    source_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (po_line_evidence_id),
    UNIQUE KEY uk_finance_po_line_inbox (tenant_id, inbox_id),
    UNIQUE KEY uk_finance_po_line_version (tenant_id, purchase_order_item_id, source_version),
    KEY idx_finance_po_line_latest (tenant_id, purchase_order_item_id, source_version),
    CONSTRAINT fk_finance_po_line_inbox FOREIGN KEY (tenant_id, inbox_id)
        REFERENCES cloudmold_finance_event_inbox (tenant_id, inbox_id),
    CONSTRAINT ck_finance_po_qty CHECK (ordered_quantity > 0),
    CONSTRAINT ck_finance_po_amounts CHECK (net_amount_minor >= 0 AND tax_amount_minor >= 0
        AND gross_amount_minor = net_amount_minor + tax_amount_minor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned canonical purchase order line accounting evidence';

CREATE TABLE cloudmold_finance_receipt_line_evidence (
    receipt_line_evidence_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inbox_id BIGINT NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    purchase_order_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    purchase_order_line_version BIGINT NOT NULL,
    delivery_schedule_id VARCHAR(128) NULL,
    received_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (receipt_line_evidence_id),
    UNIQUE KEY uk_finance_receipt_line_inbox (tenant_id, inbox_id),
    UNIQUE KEY uk_finance_receipt_line_version (tenant_id, receipt_line_id, source_version),
    KEY idx_finance_receipt_po_line (tenant_id, purchase_order_item_id),
    CONSTRAINT fk_finance_receipt_inbox FOREIGN KEY (tenant_id, inbox_id)
        REFERENCES cloudmold_finance_event_inbox (tenant_id, inbox_id),
    CONSTRAINT fk_finance_receipt_po_line FOREIGN KEY
        (tenant_id, purchase_order_item_id, purchase_order_line_version)
        REFERENCES cloudmold_finance_po_line_evidence (tenant_id, purchase_order_item_id, source_version),
    CONSTRAINT ck_finance_receipt_qty CHECK (received_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned canonical physical receipt line evidence';

CREATE TABLE cloudmold_finance_quality_disposition_evidence (
    quality_evidence_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inbox_id BIGINT NOT NULL,
    quality_disposition_id VARCHAR(128) NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    receipt_line_version BIGINT NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    inspected_quantity DECIMAL(24,8) NOT NULL,
    accepted_quantity DECIMAL(24,8) NOT NULL,
    rejected_quantity DECIMAL(24,8) NOT NULL,
    held_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    source_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (quality_evidence_id),
    UNIQUE KEY uk_finance_quality_inbox (tenant_id, inbox_id),
    UNIQUE KEY uk_finance_quality_version (tenant_id, quality_disposition_id, source_version),
    KEY idx_finance_quality_receipt (tenant_id, receipt_line_id),
    CONSTRAINT fk_finance_quality_inbox FOREIGN KEY (tenant_id, inbox_id)
        REFERENCES cloudmold_finance_event_inbox (tenant_id, inbox_id),
    CONSTRAINT fk_finance_quality_receipt_line FOREIGN KEY
        (tenant_id, receipt_line_id, receipt_line_version)
        REFERENCES cloudmold_finance_receipt_line_evidence (tenant_id, receipt_line_id, source_version),
    CONSTRAINT ck_finance_quality_quantities CHECK (inspected_quantity >= 0 AND accepted_quantity >= 0
        AND rejected_quantity >= 0 AND held_quantity >= 0
        AND inspected_quantity = accepted_quantity + rejected_quantity + held_quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned quantitative quality disposition evidence';

CREATE TABLE cloudmold_finance_inventory_movement_evidence (
    inventory_evidence_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inbox_id BIGINT NOT NULL,
    inventory_movement_id VARCHAR(128) NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    quality_disposition_id VARCHAR(128) NOT NULL,
    quality_disposition_version BIGINT NOT NULL,
    disposition VARCHAR(16) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    movement_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    movement_cost_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    valuation_policy_id VARCHAR(128) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    source_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (inventory_evidence_id),
    UNIQUE KEY uk_finance_inventory_inbox (tenant_id, inbox_id),
    UNIQUE KEY uk_finance_inventory_movement_version (tenant_id, inventory_movement_id, source_version),
    KEY idx_finance_inventory_receipt (tenant_id, receipt_line_id),
    CONSTRAINT fk_finance_inventory_inbox FOREIGN KEY (tenant_id, inbox_id)
        REFERENCES cloudmold_finance_event_inbox (tenant_id, inbox_id),
    CONSTRAINT fk_finance_inventory_quality FOREIGN KEY
        (tenant_id, quality_disposition_id, quality_disposition_version)
        REFERENCES cloudmold_finance_quality_disposition_evidence
        (tenant_id, quality_disposition_id, source_version),
    CONSTRAINT fk_finance_inventory_policy FOREIGN KEY
        (tenant_id, valuation_policy_id, valuation_policy_version)
        REFERENCES cloudmold_finance_inventory_valuation_policy
        (tenant_id, valuation_policy_id, policy_version),
    CONSTRAINT ck_finance_inventory_qty CHECK (movement_quantity > 0),
    CONSTRAINT ck_finance_inventory_disposition CHECK
        (disposition IN ('ACCEPTED','REJECTED','QUARANTINED')),
    CONSTRAINT ck_finance_inventory_cost CHECK (unit_cost_amount_minor >= 0 AND movement_cost_amount_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Versioned inventory movement accounting lineage evidence';

CREATE TABLE cloudmold_finance_supplier_invoice (
    supplier_invoice_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    invoice_code VARCHAR(64) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    accounting_period_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    supplier_invoice_number VARCHAR(128) NOT NULL,
    invoice_type VARCHAR(16) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    issue_date DATE NOT NULL,
    accounting_date DATE NOT NULL,
    due_date DATE NOT NULL,
    net_amount_minor BIGINT NOT NULL,
    tax_amount_minor BIGINT NOT NULL,
    gross_amount_minor BIGINT NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL,
    match_status VARCHAR(16) NOT NULL,
    settlement_status VARCHAR(24) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    payment_term_id VARCHAR(128) NOT NULL,
    payment_term_version BIGINT NOT NULL,
    posting_rule_id VARCHAR(128) NOT NULL,
    posting_rule_version BIGINT NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    approved_by_principal_id VARCHAR(128) NULL,
    posted_journal_entry_id VARCHAR(128) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (supplier_invoice_id),
    UNIQUE KEY uk_finance_invoice_tenant_id (tenant_id, supplier_invoice_id),
    UNIQUE KEY uk_finance_invoice_code (tenant_id, invoice_code),
    UNIQUE KEY uk_finance_supplier_invoice_number
        (tenant_id, legal_entity_id, supplier_id, supplier_invoice_number, invoice_type),
    KEY idx_finance_invoice_status (tenant_id, lifecycle_status, match_status),
    CONSTRAINT fk_finance_invoice_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT fk_finance_invoice_period FOREIGN KEY (tenant_id, accounting_period_id)
        REFERENCES cloudmold_finance_accounting_period (tenant_id, period_id),
    CONSTRAINT fk_finance_invoice_payment_term FOREIGN KEY
        (tenant_id, payment_term_id, payment_term_version)
        REFERENCES cloudmold_finance_payment_term (tenant_id, payment_term_id, term_version),
    CONSTRAINT fk_finance_invoice_posting_rule FOREIGN KEY
        (tenant_id, posting_rule_id, posting_rule_version, ledger_id)
        REFERENCES cloudmold_finance_posting_rule (tenant_id, posting_rule_id, rule_version, ledger_id),
    CONSTRAINT fk_finance_invoice_posted_journal FOREIGN KEY (tenant_id, posted_journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT ck_finance_invoice_type CHECK (invoice_type IN ('INVOICE','CREDIT_NOTE')),
    CONSTRAINT ck_finance_invoice_amounts CHECK (net_amount_minor >= 0 AND tax_amount_minor >= 0
        AND gross_amount_minor = net_amount_minor + tax_amount_minor),
    CONSTRAINT ck_finance_invoice_lifecycle CHECK (lifecycle_status IN ('DRAFT','SUBMITTED','APPROVED','POSTED','REJECTED','CANCELLED')),
    CONSTRAINT ck_finance_invoice_match CHECK (match_status IN ('NOT_STARTED','RUNNING','MATCHED','EXCEPTION')),
    CONSTRAINT ck_finance_invoice_settlement CHECK (settlement_status IN ('UNPAID','PARTIALLY_PAID','PAID'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical supplier invoice header';

CREATE TABLE cloudmold_finance_supplier_invoice_line (
    invoice_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_invoice_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    purchase_order_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    sku_id VARCHAR(128) NOT NULL,
    quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    unit_net_price DECIMAL(24,8) NOT NULL,
    net_amount_minor BIGINT NOT NULL,
    tax_code VARCHAR(64) NOT NULL,
    tax_rate DECIMAL(18,8) NOT NULL,
    tax_amount_minor BIGINT NOT NULL,
    gross_amount_minor BIGINT NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (invoice_line_id),
    UNIQUE KEY uk_finance_invoice_line_tenant_id (tenant_id, invoice_line_id),
    UNIQUE KEY uk_finance_invoice_line_number (tenant_id, supplier_invoice_id, line_number),
    KEY idx_finance_invoice_line_po (tenant_id, purchase_order_item_id),
    CONSTRAINT fk_finance_invoice_line_header FOREIGN KEY (tenant_id, supplier_invoice_id)
        REFERENCES cloudmold_finance_supplier_invoice (tenant_id, supplier_invoice_id),
    CONSTRAINT ck_finance_invoice_line_qty CHECK (quantity > 0),
    CONSTRAINT ck_finance_invoice_line_amounts CHECK (net_amount_minor >= 0 AND tax_amount_minor >= 0
        AND gross_amount_minor = net_amount_minor + tax_amount_minor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical typed supplier invoice line';

CREATE TABLE cloudmold_finance_supplier_invoice_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    supplier_invoice_id VARCHAR(128) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    match_status VARCHAR(16) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    KEY idx_finance_invoice_history (tenant_id, supplier_invoice_id, aggregate_version),
    CONSTRAINT fk_finance_invoice_history FOREIGN KEY (tenant_id, supplier_invoice_id)
        REFERENCES cloudmold_finance_supplier_invoice (tenant_id, supplier_invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable supplier invoice lifecycle history';

CREATE TABLE cloudmold_finance_invoice_match_run (
    match_run_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_invoice_id VARCHAR(128) NOT NULL,
    invoice_version BIGINT NOT NULL,
    match_policy_id VARCHAR(128) NOT NULL,
    match_policy_version BIGINT NOT NULL,
    price_tolerance_amount_minor BIGINT NOT NULL,
    tax_tolerance_amount_minor BIGINT NOT NULL,
    quantity_tolerance DECIMAL(24,8) NOT NULL,
    status VARCHAR(24) NOT NULL,
    requested_by_principal_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (match_run_id),
    UNIQUE KEY uk_finance_match_run_tenant_id (tenant_id, match_run_id),
    KEY idx_finance_match_invoice (tenant_id, supplier_invoice_id, created_at),
    CONSTRAINT fk_finance_match_invoice FOREIGN KEY (tenant_id, supplier_invoice_id)
        REFERENCES cloudmold_finance_supplier_invoice (tenant_id, supplier_invoice_id),
    CONSTRAINT fk_finance_match_policy FOREIGN KEY
        (tenant_id, match_policy_id, match_policy_version)
        REFERENCES cloudmold_finance_match_policy (tenant_id, match_policy_id, policy_version),
    CONSTRAINT ck_finance_match_run_status CHECK (status IN ('RUNNING','MATCHED','EXCEPTION','SUPERSEDED')),
    CONSTRAINT ck_finance_match_tolerances CHECK (price_tolerance_amount_minor >= 0
        AND tax_tolerance_amount_minor >= 0 AND quantity_tolerance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Version-frozen three-way match execution';

CREATE TABLE cloudmold_finance_invoice_match_line (
    match_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    match_run_id VARCHAR(128) NOT NULL,
    invoice_line_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    purchase_order_line_version BIGINT NOT NULL,
    invoice_quantity DECIMAL(24,8) NOT NULL,
    eligible_quantity DECIMAL(24,8) NOT NULL,
    invoice_net_amount_minor BIGINT NOT NULL,
    expected_po_net_amount_minor BIGINT NOT NULL,
    price_difference_amount_minor BIGINT NOT NULL,
    tax_difference_amount_minor BIGINT NOT NULL,
    result_status VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (match_line_id),
    UNIQUE KEY uk_finance_match_line_run_id (tenant_id, match_run_id, match_line_id),
    UNIQUE KEY uk_finance_match_line_tenant_id (tenant_id, match_line_id),
    UNIQUE KEY uk_finance_match_invoice_line (tenant_id, match_run_id, invoice_line_id),
    CONSTRAINT fk_finance_match_line_run FOREIGN KEY (tenant_id, match_run_id)
        REFERENCES cloudmold_finance_invoice_match_run (tenant_id, match_run_id),
    CONSTRAINT fk_finance_match_line_invoice FOREIGN KEY (tenant_id, invoice_line_id)
        REFERENCES cloudmold_finance_supplier_invoice_line (tenant_id, invoice_line_id),
    CONSTRAINT fk_finance_match_line_po_evidence FOREIGN KEY
        (tenant_id, purchase_order_item_id, purchase_order_line_version)
        REFERENCES cloudmold_finance_po_line_evidence
        (tenant_id, purchase_order_item_id, source_version),
    CONSTRAINT ck_finance_match_line_status CHECK (result_status IN ('MATCHED','EXCEPTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Frozen three-way match result per invoice line';

CREATE TABLE cloudmold_finance_invoice_match_receipt_allocation (
    match_allocation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    match_run_id VARCHAR(128) NOT NULL,
    match_line_id VARCHAR(128) NOT NULL,
    invoice_line_id VARCHAR(128) NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    receipt_line_version BIGINT NOT NULL,
    quality_disposition_id VARCHAR(128) NOT NULL,
    quality_disposition_version BIGINT NOT NULL,
    inventory_movement_id VARCHAR(128) NOT NULL,
    inventory_movement_version BIGINT NOT NULL,
    allocated_quantity DECIMAL(24,8) NOT NULL,
    unit_of_measure VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (match_allocation_id),
    KEY idx_finance_match_alloc_receipt (tenant_id, receipt_line_id, status),
    KEY idx_finance_match_alloc_invoice (tenant_id, invoice_line_id, status),
    CONSTRAINT fk_finance_match_alloc_run FOREIGN KEY (tenant_id, match_run_id)
        REFERENCES cloudmold_finance_invoice_match_run (tenant_id, match_run_id),
    CONSTRAINT fk_finance_match_alloc_line FOREIGN KEY (tenant_id, match_run_id, match_line_id)
        REFERENCES cloudmold_finance_invoice_match_line (tenant_id, match_run_id, match_line_id),
    CONSTRAINT fk_finance_match_alloc_receipt_evidence FOREIGN KEY
        (tenant_id, receipt_line_id, receipt_line_version)
        REFERENCES cloudmold_finance_receipt_line_evidence
        (tenant_id, receipt_line_id, source_version),
    CONSTRAINT fk_finance_match_alloc_quality_evidence FOREIGN KEY
        (tenant_id, quality_disposition_id, quality_disposition_version)
        REFERENCES cloudmold_finance_quality_disposition_evidence
        (tenant_id, quality_disposition_id, source_version),
    CONSTRAINT fk_finance_match_alloc_inventory_evidence FOREIGN KEY
        (tenant_id, inventory_movement_id, inventory_movement_version)
        REFERENCES cloudmold_finance_inventory_movement_evidence
        (tenant_id, inventory_movement_id, source_version),
    CONSTRAINT ck_finance_match_alloc_qty CHECK (allocated_quantity > 0),
    CONSTRAINT ck_finance_match_alloc_status CHECK (status IN ('ACTIVE','PENDING_OVERRIDE','SUPERSEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable quality-qualified receipt quantity allocation';

CREATE TABLE cloudmold_finance_invoice_match_exception (
    exception_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    match_run_id VARCHAR(128) NOT NULL,
    match_line_id VARCHAR(128) NOT NULL,
    exception_code VARCHAR(64) NOT NULL,
    exception_type VARCHAR(32) NOT NULL,
    blocking TINYINT(1) NOT NULL,
    expected_quantity DECIMAL(24,8) NULL,
    actual_quantity DECIMAL(24,8) NULL,
    expected_amount_minor BIGINT NULL,
    actual_amount_minor BIGINT NULL,
    expected_code VARCHAR(128) NULL,
    actual_code VARCHAR(128) NULL,
    status VARCHAR(24) NOT NULL,
    opened_by_principal_id VARCHAR(128) NOT NULL,
    resolved_by_principal_id VARCHAR(128) NULL,
    resolution_evidence_sha256 CHAR(64) NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (exception_id),
    UNIQUE KEY uk_finance_match_exception_tenant_id (tenant_id, exception_id),
    KEY idx_finance_match_exception (tenant_id, match_run_id, status),
    CONSTRAINT fk_finance_match_exception_line FOREIGN KEY (tenant_id, match_run_id, match_line_id)
        REFERENCES cloudmold_finance_invoice_match_line (tenant_id, match_run_id, match_line_id),
    CONSTRAINT ck_finance_match_exception_type CHECK (exception_type IN ('QUANTITY','PRICE','TAX','MISSING_EVIDENCE')),
    CONSTRAINT ck_finance_match_exception_code CHECK (exception_code IN
        ('QUANTITY_SHORTAGE','PRICE_VARIANCE','TAX_VARIANCE','PARTY_AUTHORITY_MISMATCH','CURRENCY_MISMATCH','UOM_MISMATCH')),
    CONSTRAINT ck_finance_match_exception_status CHECK (status IN ('OPEN','RESOLVED','OVERRIDE_APPROVED','REJECTED','SUPERSEDED')),
    CONSTRAINT ck_finance_match_exception_values CHECK (
        (exception_type='QUANTITY' AND expected_quantity IS NOT NULL AND actual_quantity IS NOT NULL
            AND expected_amount_minor IS NULL AND actual_amount_minor IS NULL
            AND expected_code IS NULL AND actual_code IS NULL)
        OR (exception_type IN ('PRICE','TAX') AND expected_amount_minor IS NOT NULL AND actual_amount_minor IS NOT NULL
            AND expected_quantity IS NULL AND actual_quantity IS NULL
            AND expected_code IS NULL AND actual_code IS NULL)
        OR (exception_type='MISSING_EVIDENCE' AND expected_code IS NOT NULL AND actual_code IS NOT NULL
            AND expected_quantity IS NULL AND actual_quantity IS NULL
            AND expected_amount_minor IS NULL AND actual_amount_minor IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Controlled blocking match exception';

CREATE TABLE cloudmold_finance_invoice_match_exception_resolution (
    resolution_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    exception_id VARCHAR(128) NOT NULL,
    resolution_type VARCHAR(32) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (resolution_id),
    UNIQUE KEY uk_finance_exception_resolution (tenant_id, exception_id),
    CONSTRAINT fk_finance_exception_resolution FOREIGN KEY (tenant_id, exception_id)
        REFERENCES cloudmold_finance_invoice_match_exception (tenant_id, exception_id),
    CONSTRAINT ck_finance_exception_resolution_type CHECK (resolution_type IN ('CORRECTED','OVERRIDE_APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable match exception decision evidence';

CREATE TABLE cloudmold_finance_ap_open_item (
    ap_open_item_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    supplier_invoice_id VARCHAR(128) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    original_amount_minor BIGINT NOT NULL,
    settled_amount_minor BIGINT NOT NULL,
    open_amount_minor BIGINT NOT NULL,
    due_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ap_open_item_id),
    UNIQUE KEY uk_finance_ap_tenant_id (tenant_id, ap_open_item_id),
    UNIQUE KEY uk_finance_ap_invoice (tenant_id, supplier_invoice_id),
    KEY idx_finance_ap_supplier (tenant_id, supplier_id, status, due_date),
    CONSTRAINT fk_finance_ap_invoice FOREIGN KEY (tenant_id, supplier_invoice_id)
        REFERENCES cloudmold_finance_supplier_invoice (tenant_id, supplier_invoice_id),
    CONSTRAINT fk_finance_ap_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT ck_finance_ap_amount CHECK (original_amount_minor > 0 AND settled_amount_minor >= 0
        AND open_amount_minor = original_amount_minor - settled_amount_minor AND open_amount_minor >= 0),
    CONSTRAINT ck_finance_ap_status CHECK (status IN ('OPEN','PARTIALLY_SETTLED','SETTLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical accounts payable open item';

CREATE TABLE cloudmold_finance_ap_installment (
    ap_installment_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ap_open_item_id VARCHAR(128) NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    amount_minor BIGINT NOT NULL,
    settled_amount_minor BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ap_installment_id),
    UNIQUE KEY uk_finance_ap_installment_number (tenant_id, ap_open_item_id, installment_number),
    CONSTRAINT fk_finance_ap_installment_item FOREIGN KEY (tenant_id, ap_open_item_id)
        REFERENCES cloudmold_finance_ap_open_item (tenant_id, ap_open_item_id),
    CONSTRAINT ck_finance_ap_installment_amount CHECK (amount_minor > 0 AND settled_amount_minor >= 0
        AND settled_amount_minor <= amount_minor),
    CONSTRAINT ck_finance_ap_installment_status CHECK (status IN ('OPEN','PARTIALLY_SETTLED','SETTLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Accounts payable payment schedule';

CREATE TABLE cloudmold_finance_ap_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    ap_open_item_id VARCHAR(128) NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NOT NULL,
    amount_minor BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    KEY idx_finance_ap_history (tenant_id, ap_open_item_id, aggregate_version),
    CONSTRAINT fk_finance_ap_history_item FOREIGN KEY (tenant_id, ap_open_item_id)
        REFERENCES cloudmold_finance_ap_open_item (tenant_id, ap_open_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable AP lifecycle and amount history';

CREATE TABLE cloudmold_finance_supplier_payment_instruction (
    payment_instruction_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_code VARCHAR(64) NOT NULL,
    legal_entity_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    accounting_period_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    payee_instrument_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    requested_execution_date DATE NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    posting_rule_id VARCHAR(128) NOT NULL,
    posting_rule_version BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    approved_by_principal_id VARCHAR(128) NULL,
    released_by_principal_id VARCHAR(128) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_instruction_id),
    UNIQUE KEY uk_finance_supplier_payment_tenant_id (tenant_id, payment_instruction_id),
    UNIQUE KEY uk_finance_supplier_payment_code (tenant_id, payment_code),
    KEY idx_finance_supplier_payment_status (tenant_id, status, requested_execution_date),
    CONSTRAINT fk_finance_supplier_payment_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT fk_finance_supplier_payment_period FOREIGN KEY (tenant_id, accounting_period_id)
        REFERENCES cloudmold_finance_accounting_period (tenant_id, period_id),
    CONSTRAINT fk_finance_supplier_payment_payee FOREIGN KEY (tenant_id, payee_instrument_id)
        REFERENCES cloudmold_finance_supplier_payee_instrument (tenant_id, payee_instrument_id),
    CONSTRAINT fk_finance_supplier_payment_posting_rule FOREIGN KEY
        (tenant_id, posting_rule_id, posting_rule_version, ledger_id)
        REFERENCES cloudmold_finance_posting_rule (tenant_id, posting_rule_id, rule_version, ledger_id),
    CONSTRAINT ck_finance_supplier_payment_amount CHECK (total_amount_minor > 0),
    CONSTRAINT ck_finance_supplier_payment_status CHECK (status IN ('DRAFT','SUBMITTED_FOR_APPROVAL','APPROVED','RELEASED','PROCESSING','EXECUTED','SETTLED','FAILED','RETURNED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical supplier payment instruction';

CREATE TABLE cloudmold_finance_supplier_payment_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    payment_instruction_id VARCHAR(128) NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    KEY idx_finance_payment_history (tenant_id, payment_instruction_id, aggregate_version),
    CONSTRAINT fk_finance_payment_history_instruction FOREIGN KEY (tenant_id, payment_instruction_id)
        REFERENCES cloudmold_finance_supplier_payment_instruction (tenant_id, payment_instruction_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable supplier payment status history';

CREATE TABLE cloudmold_finance_supplier_payment_allocation (
    payment_allocation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_instruction_id VARCHAR(128) NOT NULL,
    ap_open_item_id VARCHAR(128) NOT NULL,
    amount_minor BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (payment_allocation_id),
    UNIQUE KEY uk_finance_payment_ap_allocation (tenant_id, payment_instruction_id, ap_open_item_id),
    KEY idx_finance_payment_allocation_ap (tenant_id, ap_open_item_id),
    CONSTRAINT fk_finance_payment_alloc_instruction FOREIGN KEY (tenant_id, payment_instruction_id)
        REFERENCES cloudmold_finance_supplier_payment_instruction (tenant_id, payment_instruction_id),
    CONSTRAINT fk_finance_payment_alloc_ap FOREIGN KEY (tenant_id, ap_open_item_id)
        REFERENCES cloudmold_finance_ap_open_item (tenant_id, ap_open_item_id),
    CONSTRAINT ck_finance_payment_allocation_amount CHECK (amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Supplier payment allocation and AP reservation';

CREATE TABLE cloudmold_finance_supplier_payment_execution (
    execution_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_instruction_id VARCHAR(128) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(128) NOT NULL,
    execution_evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    executed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (execution_id),
    UNIQUE KEY uk_finance_payment_provider_ref (tenant_id, provider_code, provider_reference),
    CONSTRAINT fk_finance_payment_execution FOREIGN KEY (tenant_id, payment_instruction_id)
        REFERENCES cloudmold_finance_supplier_payment_instruction (tenant_id, payment_instruction_id),
    CONSTRAINT ck_finance_payment_execution_status CHECK (status IN ('EXECUTED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='External supplier payment execution evidence';

CREATE TABLE cloudmold_finance_supplier_payment_settlement (
    settlement_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    payment_instruction_id VARCHAR(128) NOT NULL,
    settlement_date DATE NOT NULL,
    settled_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    bank_reference VARCHAR(128) NOT NULL,
    settlement_evidence_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (settlement_id),
    UNIQUE KEY uk_finance_payment_settlement_instruction (tenant_id, payment_instruction_id),
    UNIQUE KEY uk_finance_payment_bank_reference (tenant_id, bank_reference),
    CONSTRAINT fk_finance_payment_settlement FOREIGN KEY (tenant_id, payment_instruction_id)
        REFERENCES cloudmold_finance_supplier_payment_instruction (tenant_id, payment_instruction_id),
    CONSTRAINT ck_finance_payment_settlement_amount CHECK (settled_amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Authenticated bank settlement evidence';

CREATE TABLE cloudmold_finance_ap_application (
    ap_application_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ap_open_item_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    application_type VARCHAR(24) NOT NULL,
    amount_minor BIGINT NOT NULL,
    journal_entry_id VARCHAR(128) NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ap_application_id),
    UNIQUE KEY uk_finance_ap_application_source (tenant_id, source_type, source_id, ap_open_item_id),
    CONSTRAINT fk_finance_ap_application_item FOREIGN KEY (tenant_id, ap_open_item_id)
        REFERENCES cloudmold_finance_ap_open_item (tenant_id, ap_open_item_id),
    CONSTRAINT fk_finance_ap_application_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT ck_finance_ap_application_source CHECK (source_type IN ('PAYMENT_SETTLEMENT','PAYMENT_RETURN','CREDIT_NOTE','JOURNAL_REVERSAL')),
    CONSTRAINT ck_finance_ap_application_type CHECK (application_type IN ('SETTLE','REOPEN')),
    CONSTRAINT ck_finance_ap_application_amount CHECK (amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable AP settlement and reopening application';

CREATE TABLE cloudmold_finance_journal_line (
    journal_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    journal_entry_id VARCHAR(128) NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    account_id VARCHAR(128) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    debit_amount_minor BIGINT NOT NULL,
    credit_amount_minor BIGINT NOT NULL,
    transaction_currency_code CHAR(3) NOT NULL,
    transaction_amount_minor BIGINT NOT NULL,
    supplier_id VARCHAR(128) NULL,
    supplier_invoice_id VARCHAR(128) NULL,
    ap_open_item_id VARCHAR(128) NULL,
    purchase_order_id VARCHAR(128) NULL,
    purchase_order_item_id VARCHAR(128) NULL,
    receipt_line_id VARCHAR(128) NULL,
    inventory_movement_id VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (journal_line_id),
    UNIQUE KEY uk_finance_journal_line_tenant_id (tenant_id, journal_line_id),
    UNIQUE KEY uk_finance_journal_line_number (tenant_id, journal_entry_id, line_number),
    CONSTRAINT fk_finance_journal_line_entry FOREIGN KEY (tenant_id, journal_entry_id, ledger_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id, ledger_id),
    CONSTRAINT fk_finance_journal_line_account FOREIGN KEY (tenant_id, ledger_id, account_id)
        REFERENCES cloudmold_finance_account (tenant_id, ledger_id, account_id),
    CONSTRAINT ck_finance_journal_line_side CHECK (
        (debit_amount_minor > 0 AND credit_amount_minor = 0)
        OR (credit_amount_minor > 0 AND debit_amount_minor = 0)),
    CONSTRAINT ck_finance_journal_line_transaction CHECK (transaction_amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable balanced canonical journal line';

CREATE TABLE cloudmold_finance_journal_line_dimension (
    journal_line_dimension_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    journal_line_id VARCHAR(128) NOT NULL,
    dimension_type_id VARCHAR(128) NOT NULL,
    dimension_value_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (journal_line_dimension_id),
    UNIQUE KEY uk_finance_journal_line_dimension
        (tenant_id, journal_line_id, dimension_type_id),
    CONSTRAINT fk_finance_journal_dimension_line FOREIGN KEY (tenant_id, journal_line_id)
        REFERENCES cloudmold_finance_journal_line (tenant_id, journal_line_id),
    CONSTRAINT fk_finance_journal_dimension_type FOREIGN KEY (tenant_id, dimension_type_id)
        REFERENCES cloudmold_finance_dimension_type (tenant_id, dimension_type_id),
    CONSTRAINT fk_finance_journal_dimension_value FOREIGN KEY
        (tenant_id, dimension_type_id, dimension_value_id)
        REFERENCES cloudmold_finance_dimension_value (tenant_id, dimension_type_id, dimension_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Typed auxiliary accounting dimension assignment';

CREATE TABLE cloudmold_finance_journal_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    journal_entry_id VARCHAR(128) NOT NULL,
    from_status VARCHAR(16) NULL,
    to_status VARCHAR(16) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NULL,
    aggregate_version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    KEY idx_finance_journal_history (tenant_id, journal_entry_id, aggregate_version),
    CONSTRAINT fk_finance_journal_history_entry FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable journal lifecycle history';

CREATE TABLE cloudmold_finance_journal_source_effect (
    source_effect_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    effect_type VARCHAR(32) NOT NULL,
    journal_entry_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (source_effect_id),
    UNIQUE KEY uk_finance_journal_source_effect (tenant_id, source_type, source_id, effect_type),
    CONSTRAINT fk_finance_journal_effect_entry FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT ck_finance_journal_effect_type CHECK (effect_type IN ('QUALIFIED_RECEIPT','INVOICE_POSTING','PAYMENT_SETTLEMENT','REVERSAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='One immutable journal effect per authoritative source transition';

CREATE TABLE cloudmold_finance_journal_reversal_link (
    reversal_link_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    original_journal_entry_id VARCHAR(128) NOT NULL,
    reversal_journal_entry_id VARCHAR(128) NOT NULL,
    reversal_evidence_sha256 CHAR(64) NOT NULL,
    reversed_by_principal_id VARCHAR(128) NOT NULL,
    reversed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (reversal_link_id),
    UNIQUE KEY uk_finance_journal_original_reversal (tenant_id, original_journal_entry_id),
    UNIQUE KEY uk_finance_journal_reversal_entry (tenant_id, reversal_journal_entry_id),
    CONSTRAINT fk_finance_journal_reversal_original FOREIGN KEY (tenant_id, original_journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT fk_finance_journal_reversal_new FOREIGN KEY (tenant_id, reversal_journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Exact one-to-one posted journal reversal authority';

CREATE TABLE cloudmold_finance_inventory_valuation_layer (
    valuation_layer_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ledger_id VARCHAR(128) NOT NULL,
    inventory_movement_id VARCHAR(128) NOT NULL,
    inventory_movement_version BIGINT NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    quality_disposition_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    valuation_policy_id VARCHAR(128) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
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
    UNIQUE KEY uk_finance_valuation_layer_tenant (tenant_id, valuation_layer_id),
    UNIQUE KEY uk_finance_valuation_movement (tenant_id, ledger_id, inventory_movement_id),
    CONSTRAINT fk_finance_valuation_layer_ledger FOREIGN KEY (tenant_id, ledger_id)
        REFERENCES cloudmold_finance_ledger (tenant_id, ledger_id),
    CONSTRAINT fk_finance_valuation_layer_inventory_evidence FOREIGN KEY
        (tenant_id, inventory_movement_id, inventory_movement_version)
        REFERENCES cloudmold_finance_inventory_movement_evidence
        (tenant_id, inventory_movement_id, source_version),
    CONSTRAINT fk_finance_valuation_layer_policy FOREIGN KEY
        (tenant_id, valuation_policy_id, valuation_policy_version)
        REFERENCES cloudmold_finance_inventory_valuation_policy
        (tenant_id, valuation_policy_id, policy_version),
    CONSTRAINT ck_finance_valuation_layer_qty CHECK (quantity > 0 AND remaining_quantity >= 0
        AND remaining_quantity <= quantity),
    CONSTRAINT ck_finance_valuation_layer_cost CHECK (unit_cost_amount_minor >= 0
        AND total_cost_amount_minor >= 0 AND remaining_cost_amount_minor >= 0
        AND remaining_cost_amount_minor <= total_cost_amount_minor),
    CONSTRAINT ck_finance_valuation_layer_status CHECK (status IN ('OPEN','CONSUMED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Finance-owned immutable-origin inventory valuation layer';

CREATE TABLE cloudmold_finance_inventory_valuation_effect (
    valuation_effect_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    valuation_layer_id VARCHAR(128) NOT NULL,
    effect_type VARCHAR(24) NOT NULL,
    inventory_movement_id VARCHAR(128) NOT NULL,
    inventory_movement_version BIGINT NOT NULL,
    quantity DECIMAL(24,8) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    journal_entry_id VARCHAR(128) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (valuation_effect_id),
    UNIQUE KEY uk_finance_valuation_effect_source
        (tenant_id, inventory_movement_id, effect_type),
    CONSTRAINT fk_finance_valuation_effect_layer FOREIGN KEY (tenant_id, valuation_layer_id)
        REFERENCES cloudmold_finance_inventory_valuation_layer (tenant_id, valuation_layer_id),
    CONSTRAINT fk_finance_valuation_effect_inventory_evidence FOREIGN KEY
        (tenant_id, inventory_movement_id, inventory_movement_version)
        REFERENCES cloudmold_finance_inventory_movement_evidence
        (tenant_id, inventory_movement_id, source_version),
    CONSTRAINT fk_finance_valuation_effect_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES cloudmold_finance_journal_entry (tenant_id, journal_entry_id),
    CONSTRAINT ck_finance_valuation_effect_type CHECK (effect_type IN ('QUALIFIED_RECEIPT','SUPPLIER_RETURN','REVERSAL')),
    CONSTRAINT ck_finance_valuation_effect_amount CHECK (quantity <> 0 AND amount_minor >= 0
        AND ((amount_minor=0 AND journal_entry_id IS NULL)
          OR (amount_minor>0 AND journal_entry_id IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable valuation effect linked to exact inventory movement and journal';
