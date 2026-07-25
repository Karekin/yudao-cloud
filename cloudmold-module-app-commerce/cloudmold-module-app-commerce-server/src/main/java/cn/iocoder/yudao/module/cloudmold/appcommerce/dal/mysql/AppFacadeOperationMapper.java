package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject.AppFacadeOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface AppFacadeOperationMapper {
    @Insert("""
            INSERT INTO cloudmold_app_facade_operation
              (tenant_id,buyer_principal_id,operation_type,idempotency_key,request_hash,attempt_token,
               status,first_occurred_at,created_at,updated_at)
            VALUES
              (#{tenantId},#{buyerPrincipalId},#{operationType},#{idempotencyKey},#{requestHash},#{attemptToken},
               0,#{now},#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("buyerPrincipalId") String buyerPrincipalId,
                        @Param("operationType") String operationType,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,buyer_principal_id,operation_type,idempotency_key,request_hash,
                   attempt_token,status,result_json,first_occurred_at
            FROM cloudmold_app_facade_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    AppFacadeOperationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_app_facade_operation
            SET status=10,result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
              AND attempt_token=#{attemptToken} AND status=0
            """)
    int markSucceeded(@Param("tenantId") Long tenantId,
                      @Param("operationId") Long operationId,
                      @Param("attemptToken") String attemptToken,
                      @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
