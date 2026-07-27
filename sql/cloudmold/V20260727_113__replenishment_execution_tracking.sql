ALTER TABLE cloudmold_replenishment_conversion
    ADD COLUMN source_system VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' AFTER target_reference,
    ADD COLUMN document_type VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' AFTER source_system,
    ADD COLUMN external_document_id VARCHAR(128) NOT NULL DEFAULT '' AFTER document_type,
    ADD COLUMN external_document_no VARCHAR(128) NULL AFTER external_document_id,
    ADD COLUMN document_status VARCHAR(32) NOT NULL DEFAULT 'PREPARE' AFTER external_document_no,
    ADD COLUMN next_waiting_event_code VARCHAR(64) NOT NULL DEFAULT 'MANUAL_FOLLOW_UP' AFTER document_status,
    ADD COLUMN next_waiting_event_label VARCHAR(128) NOT NULL DEFAULT '等待人工跟进' AFTER next_waiting_event_code;

UPDATE cloudmold_replenishment_conversion
SET source_system = SUBSTRING_INDEX(target_reference, ':', 1),
    document_type = SUBSTRING_INDEX(SUBSTRING_INDEX(target_reference, ':', 2), ':', -1),
    external_document_id = SUBSTRING_INDEX(target_reference, ':', -1),
    document_status = 'PREPARE',
    next_waiting_event_code = CASE target_type
        WHEN 'PURCHASE_REQUEST' THEN 'SUPPLIER_CONFIRMATION'
        WHEN 'TRANSFER_REQUEST' THEN 'TRANSFER_OUTBOUND'
        ELSE 'MANUAL_FOLLOW_UP'
    END,
    next_waiting_event_label = CASE target_type
        WHEN 'PURCHASE_REQUEST' THEN '等待供应商确认采购单'
        WHEN 'TRANSFER_REQUEST' THEN '等待调拨出库'
        ELSE '等待人工跟进'
    END
WHERE source_system = 'UNKNOWN';

ALTER TABLE cloudmold_replenishment_conversion
    ADD CONSTRAINT ck_replenishment_conversion_source_system CHECK (
        source_system REGEXP '^[A-Z][A-Z0-9_]{1,31}$'),
    ADD CONSTRAINT ck_replenishment_conversion_document_type CHECK (
        document_type REGEXP '^[A-Z][A-Z0-9_]{1,31}$'),
    ADD CONSTRAINT ck_replenishment_conversion_external_document_id CHECK (
        CHAR_LENGTH(external_document_id) BETWEEN 1 AND 128),
    ADD CONSTRAINT ck_replenishment_conversion_document_status CHECK (
        document_status REGEXP '^[A-Z][A-Z0-9_]{1,31}$'),
    ADD CONSTRAINT ck_replenishment_conversion_next_waiting_event_code CHECK (
        next_waiting_event_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    ADD CONSTRAINT ck_replenishment_conversion_next_waiting_event_label CHECK (
        CHAR_LENGTH(next_waiting_event_label) BETWEEN 2 AND 128);
