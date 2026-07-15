-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS warehouse_location_hierarchy_violation
FROM cloudmold_warehouse_location l
LEFT JOIN cloudmold_warehouse_zone z
  ON z.tenant_id=l.tenant_id AND z.zone_id=l.zone_id
LEFT JOIN cloudmold_warehouse w
  ON w.tenant_id=l.tenant_id AND w.warehouse_id=l.warehouse_id
WHERE z.zone_id IS NULL OR w.warehouse_id IS NULL OR z.warehouse_id<>l.warehouse_id;

SELECT COUNT(*) AS warehouse_source_mapping_collision_violation
FROM cloudmold_warehouse_source_mapping m
WHERE m.canonical_id=m.source_id;

SELECT COUNT(*) AS warehouse_source_mapping_shape_violation
FROM cloudmold_warehouse_source_mapping m
WHERE (m.canonical_type='WAREHOUSE' AND (m.canonical_id<>m.warehouse_id OR m.zone_id IS NOT NULL OR m.location_id IS NOT NULL))
   OR (m.canonical_type='ZONE' AND (m.zone_id IS NULL OR m.canonical_id<>m.zone_id OR m.location_id IS NOT NULL))
   OR (m.canonical_type='LOCATION' AND (m.zone_id IS NULL OR m.location_id IS NULL OR m.canonical_id<>m.location_id));

SELECT COUNT(*) AS warehouse_effective_mapping_duplicate_violation
FROM (
  SELECT tenant_id,source_system,source_type,source_id,COUNT(*) AS row_count
  FROM cloudmold_warehouse_source_mapping
  WHERE status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6) AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6))
  GROUP BY tenant_id,source_system,source_type,source_id
  HAVING COUNT(*)<>1
) x;

SELECT COUNT(*) AS warehouse_operator_hierarchy_violation
FROM cloudmold_warehouse_operator_assignment a
LEFT JOIN cloudmold_warehouse_zone z
  ON z.tenant_id=a.tenant_id AND z.zone_id=a.zone_id
LEFT JOIN cloudmold_warehouse_location l
  ON l.tenant_id=a.tenant_id AND l.location_id=a.location_id
LEFT JOIN cloudmold_identity_principal p
  ON p.tenant_id=a.tenant_id AND p.principal_id=a.principal_id
WHERE (a.zone_id IS NOT NULL AND (z.zone_id IS NULL OR z.warehouse_id<>a.warehouse_id))
   OR (a.location_id IS NOT NULL AND (l.location_id IS NULL OR l.warehouse_id<>a.warehouse_id OR l.zone_id<>a.zone_id))
   OR (a.status='ACTIVE' AND (p.principal_id IS NULL OR p.status<>'ACTIVE'));

SELECT COUNT(*) AS warehouse_activation_hierarchy_violation
FROM cloudmold_warehouse_location l
JOIN cloudmold_warehouse_zone z
  ON z.tenant_id=l.tenant_id AND z.zone_id=l.zone_id
JOIN cloudmold_warehouse w
  ON w.tenant_id=l.tenant_id AND w.warehouse_id=l.warehouse_id
WHERE l.status='ACTIVE' AND (z.status<>'ACTIVE' OR w.status<>'ACTIVE');
