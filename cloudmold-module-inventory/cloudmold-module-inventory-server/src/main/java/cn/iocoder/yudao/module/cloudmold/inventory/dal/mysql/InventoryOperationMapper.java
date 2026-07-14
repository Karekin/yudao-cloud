package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface InventoryOperationMapper extends BaseMapperX<InventoryOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_operation
              (tenant_id, idempotency_key, source_event_id, command_type, request_hash,
               attempt_token, status, created_at, updated_at)
            VALUES
              (#{tenantId}, #{idempotencyKey}, #{sourceEventId}, #{commandType}, #{requestHash},
               #{attemptToken}, 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE operation_id = LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("sourceEventId") String sourceEventId,
                        @Param("commandType") String commandType,
                        @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("SELECT * FROM cloudmold_inventory_operation WHERE operation_id = #{operationId} AND tenant_id = #{tenantId} FOR UPDATE")
    InventoryOperationDO selectForUpdate(@Param("operationId") Long operationId,
                                         @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_inventory_operation
            SET status = 10, ledger_transaction_id = #{ledgerTransactionId},
                result_json = #{resultJson}, updated_at = #{now}
            WHERE operation_id = #{operationId} AND tenant_id = #{tenantId} AND status = 0
            """)
    int markSucceeded(@Param("operationId") Long operationId,
                      @Param("tenantId") Long tenantId,
                      @Param("ledgerTransactionId") Long ledgerTransactionId,
                      @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);

}
