package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3BalancePageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryV3BalancePageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_inventory_balance_v3 b
            JOIN cloudmold_catalog_sku sku
              ON sku.tenant_id = b.tenant_id
             AND sku.sku_id = b.canonical_sku_id
            JOIN cloudmold_catalog_spu spu
              ON spu.tenant_id = sku.tenant_id
             AND spu.spu_id = sku.spu_id
            JOIN cloudmold_warehouse wh
              ON wh.tenant_id = b.tenant_id
             AND wh.warehouse_id = b.warehouse_id
            JOIN cloudmold_warehouse_location loc
              ON loc.tenant_id = b.tenant_id
             AND loc.location_id = b.location_id
            LEFT JOIN cloudmold_inventory_lot lot
              ON lot.tenant_id = b.tenant_id
             AND lot.lot_id = b.lot_id
            WHERE b.tenant_id = #{tenantId}
            <if test="skuCode != null">AND sku.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="warehouseCode != null">AND wh.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="locationCode != null">AND loc.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="lotCode != null">AND lot.lot_code LIKE CONCAT('%', #{lotCode}, '%')</if>
            <if test="ownerType != null">AND b.owner_type = #{ownerType}</if>
            <if test="ownerId != null">AND b.owner_id = #{ownerId}</if>
            <if test="stockStatus != null">AND b.stock_status = #{stockStatus}</if>
            <if test="qualityStatus != null">AND b.quality_status = #{qualityStatus}</if>
            <if test="onlyNonZero != null and onlyNonZero">
              AND (b.on_hand_quantity &lt;&gt; 0 OR b.reserved_quantity &lt;&gt; 0 OR b.in_transit_quantity &lt;&gt; 0)
            </if>
            </script>
            """)
    long countBalancePage(@Param("tenantId") Long tenantId,
                          @Param("skuCode") String skuCode,
                          @Param("warehouseCode") String warehouseCode,
                          @Param("locationCode") String locationCode,
                          @Param("lotCode") String lotCode,
                          @Param("ownerType") String ownerType,
                          @Param("ownerId") String ownerId,
                          @Param("stockStatus") String stockStatus,
                          @Param("qualityStatus") String qualityStatus,
                          @Param("onlyNonZero") Boolean onlyNonZero);

    @Select("""
            <script>
            SELECT b.balance_id,
                   b.canonical_sku_id,
                   sku.sku_code,
                   spu.spu_code,
                   b.warehouse_id,
                   wh.warehouse_code,
                   wh.name AS warehouse_name,
                   b.location_id,
                   loc.location_code,
                   loc.name AS location_name,
                   b.lot_id,
                   lot.lot_code,
                   b.owner_type,
                   b.owner_id,
                   b.stock_status,
                   b.quality_status,
                   b.base_uom_code,
                   b.on_hand_quantity,
                   b.reserved_quantity,
                   b.in_transit_quantity,
                   CASE
                     WHEN b.stock_status = 'SELLABLE' AND b.quality_status = 'QUALIFIED'
                       THEN b.on_hand_quantity - b.reserved_quantity
                     ELSE 0
                   END AS available_quantity,
                   CASE
                     WHEN b.stock_status = 'SELLABLE'
                      AND b.quality_status = 'QUALIFIED'
                      AND (b.lot_id IS NULL OR (lot.status = 'ACTIVE'
                           AND (lot.expires_on IS NULL OR lot.expires_on &gt;= UTC_DATE())))
                       THEN b.on_hand_quantity - b.reserved_quantity
                     ELSE 0
                   END AS allocatable_quantity,
                   CASE
                     WHEN b.stock_status &lt;&gt; 'SELLABLE' THEN 'STOCK_NOT_SELLABLE'
                     WHEN b.quality_status &lt;&gt; 'QUALIFIED' THEN 'QUALITY_NOT_QUALIFIED'
                     WHEN lot.status = 'RECALLED' THEN 'LOT_RECALLED'
                     WHEN lot.status = 'CLOSED' THEN 'LOT_CLOSED'
                     WHEN lot.expires_on IS NOT NULL AND lot.expires_on &lt; UTC_DATE() THEN 'LOT_EXPIRED'
                     ELSE 'ALLOCATABLE'
                   END AS allocation_eligibility,
                   b.version AS aggregate_version,
                   b.updated_at
            FROM cloudmold_inventory_balance_v3 b
            JOIN cloudmold_catalog_sku sku
              ON sku.tenant_id = b.tenant_id
             AND sku.sku_id = b.canonical_sku_id
            JOIN cloudmold_catalog_spu spu
              ON spu.tenant_id = sku.tenant_id
             AND spu.spu_id = sku.spu_id
            JOIN cloudmold_warehouse wh
              ON wh.tenant_id = b.tenant_id
             AND wh.warehouse_id = b.warehouse_id
            JOIN cloudmold_warehouse_location loc
              ON loc.tenant_id = b.tenant_id
             AND loc.location_id = b.location_id
            LEFT JOIN cloudmold_inventory_lot lot
              ON lot.tenant_id = b.tenant_id
             AND lot.lot_id = b.lot_id
            WHERE b.tenant_id = #{tenantId}
            <if test="skuCode != null">AND sku.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="warehouseCode != null">AND wh.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="locationCode != null">AND loc.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="lotCode != null">AND lot.lot_code LIKE CONCAT('%', #{lotCode}, '%')</if>
            <if test="ownerType != null">AND b.owner_type = #{ownerType}</if>
            <if test="ownerId != null">AND b.owner_id = #{ownerId}</if>
            <if test="stockStatus != null">AND b.stock_status = #{stockStatus}</if>
            <if test="qualityStatus != null">AND b.quality_status = #{qualityStatus}</if>
            <if test="onlyNonZero != null and onlyNonZero">
              AND (b.on_hand_quantity &lt;&gt; 0 OR b.reserved_quantity &lt;&gt; 0 OR b.in_transit_quantity &lt;&gt; 0)
            </if>
            ORDER BY b.updated_at DESC, b.balance_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<InventoryV3BalancePageItem> selectBalancePage(@Param("tenantId") Long tenantId,
                                                       @Param("skuCode") String skuCode,
                                                       @Param("warehouseCode") String warehouseCode,
                                                       @Param("locationCode") String locationCode,
                                                       @Param("lotCode") String lotCode,
                                                       @Param("ownerType") String ownerType,
                                                       @Param("ownerId") String ownerId,
                                                       @Param("stockStatus") String stockStatus,
                                                       @Param("qualityStatus") String qualityStatus,
                                                       @Param("onlyNonZero") Boolean onlyNonZero,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);
}
