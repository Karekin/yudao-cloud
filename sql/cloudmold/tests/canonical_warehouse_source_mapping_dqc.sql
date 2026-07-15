-- Every query must return zero. This DQC validates only source systems whose OLTP tables are installed.

SELECT COUNT(*) AS canonical_source_id_reuse_violation
FROM cloudmold_warehouse_source_mapping
WHERE canonical_id=source_id;

SELECT COUNT(*) AS source_identity_normalization_violation
FROM cloudmold_warehouse_source_mapping
WHERE BINARY source_system<>BINARY UPPER(TRIM(source_system))
   OR BINARY source_type<>BINARY UPPER(TRIM(source_type))
   OR BINARY source_id<>BINARY TRIM(source_id)
   OR CHAR_LENGTH(TRIM(source_system))=0
   OR CHAR_LENGTH(TRIM(source_type))=0
   OR CHAR_LENGTH(TRIM(source_id))=0
   OR CHAR_LENGTH(TRIM(verification_ref))=0;

SELECT COUNT(*) AS source_mapping_hierarchy_violation
FROM cloudmold_warehouse_source_mapping m
LEFT JOIN cloudmold_warehouse w
  ON w.tenant_id=m.tenant_id AND w.warehouse_id=m.warehouse_id
LEFT JOIN cloudmold_warehouse_zone z
  ON z.tenant_id=m.tenant_id AND z.warehouse_id=m.warehouse_id AND z.zone_id=m.zone_id
LEFT JOIN cloudmold_warehouse_location l
  ON l.tenant_id=m.tenant_id AND l.warehouse_id=m.warehouse_id
 AND l.zone_id=m.zone_id AND l.location_id=m.location_id
WHERE w.warehouse_id IS NULL
   OR (m.canonical_type IN ('ZONE','LOCATION') AND z.zone_id IS NULL)
   OR (m.canonical_type='LOCATION' AND l.location_id IS NULL);

SELECT COUNT(*) AS active_source_mapping_duplicate_violation
FROM (
  SELECT tenant_id,source_system,source_type,source_id,COUNT(*) AS mapping_count
  FROM cloudmold_warehouse_source_mapping
  WHERE status='ACTIVE' AND valid_from<=UTC_TIMESTAMP(6)
    AND (valid_to IS NULL OR valid_to>UTC_TIMESTAMP(6))
  GROUP BY tenant_id,source_system,source_type,source_id
  HAVING COUNT(*)>1
) duplicates;

SELECT COUNT(*) AS orphan_erp_warehouse_mapping_violation
FROM cloudmold_warehouse_source_mapping m
LEFT JOIN erp_warehouse source
  ON source.tenant_id=m.tenant_id AND CAST(source.id AS CHAR)=m.source_id AND source.deleted=b'0'
WHERE m.source_system='ERP' AND m.source_type='WAREHOUSE' AND m.status='ACTIVE'
  AND source.id IS NULL;

SELECT COUNT(*) AS orphan_wms_warehouse_mapping_violation
FROM cloudmold_warehouse_source_mapping m
LEFT JOIN wms_warehouse source
  ON source.tenant_id=m.tenant_id AND CAST(source.id AS CHAR)=m.source_id AND source.deleted=b'0'
WHERE m.source_system='WMS' AND m.source_type='WAREHOUSE' AND m.status='ACTIVE'
  AND source.id IS NULL;
