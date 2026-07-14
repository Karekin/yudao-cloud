package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentOrderDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface FulfillmentOrderMapper extends BaseMapperX<FulfillmentOrderDO> {
    String COLUMNS = "fulfillment_id,tenant_id,fulfillment_no,run_id,order_id,order_no,seller_id,warehouse_id,status,cancellation_saga_id,pre_cancellation_status,version,created_at,updated_at";

    @Select("SELECT " + COLUMNS + " FROM cloudmold_fulfillment_order WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId} FOR UPDATE")
    FulfillmentOrderDO selectForUpdate(@Param("tenantId") Long tenantId,
                                       @Param("fulfillmentId") String fulfillmentId);

    @Select("SELECT " + COLUMNS + " FROM cloudmold_fulfillment_order WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId}")
    FulfillmentOrderDO selectByIdForValidation(@Param("tenantId") Long tenantId,
                                               @Param("fulfillmentId") String fulfillmentId);

    @Select("SELECT " + COLUMNS + " FROM cloudmold_fulfillment_order WHERE tenant_id=#{tenantId} AND order_id=#{orderId} ORDER BY fulfillment_id FOR UPDATE")
    java.util.List<FulfillmentOrderDO> selectByOrderForUpdate(@Param("tenantId") Long tenantId,
                                                               @Param("orderId") String orderId);

    @Update("""
            UPDATE cloudmold_fulfillment_order
            SET status=#{nextStatus},version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId}
              AND version=#{expectedVersion} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId, @Param("fulfillmentId") String fulfillmentId,
                   @Param("expectedVersion") Long expectedVersion, @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_fulfillment_order
            SET status=#{nextStatus},version=version+1,
                cancellation_saga_id=COALESCE(#{cancellationSagaId},cancellation_saga_id),
                pre_cancellation_status=COALESCE(#{preCancellationStatus},pre_cancellation_status),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND fulfillment_id=#{fulfillmentId}
              AND version=#{expectedVersion} AND status=#{expectedStatus}
            """)
    int transitionCancellation(@Param("tenantId") Long tenantId, @Param("fulfillmentId") String fulfillmentId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("expectedStatus") String expectedStatus,
                               @Param("nextStatus") String nextStatus,
                               @Param("cancellationSagaId") String cancellationSagaId,
                               @Param("preCancellationStatus") String preCancellationStatus,
                               @Param("now") LocalDateTime now);
}
