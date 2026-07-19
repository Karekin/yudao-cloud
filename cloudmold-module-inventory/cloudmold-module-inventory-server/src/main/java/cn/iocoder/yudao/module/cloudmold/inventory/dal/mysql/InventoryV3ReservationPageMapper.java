package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3ReservationPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryV3ReservationPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_inventory_reservation_v3 r
            JOIN cloudmold_inventory_reservation_allocation_v3 a
              ON a.tenant_id = r.tenant_id
             AND a.reservation_id = r.reservation_id
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id = a.tenant_id
             AND b.balance_id = a.balance_id
            JOIN cloudmold_catalog_sku sku
              ON sku.tenant_id = b.tenant_id
             AND sku.sku_id = b.canonical_sku_id
            JOIN cloudmold_warehouse wh
              ON wh.tenant_id = b.tenant_id
             AND wh.warehouse_id = b.warehouse_id
            JOIN cloudmold_warehouse_location loc
              ON loc.tenant_id = b.tenant_id
             AND loc.location_id = b.location_id
            LEFT JOIN cloudmold_inventory_lot lot
              ON lot.tenant_id = b.tenant_id
             AND lot.lot_id = b.lot_id
            WHERE r.tenant_id = #{tenantId}
            <if test="reservationId != null">AND r.reservation_id LIKE CONCAT('%', #{reservationId}, '%')</if>
            <if test="businessType != null">AND r.business_type = #{businessType}</if>
            <if test="businessId != null">AND r.business_id LIKE CONCAT('%', #{businessId}, '%')</if>
            <if test="businessItemId != null">AND r.business_item_id LIKE CONCAT('%', #{businessItemId}, '%')</if>
            <if test="status != null">AND r.status = #{status}</if>
            <if test="skuCode != null">AND sku.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="warehouseCode != null">AND wh.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="locationCode != null">AND loc.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="lotCode != null">AND lot.lot_code LIKE CONCAT('%', #{lotCode}, '%')</if>
            </script>
            """)
    long countReservationPage(@Param("tenantId") Long tenantId,
                              @Param("reservationId") String reservationId,
                              @Param("businessType") String businessType,
                              @Param("businessId") String businessId,
                              @Param("businessItemId") String businessItemId,
                              @Param("status") Integer status,
                              @Param("skuCode") String skuCode,
                              @Param("warehouseCode") String warehouseCode,
                              @Param("locationCode") String locationCode,
                              @Param("lotCode") String lotCode);

    @Select("""
            <script>
            SELECT r.reservation_id,
                   a.allocation_id,
                   r.business_type,
                   r.business_id,
                   r.business_item_id,
                   r.status,
                   r.quantity,
                   a.status AS allocation_status,
                   a.quantity AS allocation_quantity,
                   a.version AS allocation_version,
                   b.canonical_sku_id,
                   sku.sku_code,
                   b.warehouse_id,
                   wh.warehouse_code,
                   b.location_id,
                   loc.location_code,
                   b.lot_id,
                   lot.lot_code,
                   b.owner_type,
                   b.owner_id,
                   b.stock_status,
                   b.quality_status,
                   b.base_uom_code,
                   r.created_operation_id,
                   r.closed_operation_id,
                   r.version,
                   r.created_at,
                   r.updated_at
            FROM cloudmold_inventory_reservation_v3 r
            JOIN cloudmold_inventory_reservation_allocation_v3 a
              ON a.tenant_id = r.tenant_id
             AND a.reservation_id = r.reservation_id
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id = a.tenant_id
             AND b.balance_id = a.balance_id
            JOIN cloudmold_catalog_sku sku
              ON sku.tenant_id = b.tenant_id
             AND sku.sku_id = b.canonical_sku_id
            JOIN cloudmold_warehouse wh
              ON wh.tenant_id = b.tenant_id
             AND wh.warehouse_id = b.warehouse_id
            JOIN cloudmold_warehouse_location loc
              ON loc.tenant_id = b.tenant_id
             AND loc.location_id = b.location_id
            LEFT JOIN cloudmold_inventory_lot lot
              ON lot.tenant_id = b.tenant_id
             AND lot.lot_id = b.lot_id
            WHERE r.tenant_id = #{tenantId}
            <if test="reservationId != null">AND r.reservation_id LIKE CONCAT('%', #{reservationId}, '%')</if>
            <if test="businessType != null">AND r.business_type = #{businessType}</if>
            <if test="businessId != null">AND r.business_id LIKE CONCAT('%', #{businessId}, '%')</if>
            <if test="businessItemId != null">AND r.business_item_id LIKE CONCAT('%', #{businessItemId}, '%')</if>
            <if test="status != null">AND r.status = #{status}</if>
            <if test="skuCode != null">AND sku.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="warehouseCode != null">AND wh.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="locationCode != null">AND loc.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="lotCode != null">AND lot.lot_code LIKE CONCAT('%', #{lotCode}, '%')</if>
            ORDER BY r.updated_at DESC, r.reservation_id DESC, a.allocation_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<InventoryV3ReservationPageItem> selectReservationPage(@Param("tenantId") Long tenantId,
                                                               @Param("reservationId") String reservationId,
                                                               @Param("businessType") String businessType,
                                                               @Param("businessId") String businessId,
                                                               @Param("businessItemId") String businessItemId,
                                                               @Param("status") Integer status,
                                                               @Param("skuCode") String skuCode,
                                                               @Param("warehouseCode") String warehouseCode,
                                                               @Param("locationCode") String locationCode,
                                                               @Param("lotCode") String lotCode,
                                                               @Param("offset") long offset,
                                                               @Param("limit") int limit);
}
