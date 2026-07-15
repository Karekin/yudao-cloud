package cn.iocoder.yudao.module.cloudmold.identity.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.IdentityOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface IdentityOperationMapper extends BaseMapperX<IdentityOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_identity_operation
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
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   principal_id,result_json,created_at,updated_at
            FROM cloudmold_identity_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    IdentityOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                        @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_identity_operation
            SET status=10,principal_id=#{principalId},result_json=CAST(#{resultJson} AS JSON),updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("principalId") String principalId, @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
