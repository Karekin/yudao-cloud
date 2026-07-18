-- Root release migration sequence: V20260718_60.
CREATE TABLE IF NOT EXISTS cloudmold_customer_service_buyer_feedback (
    feedback_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    ticket_id CHAR(36) NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    customer_principal_id VARCHAR(128) NOT NULL,
    touchpoint_code VARCHAR(64) NOT NULL,
    sentiment_code VARCHAR(16) NOT NULL,
    score_basis_points INT NOT NULL,
    reason_code VARCHAR(64) NULL,
    comment_token VARCHAR(160) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (feedback_id),
    UNIQUE KEY uk_cs_buyer_feedback_tenant_id (tenant_id, feedback_id),
    UNIQUE KEY uk_cs_buyer_feedback_ticket_touchpoint (tenant_id, ticket_id, customer_principal_id, touchpoint_code),
    KEY idx_cs_buyer_feedback_ticket_time (tenant_id, ticket_id, occurred_at),
    CONSTRAINT fk_cs_buyer_feedback_ticket FOREIGN KEY (tenant_id, ticket_id)
        REFERENCES cloudmold_customer_service_ticket (tenant_id, ticket_id),
    CONSTRAINT ck_cs_buyer_feedback_touchpoint CHECK (
        touchpoint_code IN ('TICKET_RESOLUTION', 'CLAIM_COMPENSATION', 'AFTER_SALE_HANDLING')
    ),
    CONSTRAINT ck_cs_buyer_feedback_sentiment CHECK (
        sentiment_code IN ('SATISFIED', 'NEUTRAL', 'DISSATISFIED')
    ),
    CONSTRAINT ck_cs_buyer_feedback_score CHECK (
        (sentiment_code = 'SATISFIED' AND score_basis_points = 10000)
        OR (sentiment_code = 'NEUTRAL' AND score_basis_points = 5000)
        OR (sentiment_code = 'DISSATISFIED' AND score_basis_points = 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
