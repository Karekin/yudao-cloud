CREATE TABLE IF NOT EXISTS cloudmold_risk_order_case (
    order_risk_case_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    case_id CHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36) NULL,
    risk_type VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (order_risk_case_id),
    UNIQUE KEY uk_risk_order_case_tenant_id (tenant_id, order_risk_case_id),
    UNIQUE KEY uk_risk_order_case_review (tenant_id, case_id),
    KEY idx_risk_order_case_order (tenant_id, order_id),
    KEY idx_risk_order_case_payment (tenant_id, payment_id),
    CONSTRAINT fk_risk_order_case_review FOREIGN KEY (tenant_id, case_id)
        REFERENCES cloudmold_risk_review_case (tenant_id, case_id),
    CONSTRAINT fk_risk_order_case_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES cloudmold_order_header (tenant_id, order_id),
    CONSTRAINT fk_risk_order_case_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES cloudmold_payment (tenant_id, payment_id),
    CONSTRAINT ck_risk_order_case_type CHECK (
        risk_type IN ('FRAUD', 'ABUSE', 'PAYMENT_RISK', 'POLICY_VIOLATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Constrained order context for a human risk review case';

CREATE TABLE IF NOT EXISTS cloudmold_risk_payment_dispute (
    dispute_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36) NOT NULL,
    case_id CHAR(36) NULL,
    decision_id CHAR(36) NULL,
    dispute_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    external_ref VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (dispute_id),
    UNIQUE KEY uk_risk_dispute_tenant_id (tenant_id, dispute_id),
    UNIQUE KEY uk_risk_dispute_external_ref (tenant_id, payment_id, external_ref),
    KEY idx_risk_dispute_order (tenant_id, order_id, status),
    CONSTRAINT fk_risk_dispute_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES cloudmold_order_header (tenant_id, order_id),
    CONSTRAINT fk_risk_dispute_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES cloudmold_payment (tenant_id, payment_id),
    CONSTRAINT fk_risk_dispute_review FOREIGN KEY (tenant_id, case_id)
        REFERENCES cloudmold_risk_review_case (tenant_id, case_id),
    CONSTRAINT fk_risk_dispute_decision FOREIGN KEY (tenant_id, decision_id)
        REFERENCES cloudmold_risk_decision (tenant_id, decision_id),
    CONSTRAINT ck_risk_dispute_type CHECK (dispute_type IN ('CHARGEBACK', 'PAYMENT_DISPUTE')),
    CONSTRAINT ck_risk_dispute_status CHECK (status IN ('OPEN', 'WON', 'LOST', 'REVERSED', 'CANCELLED')),
    CONSTRAINT ck_risk_dispute_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_risk_dispute_currency CHECK (currency_code = 'CNY'),
    CONSTRAINT ck_risk_dispute_version CHECK (version > 0),
    CONSTRAINT ck_risk_dispute_resolution CHECK (
        (status = 'OPEN' AND resolved_at IS NULL)
        OR (status <> 'OPEN' AND resolved_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Constrained payment dispute lifecycle for chargeback and dispute facts';

CREATE TABLE IF NOT EXISTS cloudmold_risk_loss_entry (
    loss_entry_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36) NULL,
    dispute_id CHAR(36) NULL,
    decision_id CHAR(36) NULL,
    entry_type VARCHAR(32) NOT NULL,
    signed_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    external_ref VARCHAR(128) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (loss_entry_id),
    UNIQUE KEY uk_risk_loss_tenant_id (tenant_id, loss_entry_id),
    KEY idx_risk_loss_order (tenant_id, order_id, occurred_at),
    KEY idx_risk_loss_payment (tenant_id, payment_id, occurred_at),
    CONSTRAINT fk_risk_loss_order FOREIGN KEY (tenant_id, order_id)
        REFERENCES cloudmold_order_header (tenant_id, order_id),
    CONSTRAINT fk_risk_loss_payment FOREIGN KEY (tenant_id, payment_id)
        REFERENCES cloudmold_payment (tenant_id, payment_id),
    CONSTRAINT fk_risk_loss_dispute FOREIGN KEY (tenant_id, dispute_id)
        REFERENCES cloudmold_risk_payment_dispute (tenant_id, dispute_id),
    CONSTRAINT fk_risk_loss_decision FOREIGN KEY (tenant_id, decision_id)
        REFERENCES cloudmold_risk_decision (tenant_id, decision_id),
    CONSTRAINT ck_risk_loss_type CHECK (
        entry_type IN ('CONFIRMED_RISK_LOSS', 'CHARGEBACK_LOSS', 'SERVICE_COMPENSATION_LOSS', 'REVERSAL')),
    CONSTRAINT ck_risk_loss_amount CHECK (signed_amount_minor <> 0),
    CONSTRAINT ck_risk_loss_currency CHECK (currency_code = 'CNY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable risk loss ledger entries in signed CNY minor units';
