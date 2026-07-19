package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryV3LedgerPageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryV3LedgerPageMapper {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM cloudmold_inventory_ledger_entry_v3 e
            JOIN cloudmold_inventory_ledger_transaction_v3 tx
              ON tx.tenant_id = e.tenant_id
             AND tx.ledger_transaction_id = e.ledger_transaction_id
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id = e.tenant_id
             AND b.balance_id = e.balance_id
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
            WHERE e.tenant_id = #{tenantId}
            <if test="movementGroupId != null">AND e.movement_group_id LIKE CONCAT('%', #{movementGroupId}, '%')</if>
            <if test="commandType != null">AND tx.command_type = #{commandType}</if>
            <if test="businessType != null">AND tx.business_type = #{businessType}</if>
            <if test="businessId != null">AND tx.business_id LIKE CONCAT('%', #{businessId}, '%')</if>
            <if test="businessItemId != null">AND tx.business_item_id LIKE CONCAT('%', #{businessItemId}, '%')</if>
            <if test="businessNo != null">AND tx.business_no LIKE CONCAT('%', #{businessNo}, '%')</if>
            <if test="skuCode != null">AND sku.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="warehouseCode != null">AND wh.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="locationCode != null">AND loc.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="lotCode != null">AND lot.lot_code LIKE CONCAT('%', #{lotCode}, '%')</if>
            <if test="entryRole != null">AND e.entry_role = #{entryRole}</if>
            <if test="occurredTimeFrom != null">AND tx.occurred_at &gt;= #{occurredTimeFrom}</if>
            <if test="occurredTimeTo != null">AND tx.occurred_at &lt;= #{occurredTimeTo}</if>
            </script>
            """)
    long countLedgerPage(@Param("tenantId") Long tenantId,
                         @Param("movementGroupId") String movementGroupId,
                         @Param("commandType") String commandType,
                         @Param("businessType") String businessType,
                         @Param("businessId") String businessId,
                         @Param("businessItemId") String businessItemId,
                         @Param("businessNo") String businessNo,
                         @Param("skuCode") String skuCode,
                         @Param("warehouseCode") String warehouseCode,
                         @Param("locationCode") String locationCode,
                         @Param("lotCode") String lotCode,
                         @Param("entryRole") String entryRole,
                         @Param("occurredTimeFrom") LocalDateTime occurredTimeFrom,
                         @Param("occurredTimeTo") LocalDateTime occurredTimeTo);

    @Select("""
            <script>
            SELECT e.ledger_entry_id,
                   e.ledger_transaction_id,
                   e.movement_group_id,
                   e.entry_role,
                   tx.command_type,
                   tx.business_type,
                   tx.business_id,
                   tx.business_item_id,
                   tx.business_no,
                   e.balance_id,
                   e.counterparty_balance_id,
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
                   e.base_uom_code,
                   e.before_on_hand_quantity,
                   e.delta_on_hand_quantity,
                   e.after_on_hand_quantity,
                   e.before_reserved_quantity,
                   e.delta_reserved_quantity,
                   e.after_reserved_quantity,
                   e.before_in_transit_quantity,
                   e.delta_in_transit_quantity,
                   e.after_in_transit_quantity,
                   e.aggregate_version,
                   tx.occurred_at,
                   e.created_at
            FROM cloudmold_inventory_ledger_entry_v3 e
            JOIN cloudmold_inventory_ledger_transaction_v3 tx
              ON tx.tenant_id = e.tenant_id
             AND tx.ledger_transaction_id = e.ledger_transaction_id
            JOIN cloudmold_inventory_balance_v3 b
              ON b.tenant_id = e.tenant_id
             AND b.balance_id = e.balance_id
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
            WHERE e.tenant_id = #{tenantId}
            <if test="movementGroupId != null">AND e.movement_group_id LIKE CONCAT('%', #{movementGroupId}, '%')</if>
            <if test="commandType != null">AND tx.command_type = #{commandType}</if>
            <if test="businessType != null">AND tx.business_type = #{businessType}</if>
            <if test="businessId != null">AND tx.business_id LIKE CONCAT('%', #{businessId}, '%')</if>
            <if test="businessItemId != null">AND tx.business_item_id LIKE CONCAT('%', #{businessItemId}, '%')</if>
            <if test="businessNo != null">AND tx.business_no LIKE CONCAT('%', #{businessNo}, '%')</if>
            <if test="skuCode != null">AND sku.sku_code LIKE CONCAT('%', #{skuCode}, '%')</if>
            <if test="warehouseCode != null">AND wh.warehouse_code LIKE CONCAT('%', #{warehouseCode}, '%')</if>
            <if test="locationCode != null">AND loc.location_code LIKE CONCAT('%', #{locationCode}, '%')</if>
            <if test="lotCode != null">AND lot.lot_code LIKE CONCAT('%', #{lotCode}, '%')</if>
            <if test="entryRole != null">AND e.entry_role = #{entryRole}</if>
            <if test="occurredTimeFrom != null">AND tx.occurred_at &gt;= #{occurredTimeFrom}</if>
            <if test="occurredTimeTo != null">AND tx.occurred_at &lt;= #{occurredTimeTo}</if>
            ORDER BY tx.occurred_at DESC, e.ledger_entry_id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<InventoryV3LedgerPageItem> selectLedgerPage(@Param("tenantId") Long tenantId,
                                                     @Param("movementGroupId") String movementGroupId,
                                                     @Param("commandType") String commandType,
                                                     @Param("businessType") String businessType,
                                                     @Param("businessId") String businessId,
                                                     @Param("businessItemId") String businessItemId,
                                                     @Param("businessNo") String businessNo,
                                                     @Param("skuCode") String skuCode,
                                                     @Param("warehouseCode") String warehouseCode,
                                                     @Param("locationCode") String locationCode,
                                                     @Param("lotCode") String lotCode,
                                                     @Param("entryRole") String entryRole,
                                                     @Param("occurredTimeFrom") LocalDateTime occurredTimeFrom,
                                                     @Param("occurredTimeTo") LocalDateTime occurredTimeTo,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);
}
