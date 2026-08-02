package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockTransferOperationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface InventoryStockTransferOperationMapper extends BaseMapperX<InventoryStockTransferOperationDO> {

    @Insert("""
            INSERT INTO cloudmold_inventory_stock_transfer_operation_v3
              (tenant_id,idempotency_key,source_event_id,movement_group_id,operation_type,request_hash,
               attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{sourceEventId},#{movementGroupId},#{operationType},#{requestHash},
               #{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                        @Param("sourceEventId") String sourceEventId,
                        @Param("movementGroupId") String movementGroupId,
                        @Param("operationType") String operationType, @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_inventory_stock_transfer_operation_v3
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    InventoryStockTransferOperationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_stock_transfer_operation_v3
            SET status=10,ledger_transaction_id=#{result.ledgerTransactionId},
                source_balance_id=#{result.sourceBalanceId},source_aggregate_version=#{result.sourceAggregateVersion},
                source_on_hand_quantity=#{result.sourceOnHandQuantity},
                source_in_transit_quantity=#{result.sourceInTransitQuantity},
                target_balance_id=#{result.targetBalanceId},target_aggregate_version=#{result.targetAggregateVersion},
                target_on_hand_quantity=#{result.targetOnHandQuantity},
                target_in_transit_quantity=#{result.targetInTransitQuantity},
                cumulative_dispatched_quantity=#{result.cumulativeDispatchedQuantity},
                cumulative_received_quantity=#{result.cumulativeReceivedQuantity},
                outstanding_quantity=#{result.outstandingQuantity},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markSucceeded(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId,
                      @Param("result") InventoryStockTransferResult result, @Param("now") LocalDateTime now);
}
