SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supplier_return_operation (
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
    UNIQUE KEY uk_cloudmold_supplier_return_operation_tenant_idem (tenant_id, idempotency_key),
    UNIQUE KEY uk_cloudmold_supplier_return_operation_source (tenant_id, source_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supplier_return (
    return_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    return_code VARCHAR(64) NOT NULL,
    purchase_order_id VARCHAR(128) NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    submitted_by_principal_id VARCHAR(128) NULL,
    approved_by_principal_id VARCHAR(128) NULL,
    completed_by_principal_id VARCHAR(128) NULL,
    cancelled_by_principal_id VARCHAR(128) NULL,
    submitted_at DATETIME(6) NULL,
    approved_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_supplier_return_code (tenant_id, return_code),
    KEY idx_cloudmold_supplier_return_receipt (tenant_id, receipt_id),
    KEY idx_cloudmold_supplier_return_po (tenant_id, purchase_order_id),
    CONSTRAINT fk_cloudmold_supplier_return_receipt
        FOREIGN KEY (receipt_id) REFERENCES cloudmold_warehouse_procurement_receipt (receipt_id),
    CONSTRAINT chk_cloudmold_supplier_return_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_DISPATCHED', 'DISPATCHED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_cloudmold_supplier_return_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supplier_return_line (
    return_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    return_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    purchase_order_item_id VARCHAR(128) NOT NULL,
    purchase_order_schedule_id VARCHAR(128) NOT NULL,
    quality_decision_id VARCHAR(128) NOT NULL,
    decision_version BIGINT NOT NULL,
    inspection_split_id VARCHAR(128) NOT NULL,
    source_disposition VARCHAR(32) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    location_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    return_quantity DECIMAL(24, 6) NOT NULL,
    dispatched_quantity DECIMAL(24, 6) NOT NULL,
    outstanding_quantity DECIMAL(24, 6) NOT NULL,
    uom_code VARCHAR(64) NOT NULL,
    valuation_policy_id VARCHAR(128) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    valuation_policy_hash CHAR(64) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    currency_code VARCHAR(16) NOT NULL,
    quality_evidence_ref VARCHAR(255) NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_supplier_return_line_no (tenant_id, return_id, line_number),
    KEY idx_cloudmold_supplier_return_line_quality (tenant_id, quality_decision_id, decision_version, inspection_split_id),
    CONSTRAINT fk_cloudmold_supplier_return_line_return
        FOREIGN KEY (return_id) REFERENCES cloudmold_supplier_return (return_id),
    CONSTRAINT fk_cloudmold_supplier_return_line_receipt_line
        FOREIGN KEY (receipt_line_id) REFERENCES cloudmold_warehouse_procurement_receipt_line (receipt_line_id),
    CONSTRAINT chk_cloudmold_supplier_return_line_disposition
        CHECK (source_disposition IN ('ACCEPTED', 'REJECTED', 'QUARANTINED')),
    CONSTRAINT chk_cloudmold_supplier_return_line_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_DISPATCHED', 'DISPATCHED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_cloudmold_supplier_return_line_qty
        CHECK (return_quantity > 0
           AND dispatched_quantity >= 0
           AND outstanding_quantity >= 0
           AND return_quantity = dispatched_quantity + outstanding_quantity),
    CONSTRAINT chk_cloudmold_supplier_return_line_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supplier_return_dispatch_batch (
    batch_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    return_id VARCHAR(128) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    dispatched_by_principal_id VARCHAR(128) NOT NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_supplier_return_batch_no (tenant_id, batch_no),
    KEY idx_cloudmold_supplier_return_batch_return (tenant_id, return_id),
    CONSTRAINT fk_cloudmold_supplier_return_batch_return
        FOREIGN KEY (return_id) REFERENCES cloudmold_supplier_return (return_id),
    CONSTRAINT chk_cloudmold_supplier_return_batch_status CHECK (status IN ('COMPLETED')),
    CONSTRAINT chk_cloudmold_supplier_return_batch_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supplier_return_dispatch_line (
    execution_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    return_id VARCHAR(128) NOT NULL,
    return_line_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    source_disposition VARCHAR(32) NOT NULL,
    dispatched_quantity DECIMAL(24, 6) NOT NULL,
    cumulative_dispatched_quantity DECIMAL(24, 6) NOT NULL,
    outstanding_quantity DECIMAL(24, 6) NOT NULL,
    inventory_operation_id BIGINT NOT NULL,
    ledger_transaction_id BIGINT NOT NULL,
    source_balance_id VARCHAR(128) NULL,
    inventory_aggregate_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_cloudmold_supplier_return_dispatch_line_return (tenant_id, return_id, return_line_id, occurred_at),
    CONSTRAINT fk_cloudmold_supplier_return_dispatch_line_batch
        FOREIGN KEY (batch_id) REFERENCES cloudmold_supplier_return_dispatch_batch (batch_id),
    CONSTRAINT fk_cloudmold_supplier_return_dispatch_line_line
        FOREIGN KEY (return_line_id) REFERENCES cloudmold_supplier_return_line (return_line_id),
    CONSTRAINT chk_cloudmold_supplier_return_dispatch_line_status
        CHECK (status IN ('PARTIALLY_DISPATCHED', 'DISPATCHED')),
    CONSTRAINT chk_cloudmold_supplier_return_dispatch_line_qty
        CHECK (dispatched_quantity > 0
           AND cumulative_dispatched_quantity >= dispatched_quantity
           AND outstanding_quantity >= 0),
    CONSTRAINT chk_cloudmold_supplier_return_dispatch_line_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supplier_return_status_history (
    history_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    business_object_type VARCHAR(32) NOT NULL,
    business_object_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_version BIGINT NOT NULL,
    stage_code VARCHAR(64) NOT NULL,
    stage_label VARCHAR(128) NOT NULL,
    remark VARCHAR(255) NULL,
    changed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_supplier_return_status_version
        (tenant_id, business_object_type, business_object_id, status_version),
    KEY idx_cloudmold_supplier_return_status_object
        (tenant_id, business_object_type, business_object_id, changed_at),
    CONSTRAINT fk_cloudmold_supplier_return_status_operation
        FOREIGN KEY (operation_id) REFERENCES cloudmold_supplier_return_operation (operation_id),
    CONSTRAINT chk_cloudmold_supplier_return_status_object_type
        CHECK (business_object_type IN ('RETURN', 'LINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
