ALTER TABLE cloudmold_stock_transfer_order_line
    ADD COLUMN movement_group_id VARCHAR(36) NULL AFTER canonical_sku_id,
    ADD COLUMN outbound_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 AFTER requested_quantity,
    ADD COLUMN received_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000 AFTER outbound_quantity,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'PREPARE' AFTER uom_code,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER status,
    ADD CONSTRAINT chk_cloudmold_stock_transfer_order_line_outbound
        CHECK (outbound_quantity >= 0 AND outbound_quantity <= requested_quantity),
    ADD CONSTRAINT chk_cloudmold_stock_transfer_order_line_received
        CHECK (received_quantity >= 0 AND received_quantity <= outbound_quantity),
    ADD CONSTRAINT chk_cloudmold_stock_transfer_order_line_status
        CHECK (status IN ('PREPARE', 'PARTIAL_OUTBOUND', 'FULL_OUTBOUND', 'PARTIAL_RECEIVED', 'FULL_RECEIVED'));

ALTER TABLE cloudmold_stock_transfer_status_history
    DROP CHECK chk_cloudmold_stock_transfer_status_object_type,
    ADD COLUMN remark VARCHAR(255) NULL AFTER stage_label,
    ADD CONSTRAINT chk_cloudmold_stock_transfer_status_object_type
        CHECK (business_object_type IN ('REQUEST', 'ORDER', 'ORDER_LINE'));

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_execution_batch (
    batch_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    order_id VARCHAR(128) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    batch_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_stock_transfer_execution_batch_no (tenant_id, order_id, batch_type, batch_no),
    CONSTRAINT fk_cloudmold_stock_transfer_execution_batch_order
        FOREIGN KEY (order_id) REFERENCES cloudmold_stock_transfer_order (order_id),
    CONSTRAINT chk_cloudmold_stock_transfer_execution_batch_type
        CHECK (batch_type IN ('OUTBOUND', 'RECEIPT')),
    CONSTRAINT chk_cloudmold_stock_transfer_execution_batch_status
        CHECK (status IN ('COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_stock_transfer_execution_line (
    execution_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    order_id VARCHAR(128) NOT NULL,
    order_line_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    outbound_execution_line_id VARCHAR(128) NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    movement_group_id VARCHAR(36) NOT NULL,
    executed_quantity DECIMAL(24, 6) NOT NULL,
    received_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000,
    cumulative_dispatched_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000,
    cumulative_received_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000,
    outstanding_quantity DECIMAL(24, 6) NOT NULL DEFAULT 0.000000,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    lot_id VARCHAR(128) NULL,
    source_location_id VARCHAR(128) NULL,
    source_stock_status VARCHAR(32) NULL,
    source_quality_status VARCHAR(32) NULL,
    target_location_id VARCHAR(128) NULL,
    target_stock_status VARCHAR(32) NULL,
    target_quality_status VARCHAR(32) NULL,
    dispatch_ledger_transaction_id BIGINT NULL,
    receive_ledger_transaction_id BIGINT NULL,
    remark VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_cloudmold_stock_transfer_execution_line_batch (tenant_id, batch_id, line_number),
    KEY idx_cloudmold_stock_transfer_execution_line_order (tenant_id, order_id, order_line_id),
    KEY idx_cloudmold_stock_transfer_execution_line_outbound (tenant_id, outbound_execution_line_id),
    CONSTRAINT fk_cloudmold_stock_transfer_execution_line_batch
        FOREIGN KEY (batch_id) REFERENCES cloudmold_stock_transfer_execution_batch (batch_id),
    CONSTRAINT fk_cloudmold_stock_transfer_execution_line_order
        FOREIGN KEY (order_id) REFERENCES cloudmold_stock_transfer_order (order_id),
    CONSTRAINT fk_cloudmold_stock_transfer_execution_line_order_line
        FOREIGN KEY (order_line_id) REFERENCES cloudmold_stock_transfer_order_line (line_id),
    CONSTRAINT chk_cloudmold_stock_transfer_execution_line_qty
        CHECK (executed_quantity > 0 AND received_quantity >= 0 AND received_quantity <= executed_quantity
            AND cumulative_dispatched_quantity >= 0 AND cumulative_received_quantity >= 0
            AND outstanding_quantity >= 0),
    CONSTRAINT chk_cloudmold_stock_transfer_execution_line_status
        CHECK (status IN ('IN_TRANSIT', 'PARTIAL_RECEIVED', 'FULL_RECEIVED', 'RECEIVED')),
    CONSTRAINT chk_cloudmold_stock_transfer_execution_line_stock
        CHECK ((source_stock_status IS NULL OR source_stock_status IN ('SELLABLE', 'NON_SELLABLE'))
            AND (target_stock_status IS NULL OR target_stock_status IN ('SELLABLE', 'NON_SELLABLE'))),
    CONSTRAINT chk_cloudmold_stock_transfer_execution_line_quality
        CHECK ((source_quality_status IS NULL OR source_quality_status IN ('PENDING_QC', 'QUALIFIED', 'DAMAGED', 'REJECTED'))
            AND (target_quality_status IS NULL OR target_quality_status IN ('PENDING_QC', 'QUALIFIED', 'DAMAGED', 'REJECTED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
