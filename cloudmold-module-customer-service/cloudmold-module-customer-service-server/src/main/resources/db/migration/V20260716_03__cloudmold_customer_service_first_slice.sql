CREATE TABLE IF NOT EXISTS cloudmold_customer_service_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status SMALLINT NOT NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_cs_operation_tenant_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_cs_operation_tenant_id (tenant_id, operation_id),
    CONSTRAINT ck_cs_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_ticket (
    ticket_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ticket_no VARCHAR(64) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    customer_principal_id VARCHAR(128) NOT NULL,
    channel_code VARCHAR(16) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    assigned_agent_principal_id VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id),
    UNIQUE KEY uk_cs_ticket_tenant_no (tenant_id, ticket_no),
    UNIQUE KEY uk_cs_ticket_tenant_id (tenant_id, ticket_id),
    KEY idx_cs_ticket_customer_status (tenant_id, customer_principal_id, status),
    KEY idx_cs_ticket_agent_status (tenant_id, assigned_agent_principal_id, status),
    CONSTRAINT ck_cs_ticket_channel CHECK (channel_code IN ('APP', 'WEB', 'PHONE', 'INTERNAL')),
    CONSTRAINT ck_cs_ticket_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_cs_ticket_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT ck_cs_ticket_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_ticket_order_link (
    link_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    reference_source_system VARCHAR(64) NOT NULL,
    reference_type VARCHAR(16) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (link_id),
    UNIQUE KEY uk_cs_link_business (tenant_id, ticket_id, reference_type, reference_id),
    UNIQUE KEY uk_cs_link_tenant_id (tenant_id, link_id),
    KEY idx_cs_link_reference (tenant_id, reference_type, reference_id),
    CONSTRAINT fk_cs_link_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT ck_cs_link_qualified CHECK (
        (reference_type = 'ORDER' AND reference_source_system = 'cloudmold-order')
        OR (reference_type = 'AFTER_SALE' AND reference_source_system = 'cloudmold-aftersales')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_message (
    message_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    sender_type VARCHAR(16) NOT NULL,
    sender_principal_id VARCHAR(128) NULL,
    message_type VARCHAR(16) NOT NULL,
    content_token VARCHAR(160) NOT NULL,
    attachment_count INT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (message_id),
    UNIQUE KEY uk_cs_message_tenant_id (tenant_id, message_id),
    UNIQUE KEY uk_cs_message_ticket_id (tenant_id, ticket_id, message_id),
    KEY idx_cs_message_ticket_time (tenant_id, ticket_id, occurred_at),
    CONSTRAINT fk_cs_message_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT ck_cs_message_direction CHECK (direction IN ('INBOUND', 'OUTBOUND', 'INTERNAL')),
    CONSTRAINT ck_cs_message_sender CHECK (sender_type IN ('CUSTOMER', 'AGENT', 'SYSTEM')),
    CONSTRAINT ck_cs_message_type CHECK (message_type IN ('TEXT', 'IMAGE', 'FILE', 'SYSTEM_NOTE')),
    CONSTRAINT ck_cs_message_attachment_count CHECK (attachment_count >= 0),
    CONSTRAINT ck_cs_message_sender_id CHECK (sender_type = 'SYSTEM' OR sender_principal_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_attachment (
    attachment_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    message_id CHAR(36) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    media_type VARCHAR(96) NOT NULL,
    object_token VARCHAR(160) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    malware_scan_status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attachment_id),
    UNIQUE KEY uk_cs_attachment_tenant_id (tenant_id, attachment_id),
    KEY idx_cs_attachment_ticket_message (tenant_id, ticket_id, message_id),
    CONSTRAINT fk_cs_attachment_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT fk_cs_attachment_message FOREIGN KEY (tenant_id, ticket_id, message_id)
        REFERENCES cloudmold_customer_service_message (tenant_id, ticket_id, message_id),
    CONSTRAINT ck_cs_attachment_size CHECK (size_bytes BETWEEN 0 AND 20000000),
    CONSTRAINT ck_cs_attachment_scan CHECK (malware_scan_status IN ('PENDING', 'CLEAN', 'BLOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_quality_review (
    review_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    reviewer_principal_id VARCHAR(128) NOT NULL,
    score_basis_points INT NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_cs_review_tenant_id (tenant_id, review_id),
    KEY idx_cs_review_ticket_time (tenant_id, ticket_id, created_at),
    CONSTRAINT fk_cs_review_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT ck_cs_review_score CHECK (score_basis_points BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_claim (
    claim_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    claim_code VARCHAR(64) NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    claim_type VARCHAR(32) NOT NULL,
    order_ref VARCHAR(128) NULL,
    after_sale_ref VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL,
    requested_amount_minor BIGINT NOT NULL,
    approved_amount_minor BIGINT NOT NULL,
    paid_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    compensation_entry_id CHAR(36) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (claim_id),
    UNIQUE KEY uk_cs_claim_tenant_code (tenant_id, claim_code),
    UNIQUE KEY uk_cs_claim_tenant_id (tenant_id, claim_id),
    KEY idx_cs_claim_ticket_status (tenant_id, ticket_id, status),
    CONSTRAINT fk_cs_claim_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT ck_cs_claim_type CHECK (claim_type IN ('SERVICE_COMPENSATION', 'LOGISTICS_DAMAGE', 'PRICE_PROTECTION')),
    CONSTRAINT ck_cs_claim_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PAID')),
    CONSTRAINT ck_cs_claim_reference CHECK (order_ref IS NOT NULL OR after_sale_ref IS NOT NULL),
    CONSTRAINT ck_cs_claim_amounts CHECK (
        requested_amount_minor > 0
        AND approved_amount_minor BETWEEN 0 AND requested_amount_minor
        AND paid_amount_minor BETWEEN 0 AND approved_amount_minor
    ),
    CONSTRAINT ck_cs_claim_state_amount CHECK (
        (status = 'REQUESTED' AND approved_amount_minor = 0 AND paid_amount_minor = 0 AND compensation_entry_id IS NULL)
        OR (status = 'APPROVED' AND approved_amount_minor > 0 AND paid_amount_minor = 0 AND compensation_entry_id IS NULL)
        OR (status = 'REJECTED' AND approved_amount_minor = 0 AND paid_amount_minor = 0 AND compensation_entry_id IS NULL)
        OR (status = 'PAID' AND approved_amount_minor > 0 AND paid_amount_minor = approved_amount_minor
            AND compensation_entry_id IS NOT NULL)
    ),
    CONSTRAINT ck_cs_claim_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_compensation_entry (
    compensation_entry_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    claim_id CHAR(36) NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    operation_idempotency_key VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (compensation_entry_id),
    UNIQUE KEY uk_cs_compensation_claim (tenant_id, claim_id),
    UNIQUE KEY uk_cs_compensation_operation (tenant_id, operation_idempotency_key),
    KEY idx_cs_compensation_ticket_time (tenant_id, ticket_id, occurred_at),
    CONSTRAINT fk_cs_compensation_claim FOREIGN KEY (tenant_id, claim_id)
        REFERENCES cloudmold_customer_service_claim (tenant_id, claim_id),
    CONSTRAINT fk_cs_compensation_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT ck_cs_compensation_type CHECK (entry_type = 'SERVICE_COMPENSATION'),
    CONSTRAINT ck_cs_compensation_amount CHECK (amount_minor > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_customer_service_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(16) NOT NULL,
    aggregate_id CHAR(36) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    previous_status VARCHAR(16) NULL,
    current_status VARCHAR(16) NOT NULL,
    operation_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_cs_history_aggregate_version (tenant_id, aggregate_type, aggregate_id, aggregate_version),
    KEY idx_cs_history_operation (tenant_id, operation_id),
    CONSTRAINT fk_cs_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_customer_service_operation (tenant_id, operation_id),
    CONSTRAINT ck_cs_history_type CHECK (aggregate_type IN ('TICKET', 'CLAIM')),
    CONSTRAINT ck_cs_history_version CHECK (aggregate_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
