package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.OrderCancellationSagaDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface OrderCancellationSagaMapper extends BaseMapperX<OrderCancellationSagaDO> {

    @Select("SELECT * FROM cloudmold_order_cancellation_saga WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}")
    OrderCancellationSagaDO selectTenantSaga(@Param("tenantId") Long tenantId, @Param("sagaId") String sagaId);

    @Select("SELECT * FROM cloudmold_order_cancellation_saga WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId} FOR UPDATE")
    OrderCancellationSagaDO selectForUpdate(@Param("tenantId") Long tenantId, @Param("sagaId") String sagaId);

    @Select("SELECT * FROM cloudmold_order_cancellation_saga WHERE tenant_id=#{tenantId} AND idempotency_key=#{idempotencyKey}")
    OrderCancellationSagaDO selectByIdempotency(@Param("tenantId") Long tenantId,
                                                 @Param("idempotencyKey") String idempotencyKey);

    @Select("SELECT * FROM cloudmold_order_cancellation_saga WHERE tenant_id=#{tenantId} AND order_id=#{orderId}")
    OrderCancellationSagaDO selectByOrder(@Param("tenantId") Long tenantId, @Param("orderId") String orderId);

    @Select("""
            SELECT * FROM cloudmold_order_cancellation_saga
            WHERE status IN ('REQUESTED','CANCELLING_FULFILLMENT','FULFILLMENT_CANCELLED',
              'REFUNDING_PAYMENT','PAYMENT_REFUNDED','RELEASING_RESERVATIONS',
              'RESERVATIONS_RELEASED','CANCELLING_ORDER','RETRY_SCHEDULED')
              AND (status <> 'RETRY_SCHEDULED' OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until < #{now})
            ORDER BY created_at,saga_id LIMIT #{limit}
            """)
    List<OrderCancellationSagaDO> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE cloudmold_order_cancellation_saga
            SET lease_owner=#{leaseOwner},lease_until=#{leaseUntil},attempt_count=attempt_count+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND saga_id=#{sagaId}
              AND status IN ('REQUESTED','CANCELLING_FULFILLMENT','FULFILLMENT_CANCELLED',
                'REFUNDING_PAYMENT','PAYMENT_REFUNDED','RELEASING_RESERVATIONS',
                'RESERVATIONS_RELEASED','CANCELLING_ORDER','RETRY_SCHEDULED')
              AND (status <> 'RETRY_SCHEDULED' OR next_retry_at <= #{now})
              AND (lease_until IS NULL OR lease_until < #{now})
              AND attempt_count < max_attempts
            """)
    int claim(@Param("tenantId") Long tenantId, @Param("sagaId") String sagaId,
              @Param("leaseOwner") String leaseOwner, @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now);
}
