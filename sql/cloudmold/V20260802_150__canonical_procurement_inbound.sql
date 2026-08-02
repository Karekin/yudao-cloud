ALTER TABLE cloudmold_inbound_operation
    MODIFY COLUMN source_event_id VARCHAR(128) NULL,
    MODIFY COLUMN aggregate_id VARCHAR(128) NULL;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_asn (
    asn_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    asn_no VARCHAR(64) NOT NULL,
    procurement_order_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_warehouse_procurement_asn_no (tenant_id, asn_no),
    CONSTRAINT chk_cloudmold_warehouse_procurement_asn_status
        CHECK (status IN ('DRAFT', 'IN_TRANSIT', 'PARTIAL_RECEIVED', 'RECEIVED_PENDING_QUALITY', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_schedule_fulfillment (
    schedule_fulfillment_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    procurement_order_id VARCHAR(128) NOT NULL,
    procurement_order_item_id VARCHAR(128) NOT NULL,
    delivery_schedule_id VARCHAR(128) NOT NULL,
    ordered_quantity DECIMAL(24, 6) NOT NULL,
    cancelled_quantity DECIMAL(24, 6) NOT NULL,
    allowed_over_receipt_quantity DECIMAL(24, 6) NOT NULL,
    received_quantity DECIMAL(24, 6) NOT NULL,
    pending_quality_quantity DECIMAL(24, 6) NOT NULL,
    accepted_quantity DECIMAL(24, 6) NOT NULL,
    rejected_quantity DECIMAL(24, 6) NOT NULL,
    quarantined_quantity DECIMAL(24, 6) NOT NULL,
    returned_quantity DECIMAL(24, 6) NOT NULL,
    tolerance_policy_version VARCHAR(64) NOT NULL,
    tolerance_policy_hash CHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_warehouse_procurement_schedule_fulfillment
        (tenant_id, procurement_order_item_id, delivery_schedule_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_schedule_fulfillment_qty
        CHECK (ordered_quantity > 0 AND cancelled_quantity >= 0 AND allowed_over_receipt_quantity >= 0
            AND received_quantity >= 0 AND pending_quality_quantity >= 0 AND accepted_quantity >= 0
            AND rejected_quantity >= 0 AND quarantined_quantity >= 0 AND returned_quantity >= 0
            AND received_quantity <= ordered_quantity - cancelled_quantity + allowed_over_receipt_quantity
            AND received_quantity = pending_quality_quantity + accepted_quantity
                + rejected_quantity + quarantined_quantity
            AND returned_quantity <= rejected_quantity + quarantined_quantity),
    CONSTRAINT chk_cloudmold_warehouse_procurement_schedule_tolerance_hash
        CHECK (REGEXP_LIKE(tolerance_policy_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_asn_line (
    asn_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    asn_id VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    procurement_order_id VARCHAR(128) NOT NULL,
    procurement_order_item_id VARCHAR(128) NOT NULL,
    delivery_schedule_id VARCHAR(128) NOT NULL,
    po_release_version BIGINT NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    receipt_location_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    scheduled_quantity DECIMAL(24, 6) NOT NULL,
    allowed_over_receipt_quantity DECIMAL(24, 6) NOT NULL,
    received_quantity DECIMAL(24, 6) NOT NULL,
    pending_quality_quantity DECIMAL(24, 6) NOT NULL,
    fulfillment_version BIGINT NOT NULL,
    valuation_policy_id VARCHAR(128) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    valuation_policy_hash CHAR(64) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    rounding_policy_code VARCHAR(64) NOT NULL,
    tolerance_policy_version VARCHAR(64) NOT NULL,
    tolerance_policy_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_warehouse_procurement_asn_line_no (tenant_id, asn_id, line_no),
    UNIQUE KEY uk_cloudmold_warehouse_procurement_asn_schedule (tenant_id, asn_id, delivery_schedule_id),
    KEY idx_cloudmold_warehouse_procurement_asn_line_item (tenant_id, procurement_order_id, procurement_order_item_id),
    CONSTRAINT fk_cloudmold_warehouse_procurement_asn_line_asn
        FOREIGN KEY (asn_id) REFERENCES cloudmold_warehouse_procurement_asn (asn_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_asn_line_qty
        CHECK (scheduled_quantity > 0 AND allowed_over_receipt_quantity >= 0
            AND received_quantity >= 0 AND pending_quality_quantity >= 0
            AND received_quantity <= scheduled_quantity + allowed_over_receipt_quantity
            AND pending_quality_quantity <= received_quantity),
    CONSTRAINT chk_cloudmold_warehouse_procurement_asn_line_status
        CHECK (status IN ('OPEN', 'PARTIAL_RECEIVED_PENDING_QUALITY', 'FULL_RECEIVED_PENDING_QUALITY')),
    CONSTRAINT chk_cloudmold_warehouse_procurement_asn_line_tolerance_hash
        CHECK (REGEXP_LIKE(tolerance_policy_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_cloudmold_warehouse_procurement_asn_line_valuation
        CHECK (REGEXP_LIKE(valuation_policy_hash, '^[0-9a-f]{64}$', 'c')
            AND unit_cost_amount_minor >= 0 AND REGEXP_LIKE(currency_code, '^[A-Z]{3}$', 'c')
            AND rounding_policy_code = 'HALF_UP')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_receipt (
    receipt_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    receipt_no VARCHAR(64) NOT NULL,
    asn_id VARCHAR(128) NOT NULL,
    procurement_order_id VARCHAR(128) NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_warehouse_procurement_receipt_no (tenant_id, receipt_no),
    KEY idx_cloudmold_warehouse_procurement_receipt_asn (tenant_id, asn_id, created_at),
    CONSTRAINT fk_cloudmold_warehouse_procurement_receipt_asn
        FOREIGN KEY (asn_id) REFERENCES cloudmold_warehouse_procurement_asn (asn_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_status
        CHECK (status IN ('PENDING_QUALITY', 'PARTIAL_QUALITY_DECIDED', 'QUALITY_ACCEPTED',
            'QUALITY_REJECTED', 'QUALITY_QUARANTINED', 'QUALITY_MIXED',
            'PARTIALLY_PUTAWAY', 'PUTAWAY_COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_receipt_line (
    receipt_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    line_no INT NOT NULL,
    asn_line_id VARCHAR(128) NOT NULL,
    procurement_order_id VARCHAR(128) NOT NULL,
    procurement_order_item_id VARCHAR(128) NOT NULL,
    delivery_schedule_id VARCHAR(128) NOT NULL,
    po_release_version BIGINT NOT NULL,
    fulfillment_version_before BIGINT NOT NULL,
    fulfillment_version_after BIGINT NOT NULL,
    supplier_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    receipt_location_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    lot_id VARCHAR(128) NULL,
    quality_status VARCHAR(32) NOT NULL,
    received_quantity DECIMAL(24, 6) NOT NULL,
    pending_quality_quantity DECIMAL(24, 6) NOT NULL,
    accepted_quantity DECIMAL(24, 6) NOT NULL,
    rejected_quantity DECIMAL(24, 6) NOT NULL,
    quarantined_quantity DECIMAL(24, 6) NOT NULL,
    cumulative_putaway_quantity DECIMAL(24, 6) NOT NULL,
    quality_inspection_id VARCHAR(128) NULL,
    valuation_policy_id VARCHAR(64) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    valuation_policy_hash CHAR(64) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    movement_cost_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    rounding_policy_code VARCHAR(64) NOT NULL,
    tolerance_policy_version VARCHAR(64) NOT NULL,
    tolerance_policy_hash CHAR(64) NOT NULL,
    inventory_idempotency_key VARCHAR(128) NOT NULL,
    inventory_operation_id BIGINT NOT NULL,
    inventory_ledger_tx_id BIGINT NOT NULL,
    inventory_balance_id VARCHAR(128) NOT NULL,
    finance_receipt_evidence_operation_id BIGINT NULL,
    finance_receipt_evidence_id VARCHAR(128) NULL,
    finance_receipt_evidence_version BIGINT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_warehouse_procurement_receipt_line_no (tenant_id, receipt_id, line_no),
    UNIQUE KEY uk_cloudmold_warehouse_procurement_receipt_inventory_idem (tenant_id, inventory_idempotency_key),
    KEY idx_cloudmold_warehouse_procurement_receipt_line_asn (tenant_id, asn_line_id),
    CONSTRAINT fk_cloudmold_warehouse_procurement_receipt_line_receipt
        FOREIGN KEY (receipt_id) REFERENCES cloudmold_warehouse_procurement_receipt (receipt_id),
    CONSTRAINT fk_cloudmold_warehouse_procurement_receipt_line_asn
        FOREIGN KEY (asn_line_id) REFERENCES cloudmold_warehouse_procurement_asn_line (asn_line_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_line_qty
        CHECK (received_quantity > 0 AND pending_quality_quantity >= 0 AND accepted_quantity >= 0
            AND rejected_quantity >= 0 AND quarantined_quantity >= 0 AND cumulative_putaway_quantity >= 0
            AND received_quantity = pending_quality_quantity + accepted_quantity
                + rejected_quantity + quarantined_quantity
            AND cumulative_putaway_quantity <= accepted_quantity),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_line_quality
        CHECK (quality_status IN ('PENDING_QUALITY', 'PARTIAL_QUALITY_DECIDED', 'QUALITY_ACCEPTED',
            'QUALITY_REJECTED', 'QUALITY_QUARANTINED', 'QUALITY_MIXED',
            'PARTIALLY_PUTAWAY', 'PUTAWAY_COMPLETED')),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_line_state_shape CHECK (
        (quality_status = 'PENDING_QUALITY' AND pending_quality_quantity = received_quantity
            AND accepted_quantity = 0 AND rejected_quantity = 0 AND quarantined_quantity = 0
            AND cumulative_putaway_quantity = 0)
        OR (quality_status = 'PARTIAL_QUALITY_DECIDED' AND pending_quality_quantity > 0
            AND pending_quality_quantity < received_quantity AND cumulative_putaway_quantity = 0)
        OR (quality_status = 'QUALITY_ACCEPTED' AND pending_quality_quantity = 0
            AND accepted_quantity > 0 AND rejected_quantity = 0 AND quarantined_quantity = 0
            AND cumulative_putaway_quantity = 0)
        OR (quality_status = 'QUALITY_REJECTED' AND pending_quality_quantity = 0
            AND accepted_quantity = 0 AND rejected_quantity > 0 AND quarantined_quantity = 0
            AND cumulative_putaway_quantity = 0)
        OR (quality_status = 'QUALITY_QUARANTINED' AND pending_quality_quantity = 0
            AND accepted_quantity = 0 AND rejected_quantity = 0 AND quarantined_quantity > 0
            AND cumulative_putaway_quantity = 0)
        OR (quality_status = 'QUALITY_MIXED' AND pending_quality_quantity = 0
            AND ((accepted_quantity > 0) + (rejected_quantity > 0) + (quarantined_quantity > 0)) > 1
            AND cumulative_putaway_quantity = 0)
        OR (quality_status = 'PARTIALLY_PUTAWAY' AND cumulative_putaway_quantity > 0
            AND (pending_quality_quantity > 0 OR cumulative_putaway_quantity < accepted_quantity))
        OR (quality_status = 'PUTAWAY_COMPLETED' AND pending_quality_quantity = 0
            AND accepted_quantity > 0 AND cumulative_putaway_quantity = accepted_quantity)
    ),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_line_valuation_hash
        CHECK (REGEXP_LIKE(valuation_policy_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_line_tolerance_hash
        CHECK (REGEXP_LIKE(tolerance_policy_hash, '^[0-9a-f]{64}$', 'c')),
    CONSTRAINT chk_cloudmold_warehouse_procurement_receipt_line_valuation
        CHECK (unit_cost_amount_minor >= 0 AND movement_cost_amount_minor >= 0
            AND movement_cost_amount_minor = ROUND(received_quantity * unit_cost_amount_minor, 0)
            AND REGEXP_LIKE(currency_code, '^[A-Z]{3}$', 'c') AND rounding_policy_code = 'HALF_UP'),
    CONSTRAINT chk_cm_wh_receipt_line_finance_evidence
        CHECK ((finance_receipt_evidence_operation_id IS NULL AND finance_receipt_evidence_id IS NULL
                AND finance_receipt_evidence_version IS NULL)
            OR (finance_receipt_evidence_operation_id > 0 AND finance_receipt_evidence_id <> ''
                AND finance_receipt_evidence_version > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_putaway (
    putaway_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_cloudmold_warehouse_procurement_putaway_receipt
        FOREIGN KEY (receipt_id) REFERENCES cloudmold_warehouse_procurement_receipt (receipt_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_putaway_status
        CHECK (status IN ('COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_putaway_line (
    putaway_line_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    putaway_id VARCHAR(128) NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    source_location_id VARCHAR(128) NOT NULL,
    target_location_id VARCHAR(128) NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    putaway_quantity DECIMAL(24, 6) NOT NULL,
    cumulative_putaway_quantity DECIMAL(24, 6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    inventory_operation_id BIGINT NOT NULL,
    inventory_ledger_tx_id BIGINT NOT NULL,
    inventory_movement_group_id VARCHAR(128) NOT NULL,
    inventory_target_balance_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_cloudmold_warehouse_procurement_putaway_line_receipt
        (tenant_id, receipt_line_id, created_at),
    CONSTRAINT fk_cloudmold_warehouse_procurement_putaway_line_putaway
        FOREIGN KEY (putaway_id) REFERENCES cloudmold_warehouse_procurement_putaway (putaway_id),
    CONSTRAINT fk_cloudmold_warehouse_procurement_putaway_line_receipt_line
        FOREIGN KEY (receipt_line_id) REFERENCES cloudmold_warehouse_procurement_receipt_line (receipt_line_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_putaway_line_qty
        CHECK (putaway_quantity > 0 AND cumulative_putaway_quantity >= putaway_quantity),
    CONSTRAINT chk_cloudmold_warehouse_procurement_putaway_line_status
        CHECK (status IN ('COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_quality_effect (
    effect_id VARCHAR(128) NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    operation_id BIGINT NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    quality_decision_id VARCHAR(128) NOT NULL,
    decision_version BIGINT NOT NULL,
    inspection_split_id VARCHAR(128) NOT NULL,
    disposition VARCHAR(32) NOT NULL,
    receipt_id VARCHAR(128) NOT NULL,
    receipt_line_id VARCHAR(128) NOT NULL,
    procurement_order_id VARCHAR(128) NOT NULL,
    procurement_order_item_id VARCHAR(128) NOT NULL,
    delivery_schedule_id VARCHAR(128) NOT NULL,
    disposition_quantity DECIMAL(24, 6) NOT NULL,
    pending_quantity_before DECIMAL(24, 6) NOT NULL,
    pending_quantity_after DECIMAL(24, 6) NOT NULL,
    accepted_quantity_before DECIMAL(24, 6) NOT NULL,
    accepted_quantity_after DECIMAL(24, 6) NOT NULL,
    rejected_quantity_before DECIMAL(24, 6) NOT NULL,
    rejected_quantity_after DECIMAL(24, 6) NOT NULL,
    quarantined_quantity_before DECIMAL(24, 6) NOT NULL,
    quarantined_quantity_after DECIMAL(24, 6) NOT NULL,
    receipt_version_before BIGINT NOT NULL,
    receipt_version_after BIGINT NOT NULL,
    receipt_line_version_before BIGINT NOT NULL,
    receipt_line_version_after BIGINT NOT NULL,
    schedule_fulfillment_version_before BIGINT NOT NULL,
    schedule_fulfillment_version_after BIGINT NOT NULL,
    evidence_ref VARCHAR(255) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_cloudmold_warehouse_procurement_quality_effect_source (tenant_id, source_event_id),
    UNIQUE KEY uk_cloudmold_warehouse_procurement_quality_effect_split
        (tenant_id, quality_decision_id, decision_version, inspection_split_id, disposition),
    KEY idx_cloudmold_warehouse_procurement_quality_effect_receipt
        (tenant_id, receipt_line_id, created_at),
    CONSTRAINT fk_cloudmold_warehouse_procurement_quality_effect_operation
        FOREIGN KEY (operation_id) REFERENCES cloudmold_inbound_operation (operation_id),
    CONSTRAINT fk_cloudmold_warehouse_procurement_quality_effect_receipt_line
        FOREIGN KEY (receipt_line_id) REFERENCES cloudmold_warehouse_procurement_receipt_line (receipt_line_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_quality_effect_disposition
        CHECK (disposition IN ('ACCEPTED', 'REJECTED', 'QUARANTINED')),
    CONSTRAINT chk_cloudmold_warehouse_procurement_quality_effect_quantities CHECK (
        disposition_quantity > 0
        AND pending_quantity_before = pending_quantity_after + disposition_quantity
        AND accepted_quantity_after >= accepted_quantity_before
        AND rejected_quantity_after >= rejected_quantity_before
        AND quarantined_quantity_after >= quarantined_quantity_before
        AND (accepted_quantity_after - accepted_quantity_before)
            + (rejected_quantity_after - rejected_quantity_before)
            + (quarantined_quantity_after - quarantined_quantity_before) = disposition_quantity
    ),
    CONSTRAINT chk_cloudmold_warehouse_procurement_quality_effect_versions CHECK (
        receipt_version_after = receipt_version_before + 1
        AND receipt_line_version_after = receipt_line_version_before + 1
        AND schedule_fulfillment_version_after = schedule_fulfillment_version_before + 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_warehouse_procurement_inbound_history (
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
    KEY idx_cloudmold_warehouse_procurement_inbound_history_object (tenant_id, business_object_type, business_object_id, changed_at),
    CONSTRAINT fk_cloudmold_warehouse_procurement_inbound_history_operation
        FOREIGN KEY (operation_id) REFERENCES cloudmold_inbound_operation (operation_id),
    CONSTRAINT chk_cloudmold_warehouse_procurement_inbound_history_type
        CHECK (business_object_type IN ('ASN', 'ASN_LINE', 'RECEIPT', 'RECEIPT_LINE',
            'SCHEDULE_FULFILLMENT', 'PUTAWAY', 'PUTAWAY_LINE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
CREATE PROCEDURE cm_warehouse_v150_retire_legacy()
BEGIN
    DECLARE legacy_total_rows BIGINT DEFAULT 0;
    DECLARE legacy_rows BIGINT DEFAULT 0;
    DECLARE table_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'cloudmold_asn';
    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO legacy_rows FROM cloudmold_asn;
        SET legacy_total_rows = legacy_total_rows + legacy_rows;
    END IF;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'cloudmold_asn_line';
    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO legacy_rows FROM cloudmold_asn_line;
        SET legacy_total_rows = legacy_total_rows + legacy_rows;
    END IF;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'cloudmold_receipt';
    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO legacy_rows FROM cloudmold_receipt;
        SET legacy_total_rows = legacy_total_rows + legacy_rows;
    END IF;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'cloudmold_receipt_line';
    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO legacy_rows FROM cloudmold_receipt_line;
        SET legacy_total_rows = legacy_total_rows + legacy_rows;
    END IF;

    SELECT COUNT(*) INTO table_exists
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'cloudmold_putaway';
    IF table_exists > 0 THEN
        SELECT COUNT(*) INTO legacy_rows FROM cloudmold_putaway;
        SET legacy_total_rows = legacy_total_rows + legacy_rows;
    END IF;

    IF legacy_total_rows > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'legacy warehouse inbound authority rows exist; migrate or delete old cloudmold_asn/receipt/putaway tables before V150',
                MYSQL_ERRNO = 1644;
    END IF;

    DROP TABLE IF EXISTS cloudmold_putaway;
    DROP TABLE IF EXISTS cloudmold_receipt_line;
    DROP TABLE IF EXISTS cloudmold_receipt;
    DROP TABLE IF EXISTS cloudmold_asn_line;
    DROP TABLE IF EXISTS cloudmold_asn;
END$$
CALL cm_warehouse_v150_retire_legacy()$$
DROP PROCEDURE cm_warehouse_v150_retire_legacy$$
DELIMITER ;
