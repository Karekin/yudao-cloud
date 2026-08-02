-- Replace legacy ERP/WMS execution metadata with canonical CloudMold aggregate references.
-- Existing legacy transfer rows cannot be inferred safely and stop the migration.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS cloudmold_assert_native_replenishment;
DELIMITER $$
CREATE PROCEDURE cloudmold_assert_native_replenishment()
BEGIN
    IF EXISTS (
        SELECT 1 FROM cloudmold_replenishment_conversion
        WHERE target_type <> 'PURCHASE_REQUEST'
           OR source_system <> 'CLOUDMOLD_PROCUREMENT'
           OR document_type <> 'PURCHASE_REQUISITION'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'legacy replenishment conversions must be removed by governed data remediation';
    END IF;
    IF EXISTS (
        SELECT 1 FROM cloudmold_replenishment_execution_proposal
        WHERE target_type = 'TRANSFER_REQUEST'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'legacy transfer proposals cannot be inferred as canonical stock transfers';
    END IF;
END$$
DELIMITER ;
CALL cloudmold_assert_native_replenishment();
DROP PROCEDURE cloudmold_assert_native_replenishment;

ALTER TABLE cloudmold_replenishment_conversion
    ADD COLUMN target_aggregate_type VARCHAR(64) NULL AFTER target_type,
    ADD COLUMN target_aggregate_id VARCHAR(128) NULL AFTER target_aggregate_type,
    ADD COLUMN target_aggregate_no VARCHAR(128) NULL AFTER target_aggregate_id,
    ADD COLUMN target_aggregate_status VARCHAR(32) NULL AFTER target_aggregate_no;

UPDATE cloudmold_replenishment_conversion
SET target_aggregate_type = 'PURCHASE_REQUISITION',
    target_aggregate_id = external_document_id,
    target_aggregate_no = external_document_no,
    target_aggregate_status = document_status
WHERE target_type = 'PURCHASE_REQUEST';

ALTER TABLE cloudmold_replenishment_conversion
    MODIFY COLUMN target_aggregate_type VARCHAR(64) NOT NULL,
    MODIFY COLUMN target_aggregate_id VARCHAR(128) NOT NULL,
    MODIFY COLUMN target_aggregate_status VARCHAR(32) NOT NULL,
    DROP INDEX uk_replenishment_conversion_target,
    DROP CHECK ck_replenishment_conversion_source_system,
    DROP CHECK ck_replenishment_conversion_document_type,
    DROP CHECK ck_replenishment_conversion_external_document_id,
    DROP CHECK ck_replenishment_conversion_document_status,
    DROP CHECK ck_replenishment_conversion_next_waiting_event_code,
    DROP CHECK ck_replenishment_conversion_next_waiting_event_label,
    DROP COLUMN target_reference,
    DROP COLUMN source_system,
    DROP COLUMN document_type,
    DROP COLUMN external_document_id,
    DROP COLUMN external_document_no,
    DROP COLUMN document_status,
    DROP COLUMN next_waiting_event_code,
    DROP COLUMN next_waiting_event_label,
    ADD UNIQUE KEY uk_replenishment_conversion_target_aggregate
        (tenant_id, target_aggregate_type, target_aggregate_id),
    ADD CONSTRAINT ck_replenishment_conversion_target_aggregate_type
        CHECK (target_aggregate_type IN ('PURCHASE_REQUISITION', 'STOCK_TRANSFER_ORDER')),
    ADD CONSTRAINT ck_replenishment_conversion_target_aggregate_status
        CHECK (target_aggregate_status REGEXP '^[A-Z][A-Z0-9_]{1,31}$');

ALTER TABLE cloudmold_replenishment_execution_proposal
    DROP CHECK ck_replenishment_execution_proposal_mapping_hash,
    DROP CHECK ck_replenishment_execution_proposal_cost,
    DROP CHECK ck_replenishment_execution_proposal_purchase_fields,
    DROP CHECK ck_replenishment_execution_proposal_transfer_fields,
    DROP COLUMN mapping_evidence_sha256,
    DROP COLUMN supplier_id,
    DROP COLUMN account_id,
    DROP COLUMN erp_product_id,
    DROP COLUMN erp_product_unit_id,
    DROP COLUMN unit_cost_minor,
    DROP COLUMN tax_percent,
    DROP COLUMN source_warehouse_id,
    DROP COLUMN target_warehouse_id,
    DROP COLUMN wms_sku_id,
    ADD COLUMN owner_type VARCHAR(32) NULL AFTER target_type,
    ADD COLUMN owner_id VARCHAR(128) NULL AFTER owner_type,
    ADD COLUMN source_warehouse_id VARCHAR(128) NULL AFTER owner_id,
    ADD COLUMN target_warehouse_id VARCHAR(128) NULL AFTER source_warehouse_id,
    ADD CONSTRAINT ck_replenishment_execution_proposal_native_dimensions CHECK (
        (target_type = 'PURCHASE_REQUEST'
         AND owner_type IS NULL AND owner_id IS NULL
         AND source_warehouse_id IS NULL AND target_warehouse_id IS NULL)
        OR
        (target_type = 'TRANSFER_REQUEST'
         AND owner_type REGEXP '^[A-Z][A-Z0-9_]{0,31}$'
         AND CHAR_LENGTH(owner_id) BETWEEN 1 AND 128
         AND CHAR_LENGTH(source_warehouse_id) BETWEEN 1 AND 128
         AND CHAR_LENGTH(target_warehouse_id) BETWEEN 1 AND 128
         AND source_warehouse_id <> target_warehouse_id)
    );
