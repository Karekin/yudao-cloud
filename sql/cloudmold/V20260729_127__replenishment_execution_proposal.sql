-- Governed AI execution proposals for approved replenishment recommendations.
-- A proposal freezes the purchase/transfer mapping inputs; it is not evidence
-- that a downstream draft or business document has been created.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_replenishment_execution_proposal (
    proposal_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    recommendation_id VARCHAR(128) NOT NULL,
    expected_recommendation_version BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    mapping_evidence_sha256 CHAR(64) NOT NULL,
    supplier_id BIGINT NULL,
    account_id BIGINT NULL,
    erp_product_id BIGINT NULL,
    erp_product_unit_id BIGINT NULL,
    unit_cost_minor BIGINT NOT NULL,
    tax_percent DECIMAL(10, 4) NULL,
    source_warehouse_id BIGINT NULL,
    target_warehouse_id BIGINT NULL,
    wms_sku_id BIGINT NULL,
    proposed_by_principal_id VARCHAR(128) NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    policy_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    consumed_by_conversion_id VARCHAR(128) NULL,
    proposed_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    ready_recommendation_id VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE WHEN status = 'READY' THEN recommendation_id ELSE NULL END
        ) STORED,
    PRIMARY KEY (proposal_id),
    UNIQUE KEY uk_replenishment_execution_proposal_tenant_id
        (tenant_id, proposal_id),
    UNIQUE KEY uk_replenishment_execution_proposal_ready
        (tenant_id, ready_recommendation_id),
    KEY idx_replenishment_execution_proposal_queue
        (tenant_id, status, proposed_at, proposal_id),
    KEY idx_replenishment_execution_proposal_recommendation
        (tenant_id, recommendation_id, status),
    CONSTRAINT fk_replenishment_execution_proposal_recommendation
        FOREIGN KEY (tenant_id, recommendation_id)
        REFERENCES cloudmold_replenishment_recommendation (tenant_id, recommendation_id),
    CONSTRAINT ck_replenishment_execution_proposal_expected_version CHECK (
        expected_recommendation_version > 0),
    CONSTRAINT ck_replenishment_execution_proposal_target CHECK (
        target_type IN ('PURCHASE_REQUEST', 'TRANSFER_REQUEST')),
    CONSTRAINT ck_replenishment_execution_proposal_mapping_hash CHECK (
        mapping_evidence_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_replenishment_execution_proposal_policy CHECK (
        policy_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
        AND policy_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_replenishment_execution_proposal_cost CHECK (
        unit_cost_minor >= 0),
    CONSTRAINT ck_replenishment_execution_proposal_purchase_fields CHECK (
        target_type <> 'PURCHASE_REQUEST'
        OR (
            supplier_id IS NOT NULL AND supplier_id > 0
            AND account_id IS NOT NULL AND account_id > 0
            AND erp_product_id IS NOT NULL AND erp_product_id > 0
            AND erp_product_unit_id IS NOT NULL AND erp_product_unit_id > 0
            AND tax_percent IS NOT NULL AND tax_percent BETWEEN 0 AND 100
            AND source_warehouse_id IS NULL
            AND (target_warehouse_id IS NULL OR target_warehouse_id > 0)
            AND wms_sku_id IS NULL
        )),
    CONSTRAINT ck_replenishment_execution_proposal_transfer_fields CHECK (
        target_type <> 'TRANSFER_REQUEST'
        OR (
            supplier_id IS NULL
            AND account_id IS NULL
            AND erp_product_id IS NULL
            AND erp_product_unit_id IS NULL
            AND tax_percent IS NULL
            AND source_warehouse_id IS NOT NULL AND source_warehouse_id > 0
            AND target_warehouse_id IS NOT NULL AND target_warehouse_id > 0
            AND source_warehouse_id <> target_warehouse_id
            AND wms_sku_id IS NOT NULL AND wms_sku_id > 0
        )),
    CONSTRAINT ck_replenishment_execution_proposal_status CHECK (
        status IN ('READY', 'CONSUMED')),
    CONSTRAINT ck_replenishment_execution_proposal_lifecycle CHECK (
        (
            status = 'READY'
            AND version = 1
            AND consumed_by_conversion_id IS NULL
            AND consumed_at IS NULL
        )
        OR (
            status = 'CONSUMED'
            AND version = 2
            AND consumed_by_conversion_id IS NOT NULL
            AND consumed_at IS NOT NULL
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='AI-proposed, governed replenishment execution inputs';
