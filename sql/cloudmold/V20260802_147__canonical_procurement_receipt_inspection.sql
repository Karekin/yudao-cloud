-- Canonical Quality-owned procurement receipt inspection.
-- Source receipt and purchase-order identifiers are immutable qualified references; Quality records facts only.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    inspection_id VARCHAR(128) NULL,
    aggregate_version BIGINT NULL,
    result_status VARCHAR(32) NULL,
    final_decision VARCHAR(16) NULL,
    received_quantity DECIMAL(24,6) NULL,
    sampled_quantity DECIMAL(24,6) NULL,
    accepted_quantity DECIMAL(24,6) NULL,
    rejected_quantity DECIMAL(24,6) NULL,
    quarantined_quantity DECIMAL(24,6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_pri_operation_tenant_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_pri_operation_tenant_id (tenant_id, operation_id),
    CONSTRAINT ck_pri_operation_type CHECK (
        operation_type IN ('CREATE_INSPECTION','RECORD_LINE_RESULTS','COMPLETE_INSPECTION')),
    CONSTRAINT ck_pri_operation_status CHECK (status IN (0,10)),
    CONSTRAINT ck_pri_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_pri_operation_result CHECK (
        status = 0 OR (inspection_id IS NOT NULL AND aggregate_version > 0 AND result_status IS NOT NULL
            AND received_quantity >= 0 AND sampled_quantity >= 0 AND accepted_quantity >= 0
            AND rejected_quantity >= 0 AND quarantined_quantity >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Idempotent typed procurement receipt inspection operation';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection (
    inspection_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inspection_code VARCHAR(64) NOT NULL,
    receipt_id CHAR(36) NOT NULL,
    purchase_order_id CHAR(36) NOT NULL,
    supplier_id CHAR(36) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    business_no VARCHAR(128) NOT NULL,
    standard_id VARCHAR(128) NOT NULL,
    standard_version BIGINT NOT NULL,
    standard_version_id VARCHAR(128) NOT NULL,
    standard_content_sha256 CHAR(64) NOT NULL,
    created_by_principal_id VARCHAR(128) NOT NULL,
    last_decision_actor_principal_id VARCHAR(128) NULL,
    completed_by_principal_id VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL,
    final_decision VARCHAR(16) NULL,
    received_quantity DECIMAL(24,6) NOT NULL,
    sampled_quantity DECIMAL(24,6) NOT NULL,
    accepted_quantity DECIMAL(24,6) NOT NULL,
    rejected_quantity DECIMAL(24,6) NOT NULL,
    quarantined_quantity DECIMAL(24,6) NOT NULL,
    version BIGINT NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (inspection_id),
    UNIQUE KEY uk_pri_tenant_id (tenant_id, inspection_id),
    UNIQUE KEY uk_pri_tenant_code (tenant_id, inspection_code),
    KEY idx_pri_receipt (tenant_id, receipt_id, status),
    KEY idx_pri_purchase_order (tenant_id, purchase_order_id, status),
    CONSTRAINT fk_pri_standard_snapshot FOREIGN KEY
        (tenant_id, standard_id, standard_version, standard_version_id)
        REFERENCES cloudmold_quality_standard_version
        (tenant_id, standard_id, standard_version, standard_version_id),
    CONSTRAINT ck_pri_status CHECK (
        status IN ('OPEN','IN_PROGRESS','PARTIALLY_COMPLETED','READY_TO_COMPLETE','COMPLETED')),
    CONSTRAINT ck_pri_decision CHECK (
        final_decision IS NULL OR final_decision IN ('ACCEPTED','REJECTED','QUARANTINED','MIXED')),
    CONSTRAINT ck_pri_standard_hash CHECK (standard_content_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_pri_external_uuid CHECK (
        receipt_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND purchase_order_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND supplier_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND owner_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    CONSTRAINT ck_pri_owner_type CHECK (owner_type IN ('MERCHANT','PLATFORM','MEMBER')),
    CONSTRAINT ck_pri_version CHECK (version > 0),
    CONSTRAINT ck_pri_quantities CHECK (
        received_quantity > 0 AND sampled_quantity >= 0 AND sampled_quantity <= received_quantity
        AND accepted_quantity >= 0 AND rejected_quantity >= 0 AND quarantined_quantity >= 0
        AND accepted_quantity + rejected_quantity + quarantined_quantity <= received_quantity),
    CONSTRAINT ck_pri_completion CHECK (
        (status = 'COMPLETED' AND final_decision IS NOT NULL AND completed_at IS NOT NULL
            AND completed_by_principal_id IS NOT NULL AND last_decision_actor_principal_id IS NOT NULL
            AND completed_by_principal_id <> last_decision_actor_principal_id
            AND accepted_quantity + rejected_quantity + quarantined_quantity = received_quantity)
        OR (status <> 'COMPLETED' AND final_decision IS NULL AND completed_at IS NULL
            AND completed_by_principal_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Quality-owned procurement receipt inspection header';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_line (
    inspection_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inspection_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    receipt_line_id CHAR(36) NOT NULL,
    purchase_order_id CHAR(36) NOT NULL,
    item_id CHAR(36) NOT NULL,
    schedule_id CHAR(36) NOT NULL,
    canonical_sku_id CHAR(36) NOT NULL,
    uom_code VARCHAR(32) NOT NULL,
    supplier_id CHAR(36) NOT NULL,
    owner_type VARCHAR(16) NOT NULL,
    owner_id CHAR(36) NOT NULL,
    valuation_policy VARCHAR(64) NOT NULL,
    valuation_policy_version VARCHAR(64) NOT NULL,
    valuation_policy_hash CHAR(64) NOT NULL,
    unit_cost_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    received_quantity DECIMAL(24,6) NOT NULL,
    sampled_quantity DECIMAL(24,6) NOT NULL,
    accepted_quantity DECIMAL(24,6) NOT NULL,
    rejected_quantity DECIMAL(24,6) NOT NULL,
    quarantined_quantity DECIMAL(24,6) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (inspection_line_id),
    UNIQUE KEY uk_pri_line_tenant_id (tenant_id, inspection_line_id),
    UNIQUE KEY uk_pri_line_number (tenant_id, inspection_id, line_number),
    UNIQUE KEY uk_pri_line_receipt_line (tenant_id, inspection_id, receipt_line_id),
    UNIQUE KEY uk_pri_line_identity (tenant_id, inspection_id, inspection_line_id),
    KEY idx_pri_line_order_item_schedule
        (tenant_id, purchase_order_id, item_id, schedule_id),
    KEY idx_pri_line_sku (tenant_id, canonical_sku_id, status),
    CONSTRAINT fk_pri_line_header FOREIGN KEY (tenant_id, inspection_id)
        REFERENCES cloudmold_procurement_receipt_inspection (tenant_id, inspection_id),
    CONSTRAINT ck_pri_line_number CHECK (line_number > 0),
    CONSTRAINT ck_pri_line_status CHECK (status IN ('OPEN','PARTIALLY_INSPECTED','COMPLETED')),
    CONSTRAINT ck_pri_line_version CHECK (version > 0),
    CONSTRAINT ck_pri_line_external_uuid CHECK (
        receipt_line_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND purchase_order_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND item_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND schedule_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND canonical_sku_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND supplier_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND owner_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    CONSTRAINT ck_pri_line_owner_type CHECK (owner_type IN ('MERCHANT','PLATFORM','MEMBER')),
    CONSTRAINT ck_pri_line_valuation CHECK (
        valuation_policy REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND valuation_policy_version REGEXP '^[A-Z0-9][A-Z0-9._-]{0,63}$'
        AND valuation_policy_hash REGEXP '^[0-9a-f]{64}$'
        AND unit_cost_amount_minor >= 0 AND currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_pri_line_quantities CHECK (
        received_quantity > 0 AND sampled_quantity >= 0 AND sampled_quantity <= received_quantity
        AND accepted_quantity >= 0 AND rejected_quantity >= 0 AND quarantined_quantity >= 0
        AND accepted_quantity + rejected_quantity + quarantined_quantity <= received_quantity),
    CONSTRAINT ck_pri_line_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL
            AND accepted_quantity + rejected_quantity + quarantined_quantity = received_quantity)
        OR (status <> 'COMPLETED' AND completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Exact receipt-line and PO item/schedule inspection grain';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_split (
    inspection_split_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inspection_id VARCHAR(128) NOT NULL,
    inspection_line_id VARCHAR(128) NOT NULL,
    split_number INT NOT NULL,
    warehouse_id CHAR(36) NOT NULL,
    location_id CHAR(36) NOT NULL,
    lot_id CHAR(36) NULL,
    uom_code VARCHAR(32) NOT NULL,
    received_quantity DECIMAL(24,6) NOT NULL,
    sampled_quantity DECIMAL(24,6) NOT NULL,
    accepted_quantity DECIMAL(24,6) NOT NULL,
    rejected_quantity DECIMAL(24,6) NOT NULL,
    quarantined_quantity DECIMAL(24,6) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (inspection_split_id),
    UNIQUE KEY uk_pri_split_tenant_id (tenant_id, inspection_split_id),
    UNIQUE KEY uk_pri_split_number (tenant_id, inspection_line_id, split_number),
    UNIQUE KEY uk_pri_split_identity
        (tenant_id, inspection_id, inspection_line_id, inspection_split_id),
    KEY idx_pri_split_physical (tenant_id, warehouse_id, location_id, lot_id, status),
    CONSTRAINT fk_pri_split_line FOREIGN KEY (tenant_id, inspection_id, inspection_line_id)
        REFERENCES cloudmold_procurement_receipt_inspection_line
        (tenant_id, inspection_id, inspection_line_id),
    CONSTRAINT ck_pri_split_number CHECK (split_number > 0),
    CONSTRAINT ck_pri_split_status CHECK (status IN ('OPEN','PARTIALLY_INSPECTED','COMPLETED')),
    CONSTRAINT ck_pri_split_version CHECK (version > 0),
    CONSTRAINT ck_pri_split_external_uuid CHECK (
        warehouse_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND location_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND (lot_id IS NULL OR lot_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')),
    CONSTRAINT ck_pri_split_quantities CHECK (
        received_quantity > 0 AND sampled_quantity >= 0 AND sampled_quantity <= received_quantity
        AND accepted_quantity >= 0 AND rejected_quantity >= 0 AND quarantined_quantity >= 0
        AND accepted_quantity + rejected_quantity + quarantined_quantity <= received_quantity),
    CONSTRAINT ck_pri_split_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL
            AND accepted_quantity + rejected_quantity + quarantined_quantity = received_quantity)
        OR (status <> 'COMPLETED' AND completed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Warehouse/location/nullable-lot receipt inspection split';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_result_batch (
    result_batch_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    inspection_id VARCHAR(128) NOT NULL,
    inspection_version_before BIGINT NOT NULL,
    inspection_version_after BIGINT NOT NULL,
    decision_version BIGINT NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    operation_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (result_batch_id),
    UNIQUE KEY uk_pri_result_batch_tenant_id (tenant_id, result_batch_id),
    UNIQUE KEY uk_pri_result_batch_operation (tenant_id, operation_id),
    UNIQUE KEY uk_pri_result_batch_decision_version (tenant_id, inspection_id, decision_version),
    CONSTRAINT fk_pri_result_batch_header FOREIGN KEY (tenant_id, inspection_id)
        REFERENCES cloudmold_procurement_receipt_inspection (tenant_id, inspection_id),
    CONSTRAINT fk_pri_result_batch_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_procurement_receipt_inspection_operation (tenant_id, operation_id),
    CONSTRAINT ck_pri_result_batch_version CHECK (
        inspection_version_before > 0 AND inspection_version_after = inspection_version_before + 1
        AND decision_version = inspection_version_after)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable inspection result submission batch';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_result_split (
    result_split_id VARCHAR(128) NOT NULL,
    quality_decision_id CHAR(36) NOT NULL,
    decision_version BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    result_batch_id VARCHAR(128) NOT NULL,
    inspection_id VARCHAR(128) NOT NULL,
    inspection_line_id VARCHAR(128) NOT NULL,
    inspection_split_id VARCHAR(128) NOT NULL,
    sampled_quantity DECIMAL(24,6) NOT NULL,
    accepted_quantity DECIMAL(24,6) NOT NULL,
    rejected_quantity DECIMAL(24,6) NOT NULL,
    quarantined_quantity DECIMAL(24,6) NOT NULL,
    accepted_disposition_code VARCHAR(32) NULL,
    rejected_disposition_code VARCHAR(32) NULL,
    quarantine_disposition_code VARCHAR(32) NULL,
    decision_evidence_sha256 CHAR(64) NOT NULL,
    evidence_ref VARCHAR(256) NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    operation_id BIGINT NOT NULL,
    finance_receipt_evidence_operation_id BIGINT NOT NULL,
    finance_receipt_evidence_id CHAR(36) NOT NULL,
    finance_receipt_evidence_version BIGINT NOT NULL,
    finance_quality_operation_id BIGINT NOT NULL,
    finance_quality_evidence_id CHAR(36) NOT NULL,
    finance_quality_evidence_version BIGINT NOT NULL,
    accepted_inventory_operation_id BIGINT NULL,
    accepted_ledger_transaction_id BIGINT NULL,
    accepted_inventory_aggregate_version BIGINT NULL,
    accepted_warehouse_operation_id BIGINT NULL,
    accepted_warehouse_receipt_version BIGINT NULL,
    accepted_warehouse_receipt_line_version BIGINT NULL,
    accepted_warehouse_schedule_fulfillment_version BIGINT NULL,
    accepted_finance_inventory_operation_id BIGINT NULL,
    accepted_finance_inventory_evidence_id CHAR(36) NULL,
    accepted_finance_inventory_evidence_version BIGINT NULL,
    rejected_inventory_operation_id BIGINT NULL,
    rejected_ledger_transaction_id BIGINT NULL,
    rejected_inventory_aggregate_version BIGINT NULL,
    rejected_warehouse_operation_id BIGINT NULL,
    rejected_warehouse_receipt_version BIGINT NULL,
    rejected_warehouse_receipt_line_version BIGINT NULL,
    rejected_warehouse_schedule_fulfillment_version BIGINT NULL,
    rejected_finance_inventory_operation_id BIGINT NULL,
    rejected_finance_inventory_evidence_id CHAR(36) NULL,
    rejected_finance_inventory_evidence_version BIGINT NULL,
    quarantined_inventory_operation_id BIGINT NULL,
    quarantined_ledger_transaction_id BIGINT NULL,
    quarantined_inventory_aggregate_version BIGINT NULL,
    quarantined_warehouse_operation_id BIGINT NULL,
    quarantined_warehouse_receipt_version BIGINT NULL,
    quarantined_warehouse_receipt_line_version BIGINT NULL,
    quarantined_warehouse_schedule_fulfillment_version BIGINT NULL,
    quarantined_finance_inventory_operation_id BIGINT NULL,
    quarantined_finance_inventory_evidence_id CHAR(36) NULL,
    quarantined_finance_inventory_evidence_version BIGINT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (result_split_id),
    UNIQUE KEY uk_pri_result_split_tenant_id (tenant_id, result_split_id),
    UNIQUE KEY uk_pri_result_split_quality_decision (tenant_id, quality_decision_id),
    UNIQUE KEY uk_pri_result_split_batch_split (tenant_id, result_batch_id, inspection_split_id),
    KEY idx_pri_result_split_line (tenant_id, inspection_line_id, occurred_at),
    CONSTRAINT fk_pri_result_split_batch FOREIGN KEY (tenant_id, result_batch_id)
        REFERENCES cloudmold_procurement_receipt_inspection_result_batch (tenant_id, result_batch_id),
    CONSTRAINT fk_pri_result_split_source FOREIGN KEY
        (tenant_id, inspection_id, inspection_line_id, inspection_split_id)
        REFERENCES cloudmold_procurement_receipt_inspection_split
        (tenant_id, inspection_id, inspection_line_id, inspection_split_id),
    CONSTRAINT fk_pri_result_split_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_procurement_receipt_inspection_operation (tenant_id, operation_id),
    CONSTRAINT ck_pri_result_split_quantities CHECK (
        sampled_quantity > 0 AND accepted_quantity >= 0 AND rejected_quantity >= 0
        AND quarantined_quantity >= 0
        AND accepted_quantity + rejected_quantity + quarantined_quantity > 0),
    CONSTRAINT ck_pri_result_split_hash CHECK (decision_evidence_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_pri_result_split_decision_version CHECK (decision_version > 0),
    CONSTRAINT ck_pri_result_split_decision_uuid CHECK (
        quality_decision_id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    CONSTRAINT ck_pri_result_split_finance_quality CHECK (
        finance_receipt_evidence_operation_id > 0 AND finance_receipt_evidence_version > 0
        AND finance_receipt_evidence_id REGEXP
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND finance_quality_operation_id > 0 AND finance_quality_evidence_version = decision_version
        AND finance_quality_evidence_id REGEXP
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    CONSTRAINT ck_pri_result_split_cross_domain_effects CHECK (
        ((accepted_quantity > 0 AND accepted_inventory_operation_id IS NOT NULL
            AND accepted_ledger_transaction_id IS NOT NULL
            AND accepted_inventory_aggregate_version IS NOT NULL
            AND accepted_warehouse_operation_id IS NOT NULL
            AND accepted_warehouse_receipt_version IS NOT NULL
            AND accepted_warehouse_receipt_line_version IS NOT NULL
            AND accepted_warehouse_schedule_fulfillment_version IS NOT NULL
            AND accepted_finance_inventory_operation_id IS NOT NULL
            AND accepted_finance_inventory_evidence_id IS NOT NULL
            AND accepted_finance_inventory_evidence_version IS NOT NULL
            AND accepted_inventory_operation_id > 0 AND accepted_ledger_transaction_id > 0
            AND accepted_inventory_aggregate_version > 0
            AND accepted_warehouse_operation_id > 0 AND accepted_warehouse_receipt_version > 0
            AND accepted_warehouse_receipt_line_version > 0
            AND accepted_warehouse_schedule_fulfillment_version > 0
            AND accepted_finance_inventory_operation_id > 0
            AND accepted_finance_inventory_evidence_version = accepted_inventory_aggregate_version
            AND accepted_finance_inventory_evidence_id REGEXP
                '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
        OR (accepted_quantity = 0 AND accepted_inventory_operation_id IS NULL
                AND accepted_ledger_transaction_id IS NULL AND accepted_inventory_aggregate_version IS NULL
                AND accepted_warehouse_operation_id IS NULL
                AND accepted_warehouse_receipt_version IS NULL
                AND accepted_warehouse_receipt_line_version IS NULL
                AND accepted_warehouse_schedule_fulfillment_version IS NULL
                AND accepted_finance_inventory_operation_id IS NULL
                AND accepted_finance_inventory_evidence_id IS NULL
                AND accepted_finance_inventory_evidence_version IS NULL))
        AND ((rejected_quantity > 0 AND rejected_inventory_operation_id IS NOT NULL
            AND rejected_ledger_transaction_id IS NOT NULL
            AND rejected_inventory_aggregate_version IS NOT NULL
            AND rejected_warehouse_operation_id IS NOT NULL
            AND rejected_warehouse_receipt_version IS NOT NULL
            AND rejected_warehouse_receipt_line_version IS NOT NULL
            AND rejected_warehouse_schedule_fulfillment_version IS NOT NULL
            AND rejected_finance_inventory_operation_id IS NOT NULL
            AND rejected_finance_inventory_evidence_id IS NOT NULL
            AND rejected_finance_inventory_evidence_version IS NOT NULL
            AND rejected_inventory_operation_id > 0 AND rejected_ledger_transaction_id > 0
            AND rejected_inventory_aggregate_version > 0
            AND rejected_warehouse_operation_id > 0 AND rejected_warehouse_receipt_version > 0
            AND rejected_warehouse_receipt_line_version > 0
            AND rejected_warehouse_schedule_fulfillment_version > 0
            AND rejected_finance_inventory_operation_id > 0
            AND rejected_finance_inventory_evidence_version = rejected_inventory_aggregate_version
            AND rejected_finance_inventory_evidence_id REGEXP
                '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
        OR (rejected_quantity = 0 AND rejected_inventory_operation_id IS NULL
                AND rejected_ledger_transaction_id IS NULL AND rejected_inventory_aggregate_version IS NULL
                AND rejected_warehouse_operation_id IS NULL
                AND rejected_warehouse_receipt_version IS NULL
                AND rejected_warehouse_receipt_line_version IS NULL
                AND rejected_warehouse_schedule_fulfillment_version IS NULL
                AND rejected_finance_inventory_operation_id IS NULL
                AND rejected_finance_inventory_evidence_id IS NULL
                AND rejected_finance_inventory_evidence_version IS NULL))
        AND ((quarantined_quantity > 0 AND quarantined_inventory_operation_id IS NOT NULL
                AND quarantined_ledger_transaction_id IS NOT NULL
                AND quarantined_inventory_aggregate_version IS NOT NULL
                AND quarantined_warehouse_operation_id IS NOT NULL
                AND quarantined_warehouse_receipt_version IS NOT NULL
                AND quarantined_warehouse_receipt_line_version IS NOT NULL
                AND quarantined_warehouse_schedule_fulfillment_version IS NOT NULL
                AND quarantined_finance_inventory_operation_id IS NOT NULL
                AND quarantined_finance_inventory_evidence_id IS NOT NULL
                AND quarantined_finance_inventory_evidence_version IS NOT NULL
                AND quarantined_inventory_operation_id > 0
                AND quarantined_ledger_transaction_id > 0 AND quarantined_inventory_aggregate_version > 0
                AND quarantined_warehouse_operation_id > 0
                AND quarantined_warehouse_receipt_version > 0
                AND quarantined_warehouse_receipt_line_version > 0
                AND quarantined_warehouse_schedule_fulfillment_version > 0
                AND quarantined_finance_inventory_operation_id > 0
                AND quarantined_finance_inventory_evidence_version = quarantined_inventory_aggregate_version
                AND quarantined_finance_inventory_evidence_id REGEXP
                    '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
            OR (quarantined_quantity = 0 AND quarantined_inventory_operation_id IS NULL
                AND quarantined_ledger_transaction_id IS NULL
                AND quarantined_inventory_aggregate_version IS NULL
                AND quarantined_warehouse_operation_id IS NULL
                AND quarantined_warehouse_receipt_version IS NULL
                AND quarantined_warehouse_receipt_line_version IS NULL
                AND quarantined_warehouse_schedule_fulfillment_version IS NULL
                AND quarantined_finance_inventory_operation_id IS NULL
                AND quarantined_finance_inventory_evidence_id IS NULL
                AND quarantined_finance_inventory_evidence_version IS NULL))),
    CONSTRAINT ck_pri_result_split_accept_disposition CHECK (
        (accepted_quantity > 0 AND accepted_disposition_code IN ('ACCEPT','CONDITIONAL_ACCEPT'))
        OR (accepted_quantity = 0 AND accepted_disposition_code IS NULL)),
    CONSTRAINT ck_pri_result_split_reject_disposition CHECK (
        (rejected_quantity > 0 AND rejected_disposition_code IN ('REJECT','RETURN_TO_SUPPLIER','REWORK','SCRAP'))
        OR (rejected_quantity = 0 AND rejected_disposition_code IS NULL)),
    CONSTRAINT ck_pri_result_split_quarantine_disposition CHECK (
        (quarantined_quantity > 0 AND quarantine_disposition_code IN ('QUARANTINE','HOLD','REWORK'))
        OR (quarantined_quantity = 0 AND quarantine_disposition_code IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable sampled decision and disposition fact per physical split';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_defect (
    defect_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    result_split_id VARCHAR(128) NOT NULL,
    inspection_id VARCHAR(128) NOT NULL,
    inspection_line_id VARCHAR(128) NOT NULL,
    inspection_split_id VARCHAR(128) NOT NULL,
    defect_code VARCHAR(64) NOT NULL,
    defect_category VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    affected_quantity DECIMAL(24,6) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    evidence_ref VARCHAR(256) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (defect_id),
    UNIQUE KEY uk_pri_defect_tenant_id (tenant_id, defect_id),
    KEY idx_pri_defect_inspection (tenant_id, inspection_id, severity, created_at),
    CONSTRAINT fk_pri_defect_result_split FOREIGN KEY (tenant_id, result_split_id)
        REFERENCES cloudmold_procurement_receipt_inspection_result_split (tenant_id, result_split_id),
    CONSTRAINT ck_pri_defect_severity CHECK (severity IN ('MINOR','MAJOR','CRITICAL')),
    CONSTRAINT ck_pri_defect_quantity CHECK (affected_quantity > 0),
    CONSTRAINT ck_pri_defect_hash CHECK (evidence_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable structured defect evidence';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    inspection_id VARCHAR(128) NOT NULL,
    inspection_version BIGINT NOT NULL,
    previous_status VARCHAR(32) NULL,
    current_status VARCHAR(32) NOT NULL,
    final_decision VARCHAR(16) NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    operation_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_pri_history_version (tenant_id, inspection_id, inspection_version),
    CONSTRAINT fk_pri_history_header FOREIGN KEY (tenant_id, inspection_id)
        REFERENCES cloudmold_procurement_receipt_inspection (tenant_id, inspection_id),
    CONSTRAINT fk_pri_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_procurement_receipt_inspection_operation (tenant_id, operation_id),
    CONSTRAINT ck_pri_history_version CHECK (inspection_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable inspection aggregate history';

CREATE TABLE IF NOT EXISTS cloudmold_procurement_receipt_inspection_line_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    inspection_line_id VARCHAR(128) NOT NULL,
    line_version BIGINT NOT NULL,
    previous_status VARCHAR(24) NULL,
    current_status VARCHAR(24) NOT NULL,
    sampled_quantity DECIMAL(24,6) NOT NULL,
    accepted_quantity DECIMAL(24,6) NOT NULL,
    rejected_quantity DECIMAL(24,6) NOT NULL,
    quarantined_quantity DECIMAL(24,6) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    operation_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_pri_line_history_version (tenant_id, inspection_line_id, line_version),
    CONSTRAINT fk_pri_line_history_line FOREIGN KEY (tenant_id, inspection_line_id)
        REFERENCES cloudmold_procurement_receipt_inspection_line (tenant_id, inspection_line_id),
    CONSTRAINT fk_pri_line_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_procurement_receipt_inspection_operation (tenant_id, operation_id),
    CONSTRAINT ck_pri_line_history_version CHECK (line_version > 0),
    CONSTRAINT ck_pri_line_history_quantities CHECK (
        sampled_quantity >= 0 AND accepted_quantity >= 0
        AND rejected_quantity >= 0 AND quarantined_quantity >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable line completion and quantity history';
