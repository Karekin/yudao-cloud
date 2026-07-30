-- Expected result: no rows.
SELECT 'missing_table' AS defect, required.table_name AS object_name
FROM (
  SELECT 'mes_md_workshop' AS table_name UNION ALL
  SELECT 'mes_md_workstation' UNION ALL
  SELECT 'mes_pro_process' UNION ALL
  SELECT 'mes_pro_route' UNION ALL
  SELECT 'mes_pro_route_process' UNION ALL
  SELECT 'mes_pro_route_product' UNION ALL
  SELECT 'mes_pro_route_product_bom' UNION ALL
  SELECT 'mes_pro_feedback' UNION ALL
  SELECT 'mes_wm_product_produce' UNION ALL
  SELECT 'mes_wm_product_produce_line' UNION ALL
  SELECT 'mes_wm_product_produce_detail' UNION ALL
  SELECT 'mes_wm_warehouse' UNION ALL
  SELECT 'mes_wm_warehouse_location' UNION ALL
  SELECT 'mes_wm_warehouse_area' UNION ALL
  SELECT 'mes_wm_material_stock' UNION ALL
  SELECT 'mes_wm_transaction' UNION ALL
  SELECT 'mes_md_auto_code_rule' UNION ALL
  SELECT 'mes_md_auto_code_part' UNION ALL
  SELECT 'mes_md_auto_code_record'
) required
LEFT JOIN information_schema.tables actual
  ON actual.table_schema=DATABASE() AND actual.table_name=required.table_name
WHERE actual.table_name IS NULL
UNION ALL
SELECT 'missing_wip_prerequisite', t.name
FROM system_tenant t
WHERE t.status=0 AND t.deleted=b'0'
  AND (
    NOT EXISTS (
      SELECT 1 FROM mes_wm_warehouse w
      WHERE w.tenant_id=t.id AND w.code='WIP_VIRTUAL_WAREHOUSE'
        AND w.frozen=b'0' AND w.deleted=b'0'
    )
    OR NOT EXISTS (
      SELECT 1 FROM mes_wm_warehouse_location l
      WHERE l.tenant_id=t.id AND l.code='WIP_VIRTUAL_LOCATION'
        AND l.frozen=b'0' AND l.deleted=b'0'
    )
    OR NOT EXISTS (
      SELECT 1 FROM mes_wm_warehouse_area a
      WHERE a.tenant_id=t.id AND a.code='WIP_VIRTUAL_AREA'
        AND a.status=0 AND a.frozen=b'0' AND a.deleted=b'0'
    )
  )
UNION ALL
SELECT 'invalid_task_code_rule', t.name
FROM system_tenant t
WHERE t.status=0 AND t.deleted=b'0'
  AND NOT EXISTS (
    SELECT 1
    FROM mes_md_auto_code_rule r
    JOIN mes_md_auto_code_part prefix
      ON prefix.tenant_id=r.tenant_id AND prefix.rule_id=r.id
     AND prefix.sort=1 AND prefix.type=3 AND prefix.fix_character='PT'
     AND prefix.deleted=b'0'
    JOIN mes_md_auto_code_part serial
      ON serial.tenant_id=r.tenant_id AND serial.rule_id=r.id
     AND serial.sort=2 AND serial.type=4 AND serial.length=8
     AND serial.deleted=b'0'
    WHERE r.tenant_id=t.id AND r.code='PRO_TASK_CODE'
      AND r.status=0 AND r.deleted=b'0'
  );
