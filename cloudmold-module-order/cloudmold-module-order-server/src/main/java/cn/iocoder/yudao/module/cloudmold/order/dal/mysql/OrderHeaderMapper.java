package cn.iocoder.yudao.module.cloudmold.order.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.OrderHeaderDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface OrderHeaderMapper extends BaseMapperX<OrderHeaderDO> {
    @Select("""
            SELECT order_id,tenant_id,order_no,run_id,buyer_id,status,total_quantity,product_amount_minor,
                   shipping_amount_minor,discount_amount_minor,payable_amount_minor,currency_code,
                   payment_id,fulfillment_id,shipment_id,refund_id,cancellation_saga_id,
                   pre_cancellation_status,cancellation_responsibility_party,
                   cancellation_responsibility_code,version,created_at,updated_at
            FROM cloudmold_order_header
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            FOR UPDATE
            """)
    OrderHeaderDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("orderId") String orderId);

    @Select("""
            SELECT order_id,tenant_id,order_no,run_id,buyer_id,status,total_quantity,product_amount_minor,
                   shipping_amount_minor,discount_amount_minor,payable_amount_minor,currency_code,
                   payment_id,fulfillment_id,shipment_id,refund_id,cancellation_saga_id,
                   pre_cancellation_status,cancellation_responsibility_party,
                   cancellation_responsibility_code,version,created_at,updated_at
            FROM cloudmold_order_header
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
            """)
    OrderHeaderDO selectForPayment(@Param("tenantId") Long tenantId, @Param("orderId") String orderId);

    @Update("""
            UPDATE cloudmold_order_header
            SET status=#{nextStatus},version=version+1,
                payment_id=COALESCE(#{paymentId},payment_id),
                fulfillment_id=COALESCE(#{fulfillmentId},fulfillment_id),
                shipment_id=COALESCE(#{shipmentId},shipment_id),
                refund_id=COALESCE(#{refundId},refund_id),
                cancellation_saga_id=COALESCE(#{cancellationSagaId},cancellation_saga_id),
                pre_cancellation_status=COALESCE(#{preCancellationStatus},pre_cancellation_status),
                cancellation_responsibility_party=COALESCE(#{responsibilityParty},cancellation_responsibility_party),
                cancellation_responsibility_code=COALESCE(#{responsibilityCode},cancellation_responsibility_code),
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND order_id=#{orderId}
              AND version=#{expectedVersion} AND status=#{expectedStatus}
            """)
    int transition(@Param("tenantId") Long tenantId, @Param("orderId") String orderId,
                   @Param("expectedVersion") Long expectedVersion, @Param("expectedStatus") String expectedStatus,
                   @Param("nextStatus") String nextStatus, @Param("paymentId") String paymentId,
                   @Param("fulfillmentId") String fulfillmentId, @Param("shipmentId") String shipmentId,
                   @Param("refundId") String refundId, @Param("cancellationSagaId") String cancellationSagaId,
                   @Param("preCancellationStatus") String preCancellationStatus,
                   @Param("responsibilityParty") String responsibilityParty,
                   @Param("responsibilityCode") String responsibilityCode,
                   @Param("now") LocalDateTime now);
}
