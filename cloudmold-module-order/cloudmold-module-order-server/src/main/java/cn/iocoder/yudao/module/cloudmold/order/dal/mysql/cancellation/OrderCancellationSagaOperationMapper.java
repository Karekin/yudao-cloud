package cn.iocoder.yudao.module.cloudmold.order.dal.mysql.cancellation;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.cancellation.OrderCancellationSagaOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface OrderCancellationSagaOperationMapper extends BaseMapperX<OrderCancellationSagaOperationDO> {
    @Insert("""
            INSERT INTO cloudmold_order_cancellation_saga_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                        @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_order_cancellation_saga_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    OrderCancellationSagaOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                                       @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_order_cancellation_saga_operation
            SET status=10,saga_id=#{sagaId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("sagaId") String sagaId, @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
