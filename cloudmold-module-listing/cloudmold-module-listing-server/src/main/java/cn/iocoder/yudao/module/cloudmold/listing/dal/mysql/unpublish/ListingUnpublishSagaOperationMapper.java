package cn.iocoder.yudao.module.cloudmold.listing.dal.mysql.unpublish;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.listing.dal.dataobject.unpublish.ListingUnpublishSagaOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface ListingUnpublishSagaOperationMapper extends BaseMapperX<ListingUnpublishSagaOperationDO> {
    @Insert("""
            INSERT INTO cloudmold_listing_unpublish_saga_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                        @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_listing_unpublish_saga_operation
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
            FOR UPDATE
            """)
    ListingUnpublishSagaOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                                     @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_listing_unpublish_saga_operation
            SET status=10,saga_id=#{sagaId},result_json=#{resultJson},updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("sagaId") String sagaId, @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
