-- Canonical CloudMold Inventory Scrap document and inventory disposition slice.
-- Warehouse owns the scrap document, approval, and disposition batches.
-- Inventory owns the stock deduction through a dedicated scrap disposition API.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(128) NULL,
    operation_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    scrap_id VARCHAR(128) NULL,
    scrap_code VARCHAR(64) NULL,
    batch_id VARCHAR(128) NULL,
    batch_no VARCHAR(64) NULL,
    result_status VARCHAR(32) NULL,
    processed_line_count INT NULL,
    aggregate_version BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_cm_inventory_scrap_operation_idem (tenant_id, idempotency_key),
    CONSTRAINT ck_cm_inventory_scrap_operation_type CHECK (
        operation_type IN ('CREATE_DRAFT','SUBMIT','APPROVE','RECORD_DISPOSITION_BATCH','COMPLETE','CANCEL')),
    CONSTRAINT ck_cm_inventory_scrap_operation_status CHECK (status IN (0,10)),
    CONSTRAINT ck_cm_inventory_scrap_operation_hash CHECK (REGEXP_LIKE(request_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Idempotent typed warehouse inventory scrap operation';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_document (
    scrap_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    scrap_code VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    owner_type VARCHAR(64) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    warehouse_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    total_requested_quantity DECIMAL(24,6) NOT NULL,
    total_disposed_quantity DECIMAL(24,6) NOT NULL,
    line_count INT NOT NULL,
    requested_by_principal_id VARCHAR(128) NOT NULL,
    submitted_by_principal_id VARCHAR(128) NULL,
    approved_by_principal_id VARCHAR(128) NULL,
    completed_by_principal_id VARCHAR(128) NULL,
    cancelled_by_principal_id VARCHAR(128) NULL,
    approved_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (scrap_id),
    UNIQUE KEY uk_cm_inventory_scrap_document_code (tenant_id, scrap_code),
    KEY idx_cm_inventory_scrap_document_status (tenant_id, status, updated_at),
    CONSTRAINT ck_cm_inventory_scrap_document_status CHECK (
        status IN ('DRAFT','SUBMITTED','APPROVED','PARTIALLY_DISPOSED','DISPOSED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_cm_inventory_scrap_document_qty CHECK (
        total_requested_quantity > 0 AND total_disposed_quantity >= 0
        AND total_disposed_quantity <= total_requested_quantity AND line_count > 0),
    CONSTRAINT ck_cm_inventory_scrap_document_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Warehouse-owned canonical inventory scrap document header';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_line (
    line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    scrap_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    location_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    stock_status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    requested_quantity DECIMAL(24,6) NOT NULL,
    disposed_quantity DECIMAL(24,6) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    evidence_ref VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (line_id),
    UNIQUE KEY uk_cm_inventory_scrap_line_no (tenant_id, scrap_id, line_number),
    CONSTRAINT fk_cm_inventory_scrap_line_document FOREIGN KEY (scrap_id)
        REFERENCES cloudmold_inventory_scrap_document (scrap_id),
    CONSTRAINT ck_cm_inventory_scrap_line_qty CHECK (
        requested_quantity > 0 AND disposed_quantity >= 0 AND disposed_quantity <= requested_quantity),
    CONSTRAINT ck_cm_inventory_scrap_line_status CHECK (
        status IN ('DRAFT','SUBMITTED','APPROVED','PARTIALLY_DISPOSED','DISPOSED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_cm_inventory_scrap_line_evidence CHECK (
        evidence_type IN ('QUALITY','COUNT','INVENTORY')),
    CONSTRAINT ck_cm_inventory_scrap_line_stock_status CHECK (
        stock_status IN ('SELLABLE','NON_SELLABLE')),
    CONSTRAINT ck_cm_inventory_scrap_line_quality_status CHECK (
        quality_status IN ('PENDING_QC','QUALIFIED','DAMAGED','REJECTED')),
    CONSTRAINT ck_cm_inventory_scrap_line_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable warehouse scrap inventory dimension and evidence grain';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_disposition_batch (
    batch_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    scrap_id VARCHAR(128) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    disposition_type VARCHAR(32) NOT NULL,
    proof_type VARCHAR(64) NOT NULL,
    proof_ref VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_disposed_quantity DECIMAL(24,6) NOT NULL,
    line_count INT NOT NULL,
    version BIGINT NOT NULL,
    executed_by_principal_id VARCHAR(128) NOT NULL,
    remark VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (batch_id),
    UNIQUE KEY uk_cm_inventory_scrap_batch_no (tenant_id, batch_no),
    KEY idx_cm_inventory_scrap_batch_parent (tenant_id, scrap_id, occurred_at),
    CONSTRAINT fk_cm_inventory_scrap_batch_document FOREIGN KEY (scrap_id)
        REFERENCES cloudmold_inventory_scrap_document (scrap_id),
    CONSTRAINT ck_cm_inventory_scrap_batch_type CHECK (disposition_type IN ('DESTROYED','RECYCLED')),
    CONSTRAINT ck_cm_inventory_scrap_batch_status CHECK (status IN ('COMPLETED')),
    CONSTRAINT ck_cm_inventory_scrap_batch_qty CHECK (total_disposed_quantity > 0 AND line_count > 0),
    CONSTRAINT ck_cm_inventory_scrap_batch_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Warehouse-owned inventory scrap disposition batch with proof';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_disposition_line (
    disposition_line_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    batch_id VARCHAR(128) NOT NULL,
    scrap_id VARCHAR(128) NOT NULL,
    scrap_line_id VARCHAR(128) NOT NULL,
    line_number INT NOT NULL,
    canonical_sku_id VARCHAR(128) NOT NULL,
    location_id VARCHAR(128) NOT NULL,
    lot_id VARCHAR(128) NULL,
    stock_status VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    base_uom_code VARCHAR(64) NOT NULL,
    disposed_quantity DECIMAL(24,6) NOT NULL,
    cumulative_disposed_quantity DECIMAL(24,6) NOT NULL,
    inventory_idempotency_key VARCHAR(128) NOT NULL,
    inventory_operation_id BIGINT NOT NULL,
    inventory_ledger_transaction_id BIGINT NOT NULL,
    inventory_balance_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (disposition_line_id),
    UNIQUE KEY uk_cm_inventory_scrap_disposition_inventory_idem (tenant_id, inventory_idempotency_key),
    KEY idx_cm_inventory_scrap_disposition_parent (tenant_id, scrap_id, batch_id),
    CONSTRAINT fk_cm_inventory_scrap_disposition_batch FOREIGN KEY (batch_id)
        REFERENCES cloudmold_inventory_scrap_disposition_batch (batch_id),
    CONSTRAINT ck_cm_inventory_scrap_disposition_qty CHECK (
        disposed_quantity > 0 AND cumulative_disposed_quantity >= disposed_quantity),
    CONSTRAINT ck_cm_inventory_scrap_disposition_status CHECK (status IN ('COMPLETED')),
    CONSTRAINT ck_cm_inventory_scrap_disposition_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Batch-line warehouse disposition linked to dedicated inventory deduction result';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    scrap_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    status_version BIGINT NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    note VARCHAR(255) NULL,
    operation_id BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    KEY idx_cm_inventory_scrap_history_parent (tenant_id, scrap_id, changed_at),
    CONSTRAINT fk_cm_inventory_scrap_history_document FOREIGN KEY (scrap_id)
        REFERENCES cloudmold_inventory_scrap_document (scrap_id),
    CONSTRAINT ck_cm_inventory_scrap_history_status CHECK (
        status IN ('DRAFT','SUBMITTED','APPROVED','PARTIALLY_DISPOSED','DISPOSED','COMPLETED','CANCELLED')),
    CONSTRAINT ck_cm_inventory_scrap_history_version CHECK (status_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Append-only scrap document status history';

CREATE TABLE IF NOT EXISTS cloudmold_inventory_scrap_disposition_operation_v1 (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    source_event_id VARCHAR(128) NULL,
    disposition_line_id VARCHAR(128) NOT NULL,
    scrap_document_id VARCHAR(128) NOT NULL,
    scrap_line_id VARCHAR(128) NOT NULL,
    disposition_batch_id VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    ledger_transaction_id BIGINT NULL,
    balance_id VARCHAR(128) NULL,
    aggregate_version BIGINT NULL,
    on_hand_quantity DECIMAL(24,6) NULL,
    disposed_quantity DECIMAL(24,6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_cm_inventory_scrap_disposition_op_idem (tenant_id, idempotency_key),
    CONSTRAINT ck_cm_inventory_scrap_disposition_op_status CHECK (status IN (0,10)),
    CONSTRAINT ck_cm_inventory_scrap_disposition_op_hash CHECK (REGEXP_LIKE(request_hash, '^[0-9a-f]{64}$', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Inventory-owned dedicated scrap deduction idempotency operation';

DROP TEMPORARY TABLE IF EXISTS cloudmold_v165_menu_contract;
CREATE TEMPORARY TABLE cloudmold_v165_menu_contract (
  id BIGINT NOT NULL PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  type TINYINT NOT NULL,
  sort INT NOT NULL,
  parent_id BIGINT NOT NULL,
  path VARCHAR(200) NOT NULL,
  icon VARCHAR(100) NOT NULL,
  component VARCHAR(255) NULL,
  component_name VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO cloudmold_v165_menu_contract
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name)
VALUES
  (9100000000507,'库存报废','cloudmold:warehouse:inventory-scrap:query',2,4,9100000000492,'inventory-scraps',
   'lucide:trash-2','cloudmold/warehouse/inventory-scrap/index','CloudMoldInventoryScrap'),
  (9100000000508,'库存报废查询','cloudmold:warehouse:inventory-scrap:query',3,1,9100000000507,'','',NULL,NULL),
  (9100000000509,'库存报废操作','cloudmold:warehouse:inventory-scrap:command',3,2,9100000000507,'','',NULL,NULL);

INSERT INTO system_menu
  (id,name,permission,type,sort,parent_id,path,icon,component,component_name,
   status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted)
SELECT expected.id,expected.name,expected.permission,expected.type,expected.sort,expected.parent_id,
       expected.path,expected.icon,expected.component,expected.component_name,
       0,b'1',b'1',b'1','CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-inventory-scrap-document',UTC_TIMESTAMP(6),b'0'
FROM cloudmold_v165_menu_contract expected
LEFT JOIN system_menu actual ON actual.id = expected.id
WHERE actual.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS cloudmold_v165_eligible_package;
CREATE TEMPORARY TABLE cloudmold_v165_eligible_package (
  package_id BIGINT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
INSERT INTO cloudmold_v165_eligible_package (package_id)
SELECT id FROM system_tenant_package
WHERE deleted=b'0' AND status=0 AND JSON_VALID(menu_ids)
  AND JSON_CONTAINS(CAST(menu_ids AS JSON), CAST(9100000000140 AS JSON));

DROP TEMPORARY TABLE IF EXISTS cloudmold_v165_package_menu;
CREATE TEMPORARY TABLE cloudmold_v165_package_menu (
  package_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (package_id, menu_id)
) ENGINE=InnoDB;
INSERT IGNORE INTO cloudmold_v165_package_menu (package_id, menu_id)
SELECT eligible.package_id, existing.menu_id
FROM cloudmold_v165_eligible_package eligible
JOIN system_tenant_package package ON package.id = eligible.package_id
JOIN JSON_TABLE(CAST(package.menu_ids AS JSON), '$[*]' COLUMNS(menu_id BIGINT PATH '$')) existing;
INSERT IGNORE INTO cloudmold_v165_package_menu (package_id, menu_id)
SELECT eligible.package_id, expected.id
FROM cloudmold_v165_eligible_package eligible
CROSS JOIN cloudmold_v165_menu_contract expected;
UPDATE system_tenant_package package
JOIN (
  SELECT package_id, JSON_ARRAYAGG(menu_id) AS menu_ids
  FROM cloudmold_v165_package_menu GROUP BY package_id
) merged ON merged.package_id = package.id
SET package.menu_ids = CAST(merged.menu_ids AS CHAR CHARACTER SET utf8mb4),
    package.updater = 'CloudMold:canonical-inventory-scrap-document',
    package.update_time = UTC_TIMESTAMP(6);

INSERT INTO system_role_menu
  (role_id,menu_id,tenant_id,creator,create_time,updater,update_time,deleted)
SELECT role.id,expected.id,role.tenant_id,'CloudMold',UTC_TIMESTAMP(6),
       'CloudMold:canonical-inventory-scrap-document',UTC_TIMESTAMP(6),b'0'
FROM system_role role
JOIN system_tenant tenant ON tenant.id = role.tenant_id AND tenant.deleted = b'0' AND tenant.status = 0
JOIN cloudmold_v165_eligible_package eligible ON eligible.package_id = tenant.package_id
CROSS JOIN cloudmold_v165_menu_contract expected
WHERE role.code='tenant_admin' AND role.deleted=b'0' AND role.status=0
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu current
    WHERE current.role_id=role.id AND current.menu_id=expected.id
      AND current.tenant_id=role.tenant_id AND current.deleted=b'0'
  );

DROP TEMPORARY TABLE cloudmold_v165_package_menu;
DROP TEMPORARY TABLE cloudmold_v165_eligible_package;
DROP TEMPORARY TABLE cloudmold_v165_menu_contract;
