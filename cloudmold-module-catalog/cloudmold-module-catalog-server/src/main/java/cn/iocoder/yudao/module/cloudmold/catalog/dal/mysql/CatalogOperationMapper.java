package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.CatalogOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface CatalogOperationMapper extends BaseMapperX<CatalogOperationDO> {
    @Insert("""
            INSERT INTO cloudmold_catalog_operation
              (tenant_id, idempotency_key, request_hash, attempt_token, status, created_at, updated_at)
            VALUES (#{tenantId}, #{idempotencyKey}, #{requestHash}, #{attemptToken}, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE operation_id = LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                        @Param("requestHash") String requestHash, @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("SELECT * FROM cloudmold_catalog_operation WHERE operation_id = #{operationId} AND tenant_id = #{tenantId} FOR UPDATE")
    CatalogOperationDO selectForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_catalog_operation SET status = 10, result_json = #{resultJson}, updated_at = #{now}
            WHERE operation_id = #{operationId} AND tenant_id = #{tenantId} AND status = 0
            """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("resultJson") String resultJson, @Param("now") LocalDateTime now);
}
