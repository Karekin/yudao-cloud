package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ScheduleFulfillmentDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface ScheduleFulfillmentMapper extends BaseMapperX<ScheduleFulfillmentDO> {
    @Insert("""
            INSERT IGNORE INTO cloudmold_warehouse_procurement_schedule_fulfillment
              (schedule_fulfillment_id,tenant_id,procurement_order_id,procurement_order_item_id,delivery_schedule_id,
               ordered_quantity,cancelled_quantity,allowed_over_receipt_quantity,received_quantity,pending_quality_quantity,
               accepted_quantity,rejected_quantity,quarantined_quantity,returned_quantity,
               tolerance_policy_version,tolerance_policy_hash,
               version,created_at,updated_at)
            VALUES
              (#{scheduleFulfillmentId},#{tenantId},#{procurementOrderId},#{procurementOrderItemId},#{deliveryScheduleId},
               #{orderedQuantity},#{cancelledQuantity},#{allowedOverReceiptQuantity},#{receivedQuantity},
               #{pendingQualityQuantity},#{acceptedQuantity},#{rejectedQuantity},#{quarantinedQuantity},#{returnedQuantity},
               #{tolerancePolicyVersion},#{tolerancePolicyHash},#{version},#{createdAt},#{updatedAt})
            """)
    int insertIgnore(ScheduleFulfillmentDO row);

    @Select("""
            SELECT * FROM cloudmold_warehouse_procurement_schedule_fulfillment
            WHERE tenant_id=#{tenantId} AND procurement_order_item_id=#{procurementOrderItemId}
              AND delivery_schedule_id=#{deliveryScheduleId}
            FOR UPDATE
            """)
    ScheduleFulfillmentDO selectForUpdate(@Param("tenantId") Long tenantId,
                                          @Param("procurementOrderItemId") String procurementOrderItemId,
                                          @Param("deliveryScheduleId") String deliveryScheduleId);

    @Update("""
            UPDATE cloudmold_warehouse_procurement_schedule_fulfillment
            SET received_quantity=#{receivedQuantity},pending_quality_quantity=#{pendingQualityQuantity},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND schedule_fulfillment_id=#{scheduleFulfillmentId}
              AND version=#{version}
            """)
    int updateReceiptCas(@Param("tenantId") Long tenantId,
                         @Param("scheduleFulfillmentId") String scheduleFulfillmentId,
                         @Param("version") Long version,
                         @Param("receivedQuantity") BigDecimal receivedQuantity,
                         @Param("pendingQualityQuantity") BigDecimal pendingQualityQuantity,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_warehouse_procurement_schedule_fulfillment
            SET pending_quality_quantity=#{pendingQuantity},accepted_quantity=#{acceptedQuantity},
                rejected_quantity=#{rejectedQuantity},quarantined_quantity=#{quarantinedQuantity},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND schedule_fulfillment_id=#{scheduleFulfillmentId}
              AND version=#{version}
            """)
    int updateQualityCas(@Param("tenantId") Long tenantId,
                         @Param("scheduleFulfillmentId") String scheduleFulfillmentId,
                         @Param("version") Long version,
                         @Param("pendingQuantity") BigDecimal pendingQuantity,
                         @Param("acceptedQuantity") BigDecimal acceptedQuantity,
                         @Param("rejectedQuantity") BigDecimal rejectedQuantity,
                         @Param("quarantinedQuantity") BigDecimal quarantinedQuantity,
                         @Param("now") LocalDateTime now);
}
