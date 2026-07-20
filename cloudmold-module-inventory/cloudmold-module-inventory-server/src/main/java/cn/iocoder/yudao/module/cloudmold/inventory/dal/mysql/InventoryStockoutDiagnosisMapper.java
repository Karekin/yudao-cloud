package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockoutDiagnosisResult.SizeStockFact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryStockoutDiagnosisMapper {

    @Select("""
            SELECT k.sku_id AS canonical_sku_id,
                   k.sku_code,
                   c.color_code,
                   c.display_name AS color_name,
                   z.size_code,
                   z.size_name,
                   COALESCE(SUM(b.on_hand_quantity), 0) AS on_hand_quantity,
                   COALESCE(SUM(b.reserved_quantity), 0) AS reserved_quantity,
                   COALESCE(SUM(b.in_transit_quantity), 0) AS in_transit_quantity,
                   COALESCE(SUM(CASE
                     WHEN b.stock_status='SELLABLE' AND b.quality_status='QUALIFIED'
                       THEN GREATEST(b.on_hand_quantity-b.reserved_quantity, 0)
                     ELSE 0 END), 0) AS allocatable_quantity,
                   COALESCE(MAX(b.version), 0) AS max_inventory_version
            FROM cloudmold_catalog_sku k
            JOIN cloudmold_catalog_spu p
              ON p.tenant_id=k.tenant_id AND p.spu_id=k.spu_id
            JOIN cloudmold_catalog_style s
              ON s.tenant_id=p.tenant_id AND s.style_id=p.style_id
            JOIN cloudmold_catalog_color c
              ON c.tenant_id=k.tenant_id AND c.color_id=k.color_id
            JOIN cloudmold_catalog_size z
              ON z.tenant_id=k.tenant_id AND z.size_id=k.size_id
            JOIN cloudmold_catalog_size_group g
              ON g.tenant_id=z.tenant_id AND g.size_group_id=z.size_group_id
            LEFT JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id=k.tenant_id
             AND b.canonical_sku_id=k.sku_id COLLATE utf8mb4_unicode_ci
             AND (#{warehouseId} IS NULL OR b.warehouse_id=#{warehouseId})
            WHERE k.tenant_id=#{tenantId} AND k.spu_id=#{canonicalSpuId}
              AND k.status=10 AND p.status=30 AND s.status=10
              AND c.status=10 AND z.status=10 AND g.status=10
            GROUP BY k.sku_id,k.sku_code,c.color_code,c.display_name,z.size_code,z.size_name,z.sort_order
            ORDER BY c.color_code,z.sort_order,z.size_code,k.sku_code
            """)
    List<SizeStockFact> selectSizeStockFacts(@Param("tenantId") Long tenantId,
                                             @Param("canonicalSpuId") String canonicalSpuId,
                                             @Param("warehouseId") String warehouseId);

    @Select("""
            SELECT CONCAT(p.spu_code, '|', s.style_code)
            FROM cloudmold_catalog_spu p
            JOIN cloudmold_catalog_style s
              ON s.tenant_id=p.tenant_id AND s.style_id=p.style_id
            WHERE p.tenant_id=#{tenantId} AND p.spu_id=#{canonicalSpuId}
              AND p.status=30 AND s.status=10
            """)
    String selectActiveSpuIdentity(@Param("tenantId") Long tenantId,
                                   @Param("canonicalSpuId") String canonicalSpuId);

}
