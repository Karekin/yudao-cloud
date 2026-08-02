package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockCountAdjustmentOperationDO;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InventoryStockCountAdjustmentOperationMapper
        extends BaseMapperX<InventoryStockCountAdjustmentOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_stock_count_adjustment_operation
              (tenant_id,idempotency_key,source_event_id,stock_count_line_id,request_hash,attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{sourceEventId},#{stockCountLineId},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId,
                        @Param("idempotencyKey") String idempotencyKey,
                        @Param("sourceEventId") String sourceEventId,
                        @Param("stockCountLineId") String stockCountLineId,
                        @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken,
                        @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_inventory_stock_count_adjustment_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    InventoryStockCountAdjustmentOperationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_stock_count_adjustment_operation
            SET status=10,adjustment_id=#{result.adjustmentId},ledger_transaction_id=#{result.ledgerTransactionId},
                result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            """)
    int markSucceeded(@Param("tenantId") Long tenantId,
                      @Param("operationId") Long operationId,
                      @Param("result") InventoryStockCountAdjustmentResult result,
                      @Param("resultJson") String resultJson,
                      @Param("now") LocalDateTime now);

    default int markSucceeded(Long tenantId, Long operationId, InventoryStockCountAdjustmentResult result,
                              LocalDateTime now) {
        return markSucceeded(tenantId, operationId, result, JsonUtils.toJsonString(result), now);
    }
}
