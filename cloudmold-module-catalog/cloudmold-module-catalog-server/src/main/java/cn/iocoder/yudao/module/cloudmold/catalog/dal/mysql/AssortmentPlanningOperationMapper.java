package cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentPlanningOperationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AssortmentPlanningOperationMapper extends BaseMapperX<AssortmentPlanningOperationDO> {

    @Insert("""
        INSERT INTO cloudmold_assortment_planning_operation
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
        SELECT * FROM cloudmold_assortment_planning_operation
        WHERE operation_id=#{operationId} AND tenant_id=#{tenantId}
        FOR UPDATE
        """)
    AssortmentPlanningOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                                  @Param("tenantId") Long tenantId);

    @Update("""
        UPDATE cloudmold_assortment_planning_operation
        SET status=10,aggregate_type=#{aggregateType},aggregate_id=#{aggregateId},
            result_json=#{resultJson},updated_at=#{now}
        WHERE operation_id=#{operationId} AND tenant_id=#{tenantId} AND status=0
        """)
    int markSucceeded(@Param("operationId") Long operationId,
                      @Param("tenantId") Long tenantId,
                      @Param("aggregateType") String aggregateType,
                      @Param("aggregateId") String aggregateId,
                      @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);
}
