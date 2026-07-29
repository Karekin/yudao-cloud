package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundOperationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 入库命令幂等信封 Mapper，与 WarehouseOperationMapper 同形（ON DUPLICATE KEY 回填 operation_id） */
@Mapper
public interface InboundOperationMapper extends BaseMapperX<InboundOperationDO> {
    @Insert("""
            INSERT INTO cloudmold_inbound_operation
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

    @Select("SELECT * FROM cloudmold_inbound_operation WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} FOR UPDATE")
    InboundOperationDO selectForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_inbound_operation SET status=10,aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
            WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
            """)
    int markSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                      @Param("aggregateId") String aggregateId, @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
