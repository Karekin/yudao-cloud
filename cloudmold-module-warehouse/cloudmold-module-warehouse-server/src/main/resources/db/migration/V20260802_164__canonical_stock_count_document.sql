CREATE TABLE IF NOT EXISTS cloudmold_stock_count_operation (
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
    UNIQUE KEY uk_cloudmold_stock_count_operation_tenant_idem (tenant_id, idempotency_key),
    UNIQUE KEY uk_cloudmold_stock_count_operation_tenant_source (tenant_id, source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_count (
    stock_count_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    stock_count_code VARCHAR(64) NOT NULL,
    count_mode VARCHAR(32) NOT NULL,
    scope_type VARCHAR(64) NOT NULL,
    scope_label VARCHAR(255) NOT NULL,
    source_business_type VARCHAR(64) NULL,
    source_business_ref VARCHAR(128) NULL,
    reason_code VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    freeze_ledger_transaction_id BIGINT NOT NULL,
    freeze_captured_at DATETIME(6) NULL,
    line_count INT NOT NULL,
    counted_line_count INT NOT NULL,
    difference_line_count INT NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_count_code (tenant_id, stock_count_code),
    UNIQUE KEY uk_cloudmold_stock_count_source (tenant_id, source_business_type, source_business_ref),
    CONSTRAINT chk_cloudmold_stock_count_mode
        CHECK (count_mode IN ('OPEN_COUNT', 'BLIND_COUNT')),
    CONSTRAINT chk_cloudmold_stock_count_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'COUNTING', 'COUNTED', 'DIFFERENCE_APPROVED', 'ADJUSTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_cloudmold_stock_count_line_counts
        CHECK (line_count >= 0 AND counted_line_count >= 0 AND difference_line_count >= 0
               AND counted_line_count <= line_count AND difference_line_count <= counted_line_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_count_line (
    line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    location_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    stock_status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    balance_id VARCHAR(128) NULL,
    book_on_hand_quantity DECIMAL(24, 6) NOT NULL,
    book_reserved_quantity DECIMAL(24, 6) NOT NULL,
    book_in_transit_quantity DECIMAL(24, 6) NOT NULL,
    book_available_quantity DECIMAL(24, 6) NOT NULL,
    book_aggregate_version BIGINT NOT NULL,
    counted_on_hand_quantity DECIMAL(24, 6) NULL,
    difference_quantity DECIMAL(24, 6) NULL,
    count_status VARCHAR(32) NOT NULL,
    counted_by_principal_id VARCHAR(128) NULL,
    counted_at DATETIME(6) NULL,
    adjustment_id VARCHAR(128) NULL,
    adjustment_ledger_transaction_id BIGINT NULL,
    adjusted_aggregate_version BIGINT NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL,
    freeze_captured_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_count_line_no (tenant_id, stock_count_id, line_number),
    KEY idx_cloudmold_stock_count_line_stock_count (tenant_id, stock_count_id),
    KEY idx_cloudmold_stock_count_line_balance (tenant_id, balance_id),
    CONSTRAINT fk_cloudmold_stock_count_line_stock_count
        FOREIGN KEY (stock_count_id) REFERENCES cloudmold_stock_count (stock_count_id),
    CONSTRAINT chk_cloudmold_stock_count_line_status
        CHECK (count_status IN ('DRAFT', 'SUBMITTED', 'COUNTED', 'ADJUSTED', 'CANCELLED')),
    CONSTRAINT chk_cloudmold_stock_count_line_qty
        CHECK (book_on_hand_quantity >= 0 AND book_reserved_quantity >= 0 AND book_in_transit_quantity >= 0
               AND book_available_quantity >= 0 AND (counted_on_hand_quantity IS NULL OR counted_on_hand_quantity >= 0)),
    CONSTRAINT chk_cloudmold_stock_count_line_number
        CHECK (line_number > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_count_execution_batch (
    batch_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    line_count INT NOT NULL,
    counted_by_principal_id VARCHAR(128) NOT NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_count_execution_batch_no (tenant_id, batch_no),
    KEY idx_cloudmold_stock_count_execution_batch_stock_count (tenant_id, stock_count_id),
    CONSTRAINT fk_cloudmold_stock_count_execution_batch_stock_count
        FOREIGN KEY (stock_count_id) REFERENCES cloudmold_stock_count (stock_count_id),
    CONSTRAINT chk_cloudmold_stock_count_execution_batch_status
        CHECK (status IN ('RECORDED')),
    CONSTRAINT chk_cloudmold_stock_count_execution_batch_line_count
        CHECK (line_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_count_execution_line (
    execution_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    stock_count_line_id VARCHAR(128) NOT NULL,
    counted_on_hand_quantity DECIMAL(24, 6) NOT NULL,
    difference_quantity DECIMAL(24, 6) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_cloudmold_stock_count_execution_line_batch (tenant_id, batch_id),
    KEY idx_cloudmold_stock_count_execution_line_line (tenant_id, stock_count_line_id),
    CONSTRAINT fk_cloudmold_stock_count_execution_line_batch
        FOREIGN KEY (batch_id) REFERENCES cloudmold_stock_count_execution_batch (batch_id),
    CONSTRAINT fk_cloudmold_stock_count_execution_line_line
        FOREIGN KEY (stock_count_line_id) REFERENCES cloudmold_stock_count_line (line_id),
    CONSTRAINT chk_cloudmold_stock_count_execution_line_qty
        CHECK (counted_on_hand_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_count_difference_approval (
    approval_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    approval_type VARCHAR(32) NOT NULL,
    approved_by_principal_id VARCHAR(128) NOT NULL,
    total_book_on_hand_quantity DECIMAL(24, 6) NOT NULL,
    total_counted_on_hand_quantity DECIMAL(24, 6) NOT NULL,
    total_difference_quantity DECIMAL(24, 6) NOT NULL,
    remark VARCHAR(255) NULL,
    approved_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_cloudmold_stock_count_difference_approval_stock_count (tenant_id, stock_count_id),
    CONSTRAINT fk_cloudmold_stock_count_difference_approval_stock_count
        FOREIGN KEY (stock_count_id) REFERENCES cloudmold_stock_count (stock_count_id),
    CONSTRAINT chk_cloudmold_stock_count_difference_approval_type
        CHECK (approval_type IN ('DIFFERENCE_APPROVAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_count_status_history (
    history_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_version BIGINT NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    stage_label VARCHAR(128) NOT NULL,
    changed_by_principal_id VARCHAR(128) NOT NULL,
    remark VARCHAR(255) NULL,
    changed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_count_status_version (tenant_id, stock_count_id, status_version),
    KEY idx_cloudmold_stock_count_status_history_stock_count (tenant_id, stock_count_id, changed_at),
    CONSTRAINT fk_cloudmold_stock_count_status_history_operation
        FOREIGN KEY (operation_id) REFERENCES cloudmold_stock_count_operation (operation_id),
    CONSTRAINT fk_cloudmold_stock_count_status_history_stock_count
        FOREIGN KEY (stock_count_id) REFERENCES cloudmold_stock_count (stock_count_id),
    CONSTRAINT chk_cloudmold_stock_count_status_history_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'COUNTING', 'COUNTED', 'DIFFERENCE_APPROVED', 'ADJUSTED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_inventory_stock_count_adjustment_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(128) NULL,
    stock_count_line_id VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token VARCHAR(64) NOT NULL,
    status INT NOT NULL,
    adjustment_id VARCHAR(128) NULL,
    result_json LONGTEXT NULL,
    ledger_transaction_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cm_inv_count_adjustment_op_idem (tenant_id, idempotency_key),
    UNIQUE KEY uk_cm_inv_count_adjustment_op_source (tenant_id, source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_inventory_stock_count_adjustment_v3 (
    adjustment_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    stock_count_id VARCHAR(128) NOT NULL,
    stock_count_line_id VARCHAR(128) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    location_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    stock_status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    balance_id VARCHAR(128) NOT NULL,
    book_on_hand_quantity DECIMAL(24, 6) NOT NULL,
    counted_on_hand_quantity DECIMAL(24, 6) NOT NULL,
    adjustment_quantity DECIMAL(24, 6) NOT NULL,
    ledger_transaction_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_inventory_stock_count_adjustment_line (tenant_id, stock_count_line_id),
    KEY idx_cloudmold_inventory_stock_count_adjustment_stock_count (tenant_id, stock_count_id),
    KEY idx_cloudmold_inventory_stock_count_adjustment_balance (tenant_id, balance_id),
    CONSTRAINT fk_cloudmold_inventory_stock_count_adjustment_stock_count
        FOREIGN KEY (stock_count_id) REFERENCES cloudmold_stock_count (stock_count_id),
    CONSTRAINT fk_cloudmold_inventory_stock_count_adjustment_line
        FOREIGN KEY (stock_count_line_id) REFERENCES cloudmold_stock_count_line (line_id),
    CONSTRAINT fk_cloudmold_inventory_stock_count_adjustment_ledger_tx
        FOREIGN KEY (ledger_transaction_id) REFERENCES cloudmold_inventory_ledger_transaction_v3 (ledger_transaction_id),
    CONSTRAINT chk_cloudmold_inventory_stock_count_adjustment_status
        CHECK (status IN ('APPLIED')),
    CONSTRAINT chk_cloudmold_inventory_stock_count_adjustment_qty
        CHECK (book_on_hand_quantity >= 0 AND counted_on_hand_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
