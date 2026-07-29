CREATE TABLE IF NOT EXISTS cloudmold_finance_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status INT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_finance_operation (tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Idempotent canonical finance command envelope';

CREATE TABLE IF NOT EXISTS cloudmold_finance_accounting_period (
    period_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    period_code VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    opened_by_principal_id VARCHAR(128) NOT NULL,
    closed_by_principal_id VARCHAR(128) NULL,
    close_evidence_sha256 CHAR(64) NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (period_id),
    UNIQUE KEY uk_finance_period_tenant (tenant_id, period_id),
    UNIQUE KEY uk_finance_period_code (tenant_id, period_code),
    KEY idx_finance_period_status (tenant_id, status, period_end),
    CONSTRAINT ck_finance_period_dates CHECK (period_end >= period_start),
    CONSTRAINT ck_finance_period_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_period_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical finance accounting period and close authority';

CREATE TABLE IF NOT EXISTS cloudmold_finance_channel_statement (
    statement_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    statement_code VARCHAR(64) NOT NULL,
    period_id VARCHAR(128) NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    statement_date DATE NOT NULL,
    currency_code CHAR(3) NOT NULL,
    gross_amount_minor BIGINT NOT NULL,
    refund_amount_minor BIGINT NOT NULL,
    fee_amount_minor BIGINT NOT NULL,
    net_settlement_amount_minor BIGINT NOT NULL,
    expected_business_net_amount_minor BIGINT NOT NULL,
    difference_amount_minor BIGINT NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    imported_by_principal_id VARCHAR(128) NOT NULL,
    reconciled_by_principal_id VARCHAR(128) NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    imported_at DATETIME(6) NOT NULL,
    reconciled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (statement_id),
    UNIQUE KEY uk_finance_statement_tenant (tenant_id, statement_id),
    UNIQUE KEY uk_finance_statement_code (tenant_id, channel_code, statement_code),
    KEY idx_finance_statement_period (tenant_id, period_id, status),
    CONSTRAINT ck_finance_statement_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_statement_gross CHECK (gross_amount_minor >= 0),
    CONSTRAINT ck_finance_statement_refund CHECK (refund_amount_minor >= 0),
    CONSTRAINT ck_finance_statement_fee CHECK (fee_amount_minor >= 0),
    CONSTRAINT ck_finance_statement_net CHECK (net_settlement_amount_minor >= 0),
    CONSTRAINT ck_finance_statement_expected CHECK (expected_business_net_amount_minor >= 0),
    CONSTRAINT ck_finance_statement_balance CHECK (
        net_settlement_amount_minor = gross_amount_minor - refund_amount_minor - fee_amount_minor),
    CONSTRAINT ck_finance_statement_status CHECK (status IN ('IMPORTED','EXCEPTION','RECONCILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical payment channel statement authority';

CREATE TABLE IF NOT EXISTS cloudmold_finance_reconciliation_difference (
    difference_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    period_id VARCHAR(128) NOT NULL,
    statement_id VARCHAR(128) NOT NULL,
    difference_amount_minor BIGINT NOT NULL,
    adjustment_amount_minor BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    resolution_type VARCHAR(32) NULL,
    resolution_evidence_sha256 CHAR(64) NULL,
    opened_by_principal_id VARCHAR(128) NOT NULL,
    resolved_by_principal_id VARCHAR(128) NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (difference_id),
    UNIQUE KEY uk_finance_difference_tenant (tenant_id, difference_id),
    UNIQUE KEY uk_finance_difference_statement (tenant_id, statement_id),
    KEY idx_finance_difference_period (tenant_id, period_id, status),
    CONSTRAINT ck_finance_difference_nonzero CHECK (difference_amount_minor <> 0),
    CONSTRAINT ck_finance_difference_status CHECK (status IN ('OPEN','RESOLVED')),
    CONSTRAINT ck_finance_difference_resolution CHECK (
        (status='OPEN' AND adjustment_amount_minor IS NULL AND resolution_type IS NULL
          AND resolution_evidence_sha256 IS NULL AND resolved_by_principal_id IS NULL AND resolved_at IS NULL)
        OR
        (status='RESOLVED' AND adjustment_amount_minor=difference_amount_minor
          AND resolution_type IN ('CHANNEL_ADJUSTMENT','BUSINESS_ADJUSTMENT','MANUAL_EVIDENCE')
          AND resolution_evidence_sha256 REGEXP '^[0-9a-f]{64}$'
          AND resolved_by_principal_id IS NOT NULL AND resolved_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical reconciliation difference and resolution evidence';

CREATE TABLE IF NOT EXISTS cloudmold_finance_settlement_batch (
    settlement_batch_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    settlement_code VARCHAR(64) NOT NULL,
    period_id VARCHAR(128) NOT NULL,
    statement_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    expected_amount_minor BIGINT NOT NULL,
    settled_amount_minor BIGINT NULL,
    bank_reference VARCHAR(128) NULL,
    settlement_evidence_sha256 CHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    prepared_by_principal_id VARCHAR(128) NOT NULL,
    settled_by_principal_id VARCHAR(128) NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    prepared_at DATETIME(6) NOT NULL,
    settled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (settlement_batch_id),
    UNIQUE KEY uk_finance_settlement_tenant (tenant_id, settlement_batch_id),
    UNIQUE KEY uk_finance_settlement_code (tenant_id, settlement_code),
    UNIQUE KEY uk_finance_settlement_statement (tenant_id, statement_id),
    KEY idx_finance_settlement_period (tenant_id, period_id, status),
    CONSTRAINT ck_finance_settlement_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_settlement_expected CHECK (expected_amount_minor >= 0),
    CONSTRAINT ck_finance_settlement_status CHECK (status IN ('PREPARED','SETTLED')),
    CONSTRAINT ck_finance_settlement_evidence CHECK (
        (status='PREPARED' AND settled_amount_minor IS NULL AND bank_reference IS NULL
          AND settlement_evidence_sha256 IS NULL AND settled_by_principal_id IS NULL AND settled_at IS NULL)
        OR
        (status='SETTLED' AND settled_amount_minor=expected_amount_minor
          AND bank_reference IS NOT NULL
          AND settlement_evidence_sha256 REGEXP '^[0-9a-f]{64}$'
          AND settled_by_principal_id IS NOT NULL AND settled_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical channel settlement batch authority';

CREATE TABLE IF NOT EXISTS cloudmold_finance_journal_entry (
    journal_entry_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    journal_code VARCHAR(64) NOT NULL,
    period_id VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    debit_total_minor BIGINT NOT NULL,
    credit_total_minor BIGINT NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    prepared_by_principal_id VARCHAR(128) NOT NULL,
    posted_by_principal_id VARCHAR(128) NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    prepared_at DATETIME(6) NOT NULL,
    posted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (journal_entry_id),
    UNIQUE KEY uk_finance_journal_tenant (tenant_id, journal_entry_id),
    UNIQUE KEY uk_finance_journal_code (tenant_id, journal_code),
    UNIQUE KEY uk_finance_journal_source (tenant_id, source_type, source_id),
    KEY idx_finance_journal_period (tenant_id, period_id, status),
    CONSTRAINT ck_finance_journal_source CHECK (source_type='SETTLEMENT_BATCH'),
    CONSTRAINT ck_finance_journal_currency CHECK (currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_finance_journal_amount CHECK (
        debit_total_minor > 0 AND debit_total_minor=credit_total_minor),
    CONSTRAINT ck_finance_journal_status CHECK (status IN ('PREPARED','POSTED')),
    CONSTRAINT ck_finance_journal_posted CHECK (
        (status='PREPARED' AND posted_by_principal_id IS NULL AND posted_at IS NULL)
        OR
        (status='POSTED' AND posted_by_principal_id IS NOT NULL AND posted_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Canonical balanced and immutable finance journal entry authority';
