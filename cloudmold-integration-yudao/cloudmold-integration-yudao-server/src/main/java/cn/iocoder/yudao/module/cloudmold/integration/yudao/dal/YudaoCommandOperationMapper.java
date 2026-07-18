package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface YudaoCommandOperationMapper {

    @Insert("""
            INSERT INTO cloudmold_yudao_command_operation
              (tenant_id, operation_type, idempotency_key, request_hash, attempt_token, status, created_at, updated_at)
            VALUES (#{tenantId}, #{operationType}, #{idempotencyKey}, #{requestHash}, #{attemptToken}, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE operation_id = LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("operationType") String operationType,
                        @Param("idempotencyKey") String idempotencyKey, @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id, tenant_id, operation_type, idempotency_key, request_hash,
                   attempt_token, status, result_json
              FROM cloudmold_yudao_command_operation
             WHERE operation_id = #{operationId} AND tenant_id = #{tenantId}
             FOR UPDATE
            """)
    YudaoCommandOperationRow selectForUpdate(@Param("operationId") Long operationId,
                                              @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_yudao_command_operation
               SET status = 10, result_json = #{resultJson}, updated_at = #{now}
             WHERE operation_id = #{operationId} AND tenant_id = #{tenantId} AND status = 0
            """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("resultJson") String resultJson, @Param("now") LocalDateTime now);
}
