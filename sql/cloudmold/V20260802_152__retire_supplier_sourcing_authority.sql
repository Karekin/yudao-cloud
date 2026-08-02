-- Supplier owns supplier master data, admission and performance only.
-- Procurement owns RFQ, quotation, evaluation and award. Any legacy sourcing fact
-- blocks the upgrade because there is no lossless or compatible runtime migration.

DROP PROCEDURE IF EXISTS cloudmold_retire_supplier_sourcing_authority;
DELIMITER $$
CREATE PROCEDURE cloudmold_retire_supplier_sourcing_authority()
BEGIN
    DECLARE legacy_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO legacy_count FROM cloudmold_supplier_sample_evaluation;
    IF legacy_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V152 blocked: cloudmold_supplier_sample_evaluation contains legacy sourcing authority';
    END IF;

    SELECT COUNT(*) INTO legacy_count FROM cloudmold_supplier_quote;
    IF legacy_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V152 blocked: cloudmold_supplier_quote contains legacy sourcing authority';
    END IF;

    SELECT COUNT(*) INTO legacy_count FROM cloudmold_supplier_sourcing_case;
    IF legacy_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V152 blocked: cloudmold_supplier_sourcing_case contains legacy sourcing authority';
    END IF;

    SELECT COUNT(*) INTO legacy_count
      FROM cloudmold_supplier_operation
     WHERE command_type NOT IN ('REGISTER_SUPPLIER','SUBMIT_ADMISSION','APPROVE_ADMISSION');
    IF legacy_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V152 blocked: cloudmold_supplier_operation contains non-profile commands';
    END IF;

    SELECT COUNT(*) INTO legacy_count
      FROM cloudmold_supplier_profile
     WHERE submitted_by_principal_id IS NOT NULL
       AND submitted_by_principal_id = admitted_by_principal_id;
    IF legacy_count <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V152 blocked: supplier admission violates maker-checker separation';
    END IF;
END$$
DELIMITER ;

CALL cloudmold_retire_supplier_sourcing_authority();
DROP PROCEDURE cloudmold_retire_supplier_sourcing_authority;

DROP TABLE cloudmold_supplier_sample_evaluation;
DROP TABLE cloudmold_supplier_quote;
DROP TABLE cloudmold_supplier_sourcing_case;

RENAME TABLE cloudmold_supplier_operation TO cloudmold_supplier_profile_operation;
ALTER TABLE cloudmold_supplier_profile_operation
    RENAME INDEX uk_supplier_operation TO uk_supplier_profile_operation,
    COMMENT = 'Idempotent canonical supplier master-data and admission command envelope',
    ADD CONSTRAINT ck_supplier_profile_operation_type CHECK (
        command_type IN ('REGISTER_SUPPLIER','SUBMIT_ADMISSION','APPROVE_ADMISSION')),
    ADD CONSTRAINT ck_supplier_profile_operation_status CHECK (status IN (0,10));

ALTER TABLE cloudmold_supplier_profile
    ADD CONSTRAINT ck_supplier_admission_maker_checker CHECK (
        submitted_by_principal_id IS NULL
        OR admitted_by_principal_id IS NULL
        OR submitted_by_principal_id <> admitted_by_principal_id);
