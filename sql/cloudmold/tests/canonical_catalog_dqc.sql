SELECT 'catalog_orphan_relations' AS check_name, COUNT(*) AS violations
FROM (
  SELECT p.tenant_id, p.spu_id AS entity_id FROM cloudmold_catalog_spu p
  LEFT JOIN cloudmold_catalog_style s ON s.tenant_id = p.tenant_id AND s.style_id = p.style_id
  WHERE s.style_id IS NULL
  UNION ALL
  SELECT k.tenant_id, k.sku_id FROM cloudmold_catalog_sku k
  LEFT JOIN cloudmold_catalog_spu p ON p.tenant_id = k.tenant_id AND p.spu_id = k.spu_id
  LEFT JOIN cloudmold_catalog_color c ON c.tenant_id = k.tenant_id AND c.color_id = k.color_id
  LEFT JOIN cloudmold_catalog_size z ON z.tenant_id = k.tenant_id AND z.size_id = k.size_id
  WHERE p.spu_id IS NULL OR c.color_id IS NULL OR z.size_id IS NULL
  UNION ALL
  SELECT z.tenant_id, z.size_id FROM cloudmold_catalog_size z
  LEFT JOIN cloudmold_catalog_size_group g
    ON g.tenant_id = z.tenant_id AND g.size_group_id = z.size_group_id
  WHERE g.size_group_id IS NULL
  UNION ALL
  SELECT b.tenant_id, b.barcode_id FROM cloudmold_catalog_barcode b
  LEFT JOIN cloudmold_catalog_sku k ON k.tenant_id = b.tenant_id AND k.sku_id = b.sku_id
  WHERE k.sku_id IS NULL
) orphan
UNION ALL
SELECT 'catalog_codes_are_canonical', COUNT(*)
FROM (
  SELECT tenant_id, style_code AS code FROM cloudmold_catalog_style
  UNION ALL SELECT tenant_id, spu_code FROM cloudmold_catalog_spu
  UNION ALL SELECT tenant_id, sku_code FROM cloudmold_catalog_sku
  UNION ALL SELECT tenant_id, color_code FROM cloudmold_catalog_color
  UNION ALL SELECT tenant_id, size_group_code FROM cloudmold_catalog_size_group
  UNION ALL SELECT tenant_id, size_code FROM cloudmold_catalog_size
) codes
WHERE code <> UPPER(TRIM(code))
UNION ALL
SELECT 'catalog_variant_hash_matches_key', COUNT(*)
FROM cloudmold_catalog_sku
WHERE variant_key_hash <> SHA2(variant_key, 256)
UNION ALL
SELECT 'catalog_variant_hash_collision', COUNT(*)
FROM (
  SELECT tenant_id, spu_id, variant_key_hash
  FROM cloudmold_catalog_sku
  GROUP BY tenant_id, spu_id, variant_key_hash
  HAVING COUNT(DISTINCT variant_key) > 1
) collision
UNION ALL
SELECT 'catalog_active_reference_status', COUNT(*)
FROM cloudmold_catalog_sku k
JOIN cloudmold_catalog_spu p ON p.tenant_id = k.tenant_id AND p.spu_id = k.spu_id
JOIN cloudmold_catalog_color c ON c.tenant_id = k.tenant_id AND c.color_id = k.color_id
JOIN cloudmold_catalog_size z ON z.tenant_id = k.tenant_id AND z.size_id = k.size_id
JOIN cloudmold_catalog_size_group g ON g.tenant_id = z.tenant_id AND g.size_group_id = z.size_group_id
WHERE k.status = 10 AND (p.status NOT IN (20, 30) OR c.status <> 10 OR z.status <> 10 OR g.status <> 10)
UNION ALL
SELECT 'catalog_active_sku_primary_barcode', COUNT(*)
FROM (
  SELECT k.tenant_id, k.sku_id
  FROM cloudmold_catalog_sku k
  LEFT JOIN cloudmold_catalog_barcode b
    ON b.tenant_id = k.tenant_id AND b.sku_id = k.sku_id
   AND b.status = 10 AND b.is_primary = b'1'
   AND b.valid_from <= UTC_TIMESTAMP(6) AND (b.valid_to IS NULL OR b.valid_to > UTC_TIMESTAMP(6))
  WHERE k.status = 10
  GROUP BY k.tenant_id, k.sku_id
  HAVING COUNT(b.barcode_id) <> 1
) invalid
UNION ALL
SELECT 'catalog_mapping_canonical_orphan', COUNT(*)
FROM cloudmold_catalog_source_mapping m
WHERE (m.canonical_type = 'STYLE' AND NOT EXISTS (
         SELECT 1 FROM cloudmold_catalog_style x WHERE x.tenant_id = m.tenant_id AND x.style_id = m.canonical_id))
   OR (m.canonical_type = 'SPU' AND NOT EXISTS (
         SELECT 1 FROM cloudmold_catalog_spu x WHERE x.tenant_id = m.tenant_id AND x.spu_id = m.canonical_id))
   OR (m.canonical_type = 'SKU' AND NOT EXISTS (
         SELECT 1 FROM cloudmold_catalog_sku x WHERE x.tenant_id = m.tenant_id AND x.sku_id = m.canonical_id))
   OR (m.canonical_type = 'COLOR' AND NOT EXISTS (
         SELECT 1 FROM cloudmold_catalog_color x WHERE x.tenant_id = m.tenant_id AND x.color_id = m.canonical_id))
   OR (m.canonical_type = 'SIZE_GROUP' AND NOT EXISTS (
         SELECT 1 FROM cloudmold_catalog_size_group x WHERE x.tenant_id = m.tenant_id AND x.size_group_id = m.canonical_id))
   OR (m.canonical_type = 'SIZE' AND NOT EXISTS (
         SELECT 1 FROM cloudmold_catalog_size x WHERE x.tenant_id = m.tenant_id AND x.size_id = m.canonical_id))
UNION ALL
SELECT 'catalog_synced_projection_stale', COUNT(*)
FROM cloudmold_catalog_source_mapping m
JOIN cloudmold_catalog_sku k
  ON m.canonical_type = 'SKU' AND k.tenant_id = m.tenant_id AND k.sku_id = m.canonical_id
WHERE m.sync_state = 10 AND m.projection_version < k.version
UNION ALL
SELECT 'catalog_legacy_projection_orphan', COUNT(*)
FROM cloudmold_catalog_legacy_projection p
LEFT JOIN cloudmold_catalog_sku k
  ON p.canonical_type = 'SKU' AND k.tenant_id = p.tenant_id AND k.sku_id = p.canonical_id
WHERE p.canonical_type <> 'SKU' OR k.sku_id IS NULL
UNION ALL
SELECT 'catalog_legacy_projection_stale', COUNT(*)
FROM cloudmold_catalog_legacy_projection p
JOIN cloudmold_catalog_sku k
  ON p.canonical_type = 'SKU' AND k.tenant_id = p.tenant_id AND k.sku_id = p.canonical_id
WHERE p.aggregate_version < k.version
UNION ALL
SELECT 'catalog_legacy_projection_ownership_leak', COUNT(*)
FROM cloudmold_catalog_legacy_projection
WHERE JSON_CONTAINS_PATH(payload, 'one', '$.stock', '$.price', '$.purchase_price', '$.sale_price',
                         '$.available_quantity', '$.listing_status') = 1
UNION ALL
SELECT 'catalog_legacy_projection_target_invalid', COUNT(*)
FROM cloudmold_catalog_legacy_projection
WHERE (target_system = 'MALL' AND target_entity <> 'SKU')
   OR (target_system = 'ERP' AND target_entity <> 'PRODUCT')
   OR (target_system = 'WMS' AND target_entity <> 'ITEM_SKU')
   OR target_system NOT IN ('MALL', 'ERP', 'WMS');
