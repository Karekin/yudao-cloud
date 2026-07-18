-- Root release migration sequence: V20260718_63.
CREATE TABLE IF NOT EXISTS cloudmold_yudao_purchase_promise (
    promise_id varchar(36) NOT NULL,
    promise_key varchar(96) NOT NULL,
    tenant_id bigint NOT NULL,
    purchase_order_id bigint NOT NULL,
    purchase_order_no varchar(64) NOT NULL,
    purchase_order_line_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    product_id bigint NOT NULL,
    product_unit_id bigint NOT NULL,
    ordered_quantity decimal(24,6) NOT NULL,
    promised_receipt_at datetime(6) NOT NULL,
    promise_timezone varchar(64) NOT NULL,
    grace_minutes int NOT NULL DEFAULT 0,
    pause_minutes int NOT NULL DEFAULT 0,
    promise_frozen_at datetime(6) NOT NULL,
    status varchar(16) NOT NULL,
    version bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    reason varchar(255) DEFAULT NULL,
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (promise_id),
    UNIQUE KEY uq_cloudmold_yudao_purchase_promise_line (tenant_id, purchase_order_line_id),
    UNIQUE KEY uq_cloudmold_yudao_purchase_promise_key (tenant_id, promise_key),
    KEY idx_cloudmold_yudao_purchase_promise_due (tenant_id, promised_receipt_at),
    CONSTRAINT ck_cloudmold_yudao_purchase_promise_status
        CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CONSTRAINT ck_cloudmold_yudao_purchase_promise_grace
        CHECK (grace_minutes >= 0),
    CONSTRAINT ck_cloudmold_yudao_purchase_promise_pause
        CHECK (pause_minutes >= 0),
    CONSTRAINT ck_cloudmold_yudao_purchase_promise_version
        CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='CloudMold governed procurement promise snapshot per ERP purchase order line';
