package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.exception;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception.FulfillmentExceptionOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface FulfillmentExceptionOperationMapper extends BaseMapperX<FulfillmentExceptionOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_fulfillment_exception_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("commandType") String commandType,
                        @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   exception_id,result_json,created_at,updated_at
            FROM cloudmold_fulfillment_exception_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    FulfillmentExceptionOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                                    @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_fulfillment_exception_operation
            SET status=10,exception_id=#{exceptionId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markSucceeded(@Param("operationId") Long operationId,
                      @Param("tenantId") Long tenantId,
                      @Param("exceptionId") String exceptionId,
                      @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
