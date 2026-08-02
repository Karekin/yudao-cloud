package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryProcurementReceiptDO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface InventoryProcurementReceiptMapper extends BaseMapperX<InventoryProcurementReceiptDO> {
    @Insert("""
            INSERT INTO cloudmold_inventory_procurement_receipt_v3
              (receipt_line_id,tenant_id,receipt_id,purchase_order_id,purchase_order_item_id,
               purchase_order_schedule_id,supplier_id,owner_type,owner_id,canonical_sku_id,warehouse_id,
               location_id,lot_id,base_uom_code,valuation_policy,valuation_policy_version,valuation_policy_hash,
               unit_cost_amount_minor,currency_code,
               received_quantity,pending_quantity,accepted_quantity,rejected_quantity,quarantined_quantity,
               returned_quantity,version,created_operation_id,last_operation_id,created_at,updated_at)
            VALUES
              (#{receiptLineId},#{tenantId},#{receiptId},#{poId},#{poItemId},#{scheduleId},#{supplierId},
               #{ownerType},#{ownerId},#{skuId},#{warehouseId},#{locationId},#{lotId},#{uomCode},
               #{valuationPolicy},#{valuationPolicyVersion},#{valuationPolicyHash},#{unitCost},#{currencyCode},
               0,0,0,0,0,0,0,#{operationId},#{operationId},#{now},#{now})
            ON DUPLICATE KEY UPDATE receipt_line_id=receipt_line_id
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("receiptId") String receiptId,
                        @Param("receiptLineId") String receiptLineId, @Param("poId") String poId,
                        @Param("poItemId") String poItemId, @Param("scheduleId") String scheduleId,
                        @Param("supplierId") String supplierId, @Param("ownerType") String ownerType,
                        @Param("ownerId") String ownerId, @Param("skuId") String skuId,
                        @Param("warehouseId") String warehouseId, @Param("locationId") String locationId,
                        @Param("lotId") String lotId, @Param("uomCode") String uomCode,
                        @Param("valuationPolicy") String valuationPolicy, @Param("unitCost") Long unitCost,
                        @Param("valuationPolicyVersion") String valuationPolicyVersion,
                        @Param("valuationPolicyHash") String valuationPolicyHash,
                        @Param("currencyCode") String currencyCode, @Param("operationId") Long operationId,
                        @Param("now") LocalDateTime now);

    @Select("SELECT * FROM cloudmold_inventory_procurement_receipt_v3 WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId} FOR UPDATE")
    InventoryProcurementReceiptDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                   @Param("receiptLineId") String receiptLineId);

    @Update("""
            UPDATE cloudmold_inventory_procurement_receipt_v3
            SET received_quantity=#{received},pending_quantity=#{pending},accepted_quantity=#{accepted},
                rejected_quantity=#{rejected},quarantined_quantity=#{quarantined},returned_quantity=#{returned},
                version=version+1,last_operation_id=#{operationId},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId} AND version=#{expectedVersion}
            """)
    int updateQuantitiesCas(@Param("tenantId") Long tenantId, @Param("receiptLineId") String receiptLineId,
                            @Param("expectedVersion") Long expectedVersion, @Param("received") BigDecimal received,
                            @Param("pending") BigDecimal pending, @Param("accepted") BigDecimal accepted,
                            @Param("rejected") BigDecimal rejected, @Param("quarantined") BigDecimal quarantined,
                            @Param("returned") BigDecimal returned, @Param("operationId") Long operationId,
                            @Param("now") LocalDateTime now);
}
