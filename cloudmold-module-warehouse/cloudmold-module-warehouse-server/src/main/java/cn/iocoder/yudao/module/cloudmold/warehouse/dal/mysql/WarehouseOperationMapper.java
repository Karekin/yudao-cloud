package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.WarehouseOperationDO;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface WarehouseOperationMapper extends BaseMapperX<WarehouseOperationDO> {
    @Insert("""
        INSERT INTO cloudmold_warehouse_operation
          (tenant_id,idempotency_key,source_event_id,operation_type,request_hash,attempt_token,status,created_at,updated_at)
        VALUES (#{tenantId},#{idempotencyKey},#{sourceEventId},#{operationType},#{requestHash},#{attemptToken},0,#{now},#{now})
        ON DUPLICATE KEY UPDATE operation_id = LAST_INSERT_ID(operation_id)
        """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                        @Param("sourceEventId") String sourceEventId, @Param("operationType") String operationType,
                        @Param("requestHash") String requestHash, @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("SELECT * FROM cloudmold_warehouse_operation WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE")
    WarehouseOperationDO selectForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
        UPDATE cloudmold_warehouse_operation SET status=10,aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
        WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
        """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
