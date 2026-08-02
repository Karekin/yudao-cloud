SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS cloudmold_assert_procurement_release_authority;
DELIMITER $$
CREATE PROCEDURE cloudmold_assert_procurement_release_authority()
BEGIN
    IF EXISTS (SELECT 1 FROM cloudmold_purchase_requisition LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'existing purchase requisitions must be remediated before locking canonical authority fields';
    END IF;
    IF EXISTS (SELECT 1 FROM cloudmold_purchase_requisition_line LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'existing purchase requisition lines must be remediated before locking valuation authority fields';
    END IF;
    IF EXISTS (SELECT 1 FROM cloudmold_procurement_award_snapshot LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'existing award snapshots must be rebuilt before locking immutable release authority fields';
    END IF;
    IF EXISTS (SELECT 1 FROM cloudmold_procurement_award_snapshot_line LIMIT 1) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'existing award snapshot lines must be rebuilt before locking immutable valuation fields';
    END IF;
END$$
DELIMITER ;
CALL cloudmold_assert_procurement_release_authority();
DROP PROCEDURE cloudmold_assert_procurement_release_authority;

ALTER TABLE cloudmold_purchase_requisition
    ADD COLUMN legal_entity_id VARCHAR(128) NOT NULL AFTER source_business_ref,
    ADD COLUMN tax_calculation_policy_code VARCHAR(64) NOT NULL AFTER legal_entity_id,
    ADD COLUMN rounding_policy_code VARCHAR(64) NOT NULL AFTER tax_calculation_policy_code,
    ADD CONSTRAINT ck_purchase_requisition_legal_entity_id
        CHECK(REGEXP_LIKE(legal_entity_id,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$','c')),
    ADD CONSTRAINT ck_purchase_requisition_tax_policy
        CHECK(REGEXP_LIKE(tax_calculation_policy_code,'^[A-Z][A-Z0-9_]{0,63}$','c')),
    ADD CONSTRAINT ck_purchase_requisition_rounding_policy
        CHECK(REGEXP_LIKE(rounding_policy_code,'^[A-Z][A-Z0-9_]{0,63}$','c'));

ALTER TABLE cloudmold_purchase_requisition_line
    ADD COLUMN valuation_policy_id VARCHAR(128) NOT NULL AFTER uom_code,
    ADD COLUMN valuation_policy_version VARCHAR(64) NOT NULL AFTER valuation_policy_id,
    ADD COLUMN valuation_policy_hash CHAR(64) NOT NULL AFTER valuation_policy_version,
    ADD CONSTRAINT ck_purchase_requisition_line_valuation_policy_id
        CHECK(REGEXP_LIKE(valuation_policy_id,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$','c')),
    ADD CONSTRAINT ck_purchase_requisition_line_valuation_policy_version
        CHECK(REGEXP_LIKE(valuation_policy_version,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}$','c')),
    ADD CONSTRAINT ck_purchase_requisition_line_valuation_policy_hash
        CHECK(REGEXP_LIKE(valuation_policy_hash,'^[0-9a-f]{64}$','c'));

ALTER TABLE cloudmold_procurement_award_snapshot
    ADD COLUMN requisition_id VARCHAR(128) NOT NULL AFTER event_version,
    ADD COLUMN requisition_version BIGINT NOT NULL AFTER requisition_id,
    ADD COLUMN legal_entity_id VARCHAR(128) NOT NULL AFTER requisition_version,
    ADD COLUMN tax_calculation_policy_code VARCHAR(64) NOT NULL AFTER legal_entity_id,
    ADD COLUMN rounding_policy_code VARCHAR(64) NOT NULL AFTER tax_calculation_policy_code,
    ADD KEY idx_award_snapshot_requisition(tenant_id,requisition_id),
    ADD CONSTRAINT fk_award_snapshot_requisition
        FOREIGN KEY(tenant_id,requisition_id) REFERENCES cloudmold_purchase_requisition(tenant_id,requisition_id),
    ADD CONSTRAINT ck_award_snapshot_requisition_version CHECK(requisition_version > 0),
    ADD CONSTRAINT ck_award_snapshot_legal_entity_id
        CHECK(REGEXP_LIKE(legal_entity_id,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$','c')),
    ADD CONSTRAINT ck_award_snapshot_tax_policy
        CHECK(REGEXP_LIKE(tax_calculation_policy_code,'^[A-Z][A-Z0-9_]{0,63}$','c')),
    ADD CONSTRAINT ck_award_snapshot_rounding_policy
        CHECK(REGEXP_LIKE(rounding_policy_code,'^[A-Z][A-Z0-9_]{0,63}$','c'));

ALTER TABLE cloudmold_procurement_award_snapshot_line
    ADD COLUMN requisition_line_id VARCHAR(128) NOT NULL AFTER reviewer_evidence_sha256,
    ADD COLUMN requisition_schedule_id VARCHAR(128) NOT NULL AFTER requisition_line_id,
    ADD COLUMN valuation_policy_id VARCHAR(128) NOT NULL AFTER requisition_schedule_id,
    ADD COLUMN valuation_policy_version VARCHAR(64) NOT NULL AFTER valuation_policy_id,
    ADD COLUMN valuation_policy_hash CHAR(64) NOT NULL AFTER valuation_policy_version,
    ADD KEY idx_award_snapshot_line_requisition_line(tenant_id,requisition_line_id),
    ADD KEY idx_award_snapshot_line_requisition_schedule(tenant_id,requisition_schedule_id),
    ADD CONSTRAINT fk_award_snapshot_line_requisition_line
        FOREIGN KEY(tenant_id,requisition_line_id) REFERENCES cloudmold_purchase_requisition_line(tenant_id,line_id),
    ADD CONSTRAINT fk_award_snapshot_line_requisition_schedule
        FOREIGN KEY(tenant_id,requisition_schedule_id) REFERENCES cloudmold_purchase_requisition_delivery_schedule(tenant_id,schedule_id),
    ADD CONSTRAINT ck_award_snapshot_line_valuation_policy_id
        CHECK(REGEXP_LIKE(valuation_policy_id,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$','c')),
    ADD CONSTRAINT ck_award_snapshot_line_valuation_policy_version
        CHECK(REGEXP_LIKE(valuation_policy_version,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}$','c')),
    ADD CONSTRAINT ck_award_snapshot_line_valuation_policy_hash
        CHECK(REGEXP_LIKE(valuation_policy_hash,'^[0-9a-f]{64}$','c'));

ALTER TABLE cloudmold_procurement_order
    DROP INDEX uk_procurement_source_ref,
    ADD UNIQUE KEY uk_procurement_source_supplier_currency
        (tenant_id,source_business_type,source_business_ref,supplier_id,currency_code);
