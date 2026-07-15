-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS identity_source_orphan_violation
FROM cloudmold_identity_source_identity s
LEFT JOIN cloudmold_identity_principal p
  ON p.tenant_id=s.tenant_id AND p.principal_id=s.principal_id
WHERE p.principal_id IS NULL OR s.principal_id=s.source_id;

SELECT COUNT(*) AS identity_active_source_duplicate_violation
FROM (
  SELECT tenant_id,source_system,source_type,source_id,COUNT(*) AS row_count
  FROM cloudmold_identity_source_identity
  WHERE status='ACTIVE'
  GROUP BY tenant_id,source_system,source_type,source_id
  HAVING COUNT(*)<>1
) x;

SELECT COUNT(*) AS merchant_entity_identity_collision_violation
FROM cloudmold_merchant_account m
JOIN cloudmold_merchant_shop s
  ON s.tenant_id=m.tenant_id AND (s.shop_id=m.merchant_id OR s.shop_id=m.legal_entity_id)
UNION ALL
SELECT COUNT(*)
FROM cloudmold_merchant_account m
WHERE m.merchant_id=m.legal_entity_id;

SELECT COUNT(*) AS merchant_shop_orphan_violation
FROM cloudmold_merchant_shop s
LEFT JOIN cloudmold_merchant_account m
  ON m.tenant_id=s.tenant_id AND m.merchant_id=s.merchant_id
WHERE m.merchant_id IS NULL;

SELECT COUNT(*) AS merchant_owner_assignment_violation
FROM cloudmold_merchant_onboarding_application a
LEFT JOIN cloudmold_merchant_operator_assignment x
  ON x.tenant_id=a.tenant_id AND x.assignment_id=a.owner_assignment_id
LEFT JOIN cloudmold_identity_principal p
  ON p.tenant_id=x.tenant_id AND p.principal_id=x.principal_id
WHERE a.status='APPROVED'
  AND (a.merchant_id IS NULL OR a.shop_id IS NULL OR x.assignment_id IS NULL
    OR x.merchant_id<>a.merchant_id OR x.shop_id<>a.shop_id
    OR x.principal_id<>a.owner_principal_id OR x.role_code<>'OWNER'
    OR x.status<>'ACTIVE' OR p.status<>'ACTIVE');

SELECT COUNT(*) AS merchant_history_version_violation
FROM cloudmold_merchant_status_history h
WHERE h.aggregate_version <> (
  SELECT COUNT(*) FROM cloudmold_merchant_status_history x
  WHERE x.tenant_id=h.tenant_id AND x.aggregate_type=h.aggregate_type
    AND x.aggregate_id=h.aggregate_id AND x.aggregate_version<=h.aggregate_version
);

SELECT COUNT(*) AS merchant_activation_order_violation
FROM cloudmold_merchant_shop s
JOIN cloudmold_merchant_account m
  ON m.tenant_id=s.tenant_id AND m.merchant_id=s.merchant_id
WHERE s.status='ACTIVE' AND m.status<>'ACTIVE';
