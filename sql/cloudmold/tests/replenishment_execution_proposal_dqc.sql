-- All statements must return zero rows.

SELECT proposal_id
FROM cloudmold_replenishment_execution_proposal
WHERE expected_recommendation_version <= 0
   OR mapping_evidence_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR policy_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR policy_code NOT REGEXP '^[A-Z][A-Z0-9_]{0,63}$';

SELECT proposal.proposal_id
FROM cloudmold_replenishment_execution_proposal proposal
LEFT JOIN cloudmold_replenishment_recommendation recommendation
  ON recommendation.tenant_id=proposal.tenant_id
 AND recommendation.recommendation_id=proposal.recommendation_id
WHERE recommendation.recommendation_id IS NULL;

SELECT tenant_id, recommendation_id, COUNT(*) AS ready_count
FROM cloudmold_replenishment_execution_proposal
WHERE status='READY'
GROUP BY tenant_id, recommendation_id
HAVING COUNT(*) > 1;

SELECT proposal.proposal_id
FROM cloudmold_replenishment_execution_proposal proposal
JOIN cloudmold_replenishment_recommendation recommendation
  ON recommendation.tenant_id=proposal.tenant_id
 AND recommendation.recommendation_id=proposal.recommendation_id
LEFT JOIN cloudmold_replenishment_conversion conversion
  ON conversion.tenant_id=proposal.tenant_id
 AND conversion.recommendation_id=proposal.recommendation_id
WHERE proposal.status='READY'
  AND (
      recommendation.status <> 'APPROVED'
      OR recommendation.version <> proposal.expected_recommendation_version
      OR conversion.conversion_id IS NOT NULL
  );

SELECT proposal_id
FROM cloudmold_replenishment_execution_proposal
WHERE (
        target_type='PURCHASE_REQUEST'
        AND (
            supplier_id IS NULL OR supplier_id <= 0
            OR account_id IS NULL OR account_id <= 0
            OR erp_product_id IS NULL OR erp_product_id <= 0
            OR erp_product_unit_id IS NULL OR erp_product_unit_id <= 0
            OR tax_percent IS NULL OR tax_percent < 0 OR tax_percent > 100
            OR source_warehouse_id IS NOT NULL
            OR wms_sku_id IS NOT NULL
        )
    )
   OR (
        target_type='TRANSFER_REQUEST'
        AND (
            supplier_id IS NOT NULL OR account_id IS NOT NULL
            OR erp_product_id IS NOT NULL OR erp_product_unit_id IS NOT NULL
            OR tax_percent IS NOT NULL
            OR source_warehouse_id IS NULL OR source_warehouse_id <= 0
            OR target_warehouse_id IS NULL OR target_warehouse_id <= 0
            OR source_warehouse_id=target_warehouse_id
            OR wms_sku_id IS NULL OR wms_sku_id <= 0
        )
    );

SELECT proposal_id
FROM cloudmold_replenishment_execution_proposal
WHERE (
        status='READY'
        AND (
            version <> 1
            OR consumed_by_conversion_id IS NOT NULL
            OR consumed_at IS NOT NULL
        )
    )
   OR (
        status='CONSUMED'
        AND (
            version <> 2
            OR consumed_by_conversion_id IS NULL
            OR consumed_at IS NULL
        )
    );
