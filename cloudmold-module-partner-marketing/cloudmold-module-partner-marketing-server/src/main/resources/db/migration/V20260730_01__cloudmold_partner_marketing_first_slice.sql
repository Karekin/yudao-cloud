CREATE TABLE cloudmold_partner_marketing_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_partner_marketing_operation (tenant_id, idempotency_key),
    CONSTRAINT ck_partner_marketing_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB COMMENT='Idempotent canonical partner marketing command envelope';

CREATE TABLE cloudmold_partner_marketing_case (
    case_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    case_code VARCHAR(64) NOT NULL,
    creator_principal_id VARCHAR(128) NOT NULL,
    candidate_handle VARCHAR(128) NOT NULL,
    platform_code VARCHAR(64) NOT NULL,
    region_code VARCHAR(64) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    cooperation_model VARCHAR(16) NULL,
    brief_budget_amount_minor BIGINT NULL,
    currency_code CHAR(3) NULL,
    risk_level VARCHAR(16) NULL,
    risk_evidence_sha256 CHAR(64) NULL,
    qualification_note VARCHAR(512) NULL,
    outreach_channel_code VARCHAR(64) NULL,
    outreach_external_ref VARCHAR(128) NULL,
    campaign_id VARCHAR(128) NULL,
    listing_id VARCHAR(128) NULL,
    brief_summary VARCHAR(512) NULL,
    brief_evidence_sha256 CHAR(64) NULL,
    brief_submitted_by_principal_id VARCHAR(128) NULL,
    brief_approved_by_principal_id VARCHAR(128) NULL,
    content_summary VARCHAR(512) NULL,
    content_evidence_sha256 CHAR(64) NULL,
    content_submitted_by_principal_id VARCHAR(128) NULL,
    content_approved_by_principal_id VARCHAR(128) NULL,
    external_publish_ref VARCHAR(128) NULL,
    external_publish_url VARCHAR(512) NULL,
    disclosure_label VARCHAR(128) NULL,
    publish_evidence_sha256 CHAR(64) NULL,
    disclosure_verified BIT NOT NULL DEFAULT b'0',
    publish_verified_by_principal_id VARCHAR(128) NULL,
    attributed_order_count INT NULL,
    attributed_order_id VARCHAR(128) NULL,
    attributed_payment_id VARCHAR(128) NULL,
    attribution_source_ref VARCHAR(128) NULL,
    gross_settlement_amount_minor BIGINT NULL,
    platform_fee_amount_minor BIGINT NULL,
    tax_withholding_amount_minor BIGINT NULL,
    net_payable_amount_minor BIGINT NULL,
    attribution_evidence_sha256 CHAR(64) NULL,
    settlement_requested_by_principal_id VARCHAR(128) NULL,
    settlement_approved_by_principal_id VARCHAR(128) NULL,
    settlement_paid_by_principal_id VARCHAR(128) NULL,
    settlement_reference VARCHAR(128) NULL,
    settlement_approval_evidence_sha256 CHAR(64) NULL,
    settlement_payment_evidence_sha256 CHAR(64) NULL,
    closed_by_principal_id VARCHAR(128) NULL,
    status VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    qualified_at DATETIME(6) NULL,
    outreach_started_at DATETIME(6) NULL,
    brief_submitted_at DATETIME(6) NULL,
    brief_approved_at DATETIME(6) NULL,
    content_submitted_at DATETIME(6) NULL,
    content_approved_at DATETIME(6) NULL,
    publish_verified_at DATETIME(6) NULL,
    attribution_reconciled_at DATETIME(6) NULL,
    settlement_requested_at DATETIME(6) NULL,
    settlement_approved_at DATETIME(6) NULL,
    settlement_paid_at DATETIME(6) NULL,
    closed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_partner_marketing_case_id (tenant_id, case_id),
    UNIQUE KEY uk_partner_marketing_case_code (tenant_id, case_code),
    KEY idx_partner_marketing_status (tenant_id, status, updated_at),
    CONSTRAINT ck_partner_marketing_case_status CHECK (
        status IN ('CANDIDATE','QUALIFIED','OUTREACH_ACTIVE','BRIEF_PENDING_APPROVAL','BRIEF_APPROVED',
                   'CONTENT_PENDING_APPROVAL','CONTENT_APPROVED','PUBLISHED_DISCLOSURE_VERIFIED',
                   'SETTLEMENT_PENDING_APPROVAL','SETTLEMENT_APPROVED','SETTLEMENT_PAID','CLOSED')
    ),
    CONSTRAINT ck_partner_marketing_cooperation_model CHECK (
        cooperation_model IS NULL OR cooperation_model IN ('FIXED_FEE','CPS')
    ),
    CONSTRAINT ck_partner_marketing_budget CHECK (
        cooperation_model IS NULL
        OR (brief_budget_amount_minor IS NOT NULL AND brief_budget_amount_minor > 0 AND currency_code REGEXP '^[A-Z]{3}$')
    ),
    CONSTRAINT ck_partner_marketing_risk_level CHECK (
        risk_level IS NULL OR risk_level IN ('LOW','MEDIUM','HIGH')
    ),
    CONSTRAINT ck_partner_marketing_attribution_refs CHECK (
        (attributed_order_id IS NULL AND attributed_payment_id IS NULL AND attribution_source_ref IS NULL
         AND attributed_order_count IS NULL)
        OR (attributed_order_id IS NOT NULL AND attributed_payment_id IS NOT NULL
            AND attribution_source_ref IS NOT NULL AND attributed_order_count > 0)
    ),
    CONSTRAINT ck_partner_marketing_amount_conservation CHECK (
        gross_settlement_amount_minor IS NULL
        OR (platform_fee_amount_minor IS NOT NULL AND tax_withholding_amount_minor IS NOT NULL
            AND net_payable_amount_minor IS NOT NULL AND net_payable_amount_minor > 0
            AND gross_settlement_amount_minor > 0
            AND gross_settlement_amount_minor = platform_fee_amount_minor + tax_withholding_amount_minor + net_payable_amount_minor)
    ),
    CONSTRAINT ck_partner_marketing_maker_checker CHECK (
        settlement_approved_by_principal_id IS NULL
        OR (settlement_approved_by_principal_id <> creator_principal_id
            AND (settlement_requested_by_principal_id IS NULL
                 OR settlement_approved_by_principal_id <> settlement_requested_by_principal_id))
    ),
    CONSTRAINT ck_partner_marketing_closed_invariants CHECK (
        status <> 'CLOSED'
        OR (closed_at IS NOT NULL AND settlement_paid_at IS NOT NULL AND settlement_reference IS NOT NULL
            AND settlement_approval_evidence_sha256 IS NOT NULL
            AND settlement_payment_evidence_sha256 IS NOT NULL
            AND attributed_order_id IS NOT NULL AND attributed_payment_id IS NOT NULL
            AND attribution_source_ref IS NOT NULL AND disclosure_verified = b'1'
            AND external_publish_ref IS NOT NULL AND external_publish_url IS NOT NULL
            AND publish_verified_at IS NOT NULL)
    ),
    CONSTRAINT ck_partner_marketing_version CHECK (version > 0)
) ENGINE=InnoDB COMMENT='Canonical partner marketing workflow owned by KOL / influencer operator';

CREATE TABLE cloudmold_partner_marketing_case_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    case_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(64) NULL,
    to_status VARCHAR(64) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NULL,
    evidence_sha256 CHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_partner_marketing_history_case_version (tenant_id, case_id, aggregate_version),
    KEY idx_partner_marketing_history_status (tenant_id, case_id, to_status, created_at),
    CONSTRAINT fk_partner_marketing_history_case FOREIGN KEY (tenant_id, case_id)
        REFERENCES cloudmold_partner_marketing_case (tenant_id, case_id),
    CONSTRAINT ck_partner_marketing_history_to_status CHECK (
        to_status IN ('CANDIDATE','QUALIFIED','OUTREACH_ACTIVE','BRIEF_PENDING_APPROVAL','BRIEF_APPROVED',
                      'CONTENT_PENDING_APPROVAL','CONTENT_APPROVED','PUBLISHED_DISCLOSURE_VERIFIED',
                      'SETTLEMENT_PENDING_APPROVAL','SETTLEMENT_APPROVED','SETTLEMENT_PAID','CLOSED')
    ),
    CONSTRAINT ck_partner_marketing_history_version CHECK (aggregate_version > 0)
) ENGINE=InnoDB COMMENT='Append-only canonical partner marketing workflow status history';
