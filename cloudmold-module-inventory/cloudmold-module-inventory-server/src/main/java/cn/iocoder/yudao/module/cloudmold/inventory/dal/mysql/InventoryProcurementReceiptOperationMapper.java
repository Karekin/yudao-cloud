package cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryProcurementReceiptOperationDO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface InventoryProcurementReceiptOperationMapper
        extends BaseMapperX<InventoryProcurementReceiptOperationDO> {
    @Insert("""
            INSERT INTO cloudmold_inventory_procurement_receipt_operation_v3
              (tenant_id,idempotency_key,source_event_id,receipt_id,receipt_line_id,decision_version,disposition,
               operation_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{sourceEventId},#{receiptId},#{receiptLineId},#{decisionVersion},#{disposition},
               #{operationType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolve(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                        @Param("sourceEventId") String sourceEventId, @Param("receiptLineId") String receiptLineId,
                        @Param("receiptId") String receiptId,
                        @Param("decisionVersion") Long decisionVersion, @Param("disposition") String disposition,
                        @Param("operationType") String operationType, @Param("requestHash") String requestHash,
                        @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()") Long selectLastInsertId();

    @Select("SELECT * FROM cloudmold_inventory_procurement_receipt_operation_v3 WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE")
    InventoryProcurementReceiptOperationDO selectForUpdate(@Param("tenantId") Long tenantId,
                                                            @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_inventory_procurement_receipt_operation_v3
            SET status=10,ledger_transaction_id=#{result.ledgerTransactionId},
                source_balance_id=#{result.sourceBalanceId},source_aggregate_version=#{result.sourceAggregateVersion},
                target_balance_id=#{result.targetBalanceId},target_aggregate_version=#{result.targetAggregateVersion},
                received_quantity=#{result.receivedQuantity},pending_quantity=#{result.pendingQuantity},
                accepted_quantity=#{result.acceptedQuantity},rejected_quantity=#{result.rejectedQuantity},
                quarantined_quantity=#{result.quarantinedQuantity},returned_quantity=#{result.returnedQuantity},
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markSucceeded(@Param("tenantId") Long tenantId, @Param("operationId") Long operationId,
                      @Param("result") InventoryProcurementReceiptResult result, @Param("now") LocalDateTime now);
}
