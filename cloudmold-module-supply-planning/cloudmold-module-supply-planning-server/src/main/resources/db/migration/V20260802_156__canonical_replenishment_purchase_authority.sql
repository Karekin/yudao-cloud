-- Freeze the complete canonical procurement authority on governed replenishment proposals.
-- Existing purchase proposals cannot be inferred and must be remediated before this migration.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS cloudmold_assert_replenishment_purchase_authority;
DELIMITER $$
CREATE PROCEDURE cloudmold_assert_replenishment_purchase_authority()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM cloudmold_replenishment_execution_proposal
        WHERE target_type = 'PURCHASE_REQUEST'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'existing purchase proposals require governed authority remediation';
    END IF;
END$$
DELIMITER ;
CALL cloudmold_assert_replenishment_purchase_authority();
DROP PROCEDURE cloudmold_assert_replenishment_purchase_authority;

ALTER TABLE cloudmold_replenishment_execution_proposal
    ADD COLUMN legal_entity_id VARCHAR(128) NULL AFTER target_warehouse_id,
    ADD COLUMN tax_calculation_policy_code VARCHAR(64) NULL AFTER legal_entity_id,
    ADD COLUMN rounding_policy_code VARCHAR(64) NULL AFTER tax_calculation_policy_code,
    ADD COLUMN valuation_policy_id VARCHAR(128) NULL AFTER rounding_policy_code,
    ADD COLUMN valuation_policy_version VARCHAR(64) NULL AFTER valuation_policy_id,
    ADD COLUMN valuation_policy_hash CHAR(64) NULL AFTER valuation_policy_version,
    ADD CONSTRAINT ck_replenishment_execution_proposal_purchase_authority CHECK (
        (target_type = 'PURCHASE_REQUEST'
         AND CHAR_LENGTH(legal_entity_id) BETWEEN 1 AND 128
         AND tax_calculation_policy_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
         AND rounding_policy_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
         AND CHAR_LENGTH(valuation_policy_id) BETWEEN 1 AND 128
         AND CHAR_LENGTH(valuation_policy_version) BETWEEN 1 AND 64
         AND valuation_policy_hash REGEXP '^[0-9a-f]{64}$')
        OR
        (target_type = 'TRANSFER_REQUEST'
         AND legal_entity_id IS NULL
         AND tax_calculation_policy_code IS NULL
         AND rounding_policy_code IS NULL
         AND valuation_policy_id IS NULL
         AND valuation_policy_version IS NULL
         AND valuation_policy_hash IS NULL)
    );
