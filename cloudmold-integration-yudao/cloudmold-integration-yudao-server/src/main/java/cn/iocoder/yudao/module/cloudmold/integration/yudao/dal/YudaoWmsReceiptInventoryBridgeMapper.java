package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface YudaoWmsReceiptInventoryBridgeMapper {

    @Select("""
            SELECT bridge_id, tenant_id, receipt_order_id, receipt_order_no, receipt_order_line_id,
                   wms_merchant_id, wms_warehouse_id, wms_sku_id, canonical_owner_id, canonical_sku_id,
                   warehouse_mapping_id, canonical_warehouse_id, canonical_zone_id, canonical_location_id,
                   lot_mapping_status, canonical_lot_id, receipt_quantity, base_uom_code,
                   inventory_idempotency_key, inventory_source_event_id, inventory_business_id,
                   inventory_business_item_id, inventory_operation_id, inventory_ledger_transaction_id,
                   inventory_balance_id, inventory_aggregate_version, created_at, updated_at
            FROM cloudmold_wms_receipt_inventory_bridge
            WHERE tenant_id=#{tenantId} AND receipt_order_id=#{receiptOrderId}
            ORDER BY receipt_order_line_id
            """)
    List<YudaoWmsReceiptInventoryBridgeRow> selectByReceiptOrderId(@Param("tenantId") Long tenantId,
                                                                   @Param("receiptOrderId") Long receiptOrderId);

    @Insert("""
            INSERT INTO cloudmold_wms_receipt_inventory_bridge
              (tenant_id, receipt_order_id, receipt_order_no, receipt_order_line_id,
               wms_merchant_id, wms_warehouse_id, wms_sku_id, canonical_owner_id, canonical_sku_id,
               warehouse_mapping_id, canonical_warehouse_id, canonical_zone_id, canonical_location_id,
               lot_mapping_status, canonical_lot_id, receipt_quantity, base_uom_code,
               inventory_idempotency_key, inventory_source_event_id, inventory_business_id,
               inventory_business_item_id, inventory_operation_id, inventory_ledger_transaction_id,
               inventory_balance_id, inventory_aggregate_version, created_at, updated_at)
            VALUES
              (#{tenantId}, #{receiptOrderId}, #{receiptOrderNo}, #{receiptOrderLineId},
               #{wmsMerchantId}, #{wmsWarehouseId}, #{wmsSkuId}, #{canonicalOwnerId}, #{canonicalSkuId},
               #{warehouseMappingId}, #{canonicalWarehouseId}, #{canonicalZoneId}, #{canonicalLocationId},
               #{lotMappingStatus}, #{canonicalLotId}, #{receiptQuantity}, #{baseUomCode},
               #{inventoryIdempotencyKey}, #{inventorySourceEventId}, #{inventoryBusinessId},
               #{inventoryBusinessItemId}, #{inventoryOperationId}, #{inventoryLedgerTransactionId},
               #{inventoryBalanceId}, #{inventoryAggregateVersion}, #{createdAt}, #{updatedAt})
            """)
    int insert(YudaoWmsReceiptInventoryBridgeRow row);
}
