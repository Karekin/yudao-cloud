CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(128) NULL,
    operation_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token VARCHAR(64) NOT NULL,
    status INT NOT NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json LONGTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_operation_tenant_idem (tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_request (
    request_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    request_code VARCHAR(64) NOT NULL,
    source_business_type VARCHAR(64) NOT NULL,
    source_business_ref VARCHAR(128) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    source_warehouse_id VARCHAR(128) NOT NULL,
    target_warehouse_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    approved_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_request_code (tenant_id, request_code),
    UNIQUE KEY uk_cloudmold_stock_transfer_request_source (tenant_id, source_business_type, source_business_ref),
    CONSTRAINT chk_cloudmold_stock_transfer_request_status
        CHECK (status IN ('APPROVED', 'COMPLETED', 'CANCELED')),
    CONSTRAINT chk_cloudmold_stock_transfer_request_distinct_wh
        CHECK (source_warehouse_id <> target_warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_request_line (
    line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    requested_quantity DECIMAL(24, 6) NOT NULL,
    uom_code VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_request_line_no (tenant_id, request_id, line_number),
    KEY idx_cloudmold_stock_transfer_request_line_request (tenant_id, request_id),
    CONSTRAINT fk_cloudmold_stock_transfer_request_line_request
        FOREIGN KEY (request_id) REFERENCES cloudmold_stock_transfer_request (request_id),
    CONSTRAINT chk_cloudmold_stock_transfer_request_line_qty
        CHECK (requested_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_order (
    order_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    order_code VARCHAR(64) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    source_warehouse_id VARCHAR(128) NOT NULL,
    target_warehouse_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    prepared_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_order_code (tenant_id, order_code),
    UNIQUE KEY uk_cloudmold_stock_transfer_order_request (tenant_id, request_id),
    CONSTRAINT fk_cloudmold_stock_transfer_order_request
        FOREIGN KEY (request_id) REFERENCES cloudmold_stock_transfer_request (request_id),
    CONSTRAINT chk_cloudmold_stock_transfer_order_status
        CHECK (status IN ('PREPARE', 'RELEASED', 'IN_TRANSIT', 'COMPLETED', 'CANCELED')),
    CONSTRAINT chk_cloudmold_stock_transfer_order_distinct_wh
        CHECK (source_warehouse_id <> target_warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_order_line (
    line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    requested_quantity DECIMAL(24, 6) NOT NULL,
    uom_code VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_order_line_no (tenant_id, order_id, line_number),
    KEY idx_cloudmold_stock_transfer_order_line_order (tenant_id, order_id),
    CONSTRAINT fk_cloudmold_stock_transfer_order_line_order
        FOREIGN KEY (order_id) REFERENCES cloudmold_stock_transfer_order (order_id),
    CONSTRAINT chk_cloudmold_stock_transfer_order_line_qty
        CHECK (requested_quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_status_history (
    history_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    business_object_type VARCHAR(32) NOT NULL,
    business_object_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_version BIGINT NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    stage_label VARCHAR(128) NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_status_version
        (tenant_id, business_object_type, business_object_id, status_version),
    KEY idx_cloudmold_stock_transfer_status_object
        (tenant_id, business_object_type, business_object_id, changed_at),
    CONSTRAINT fk_cloudmold_stock_transfer_status_operation
        FOREIGN KEY (operation_id) REFERENCES cloudmold_stock_transfer_operation (operation_id),
    CONSTRAINT chk_cloudmold_stock_transfer_status_object_type
        CHECK (business_object_type IN ('REQUEST', 'ORDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
