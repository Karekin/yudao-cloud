-- Every query must return zero. These checks validate the canonical Merchant OLTP mapping authority.

SELECT COUNT(*) AS source_identity_normalization_violation
FROM cloudmold_merchant_source_mapping
WHERE CHAR_LENGTH(TRIM(source_system))=0
   OR CHAR_LENGTH(TRIM(source_type))=0
   OR CHAR_LENGTH(TRIM(source_id))=0
   OR BINARY source_system<>BINARY UPPER(TRIM(source_system))
   OR BINARY source_type<>BINARY UPPER(TRIM(source_type))
   OR BINARY source_id<>BINARY TRIM(source_id);

SELECT COUNT(*) AS canonical_source_id_reuse_violation
FROM cloudmold_merchant_source_mapping
WHERE BINARY target_id=BINARY source_id;

SELECT COUNT(*) AS source_mapping_evidence_violation
FROM cloudmold_merchant_source_mapping
WHERE CHAR_LENGTH(TRIM(verification_ref))=0
   OR CHAR_LENGTH(TRIM(migration_run_id))=0;

SELECT COUNT(*) AS source_mapping_target_shape_violation
FROM cloudmold_merchant_source_mapping
WHERE NOT (
  (target_type='LEGAL_ENTITY' AND BINARY target_id=BINARY legal_entity_id
    AND merchant_id IS NULL AND shop_id IS NULL)
  OR (target_type='MERCHANT' AND BINARY target_id=BINARY merchant_id
    AND legal_entity_id IS NULL AND shop_id IS NULL)
  OR (target_type='SHOP' AND BINARY target_id=BINARY shop_id
    AND legal_entity_id IS NULL AND merchant_id IS NULL)
);

SELECT COUNT(*) AS source_mapping_target_orphan_violation
FROM cloudmold_merchant_source_mapping mapping
LEFT JOIN cloudmold_merchant_legal_entity legal
  ON legal.tenant_id=mapping.tenant_id AND legal.legal_entity_id=mapping.legal_entity_id
LEFT JOIN cloudmold_merchant_account merchant
  ON merchant.tenant_id=mapping.tenant_id AND merchant.merchant_id=mapping.merchant_id
LEFT JOIN cloudmold_merchant_shop shop
  ON shop.tenant_id=mapping.tenant_id AND shop.shop_id=mapping.shop_id
WHERE (mapping.target_type='LEGAL_ENTITY' AND legal.legal_entity_id IS NULL)
   OR (mapping.target_type='MERCHANT' AND merchant.merchant_id IS NULL)
   OR (mapping.target_type='SHOP' AND shop.shop_id IS NULL);

SELECT COUNT(*) AS active_source_mapping_duplicate_violation
FROM (
  SELECT tenant_id,source_system,source_type,source_id,COUNT(*) AS mapping_count
  FROM cloudmold_merchant_source_mapping
  WHERE status='ACTIVE'
  GROUP BY tenant_id,source_system,source_type,source_id
  HAVING COUNT(*)>1
) duplicate_mapping;

SELECT COUNT(*) AS active_source_mapping_effective_overlap_violation
FROM cloudmold_merchant_source_mapping earlier
JOIN cloudmold_merchant_source_mapping later
  ON later.tenant_id=earlier.tenant_id
 AND later.source_system=earlier.source_system
 AND later.source_type=earlier.source_type
 AND later.source_id=earlier.source_id
 AND later.mapping_id>earlier.mapping_id
 AND later.valid_from<COALESCE(earlier.valid_to,'9999-12-31 23:59:59.999999')
 AND earlier.valid_from<COALESCE(later.valid_to,'9999-12-31 23:59:59.999999')
WHERE earlier.status='ACTIVE' AND later.status='ACTIVE';

SELECT COUNT(*) AS source_mapping_state_violation
FROM cloudmold_merchant_source_mapping
WHERE status NOT IN ('ACTIVE','REVOKED')
   OR version<1
   OR (valid_to IS NOT NULL AND valid_to<=valid_from);
