package cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupplierReturnStoreMapper extends BaseMapperX<SupplierReturnDocumentDO> {

    @Insert("""
            INSERT INTO cloudmold_supplier_return_operation
              (tenant_id,idempotency_key,source_event_id,operation_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES
              (#{tenantId},#{idempotencyKey},#{sourceEventId},#{operationType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("sourceEventId") String sourceEventId,
                                 @Param("operationType") String operationType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT * FROM cloudmold_supplier_return_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    SupplierReturnOperationDO selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_supplier_return_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supplier_return
              (return_id,tenant_id,return_code,purchase_order_id,receipt_id,supplier_id,owner_type,owner_id,
               warehouse_id,reason_code,remark,status,created_by_principal_id,submitted_by_principal_id,
               approved_by_principal_id,completed_by_principal_id,cancelled_by_principal_id,submitted_at,
               approved_at,completed_at,cancelled_at,version,created_at,updated_at)
            VALUES
              (#{returnId},#{tenantId},#{returnCode},#{purchaseOrderId},#{receiptId},#{supplierId},#{ownerType},
               #{ownerId},#{warehouseId},#{reasonCode},#{remark},#{status},#{createdByPrincipalId},
               #{submittedByPrincipalId},#{approvedByPrincipalId},#{completedByPrincipalId},#{cancelledByPrincipalId},
               #{submittedAt},#{approvedAt},#{completedAt},#{cancelledAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertDocument(SupplierReturnDocumentDO row);

    @Select("""
            SELECT * FROM cloudmold_supplier_return
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            """)
    SupplierReturnDocumentDO selectDocument(@Param("tenantId") Long tenantId,
                                            @Param("returnId") String returnId);

    @Select("""
            SELECT * FROM cloudmold_supplier_return
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            FOR UPDATE
            """)
    SupplierReturnDocumentDO selectDocumentForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("returnId") String returnId);

    @Update("""
            UPDATE cloudmold_supplier_return
            SET status=#{status},reason_code=#{reasonCode},remark=#{remark},
                submitted_by_principal_id=#{submittedByPrincipalId},approved_by_principal_id=#{approvedByPrincipalId},
                completed_by_principal_id=#{completedByPrincipalId},cancelled_by_principal_id=#{cancelledByPrincipalId},
                submitted_at=#{submittedAt},approved_at=#{approvedAt},completed_at=#{completedAt},
                cancelled_at=#{cancelledAt},version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId} AND version=#{expectedVersion}
            """)
    int updateDocumentStatusCas(@Param("tenantId") Long tenantId,
                                @Param("returnId") String returnId,
                                @Param("expectedVersion") Long expectedVersion,
                                @Param("nextVersion") Long nextVersion,
                                @Param("status") String status,
                                @Param("reasonCode") String reasonCode,
                                @Param("remark") String remark,
                                @Param("submittedByPrincipalId") String submittedByPrincipalId,
                                @Param("approvedByPrincipalId") String approvedByPrincipalId,
                                @Param("completedByPrincipalId") String completedByPrincipalId,
                                @Param("cancelledByPrincipalId") String cancelledByPrincipalId,
                                @Param("submittedAt") LocalDateTime submittedAt,
                                @Param("approvedAt") LocalDateTime approvedAt,
                                @Param("completedAt") LocalDateTime completedAt,
                                @Param("cancelledAt") LocalDateTime cancelledAt,
                                @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supplier_return_line
              (return_line_id,tenant_id,return_id,line_number,receipt_line_id,purchase_order_item_id,
               purchase_order_schedule_id,quality_decision_id,decision_version,inspection_split_id,source_disposition,
               canonical_sku_id,warehouse_id,location_id,lot_id,return_quantity,dispatched_quantity,outstanding_quantity,
               uom_code,valuation_policy_id,valuation_policy_version,valuation_policy_hash,unit_cost_amount_minor,
               currency_code,quality_evidence_ref,remark,status,version,created_at,updated_at)
            VALUES
              (#{returnLineId},#{tenantId},#{returnId},#{lineNumber},#{receiptLineId},#{purchaseOrderItemId},
               #{purchaseOrderScheduleId},#{qualityDecisionId},#{decisionVersion},#{inspectionSplitId},
               #{sourceDisposition},#{canonicalSkuId},#{warehouseId},#{locationId},#{lotId},#{returnQuantity},
               #{dispatchedQuantity},#{outstandingQuantity},#{uomCode},#{valuationPolicyId},
               #{valuationPolicyVersion},#{valuationPolicyHash},#{unitCostAmountMinor},#{currencyCode},
               #{qualityEvidenceRef},#{remark},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertLine(SupplierReturnLineDO row);

    @Select("""
            SELECT * FROM cloudmold_supplier_return_line
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            ORDER BY line_number ASC, return_line_id ASC
            """)
    List<SupplierReturnLineDO> selectLines(@Param("tenantId") Long tenantId,
                                           @Param("returnId") String returnId);

    @Select("""
            SELECT * FROM cloudmold_supplier_return_line
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId} AND return_line_id=#{returnLineId}
            FOR UPDATE
            """)
    SupplierReturnLineDO selectLineForUpdate(@Param("tenantId") Long tenantId,
                                             @Param("returnId") String returnId,
                                             @Param("returnLineId") String returnLineId);

    @Select("""
            SELECT * FROM cloudmold_supplier_return_line
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId} AND line_number=#{lineNumber}
            FOR UPDATE
            """)
    SupplierReturnLineDO selectLineForUpdateByNumber(@Param("tenantId") Long tenantId,
                                                     @Param("returnId") String returnId,
                                                     @Param("lineNumber") Integer lineNumber);

    @Update("""
            UPDATE cloudmold_supplier_return_line
            SET dispatched_quantity=#{dispatchedQuantity},outstanding_quantity=#{outstandingQuantity},
                status=#{status},version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND return_line_id=#{returnLineId} AND version=#{expectedVersion}
            """)
    int updateLineDispatchCas(@Param("tenantId") Long tenantId,
                              @Param("returnLineId") String returnLineId,
                              @Param("expectedVersion") Long expectedVersion,
                              @Param("nextVersion") Long nextVersion,
                              @Param("dispatchedQuantity") BigDecimal dispatchedQuantity,
                              @Param("outstandingQuantity") BigDecimal outstandingQuantity,
                              @Param("status") String status,
                              @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_supplier_return_line
            SET status=#{status},version=#{nextVersion},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND return_line_id=#{returnLineId} AND version=#{expectedVersion}
            """)
    int updateLineStatusCas(@Param("tenantId") Long tenantId,
                            @Param("returnLineId") String returnLineId,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("nextVersion") Long nextVersion,
                            @Param("status") String status,
                            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_supplier_return_dispatch_batch
              (batch_id,tenant_id,return_id,batch_no,status,dispatched_by_principal_id,remark,version,
               occurred_at,created_at,updated_at)
            VALUES
              (#{batchId},#{tenantId},#{returnId},#{batchNo},#{status},#{dispatchedByPrincipalId},#{remark},
               #{version},#{occurredAt},#{createdAt},#{updatedAt})
            """)
    int insertDispatchBatch(SupplierReturnDispatchBatchDO row);

    @Select("""
            SELECT * FROM cloudmold_supplier_return_dispatch_batch
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            ORDER BY occurred_at ASC, batch_id ASC
            """)
    List<SupplierReturnDispatchBatchDO> selectDispatchBatches(@Param("tenantId") Long tenantId,
                                                              @Param("returnId") String returnId);

    @Insert("""
            INSERT INTO cloudmold_supplier_return_dispatch_line
              (execution_line_id,tenant_id,batch_id,return_id,return_line_id,line_number,source_disposition,
               dispatched_quantity,cumulative_dispatched_quantity,outstanding_quantity,inventory_operation_id,
               ledger_transaction_id,source_balance_id,inventory_aggregate_version,status,remark,version,
               occurred_at,created_at,updated_at)
            VALUES
              (#{executionLineId},#{tenantId},#{batchId},#{returnId},#{returnLineId},#{lineNumber},
               #{sourceDisposition},#{dispatchedQuantity},#{cumulativeDispatchedQuantity},#{outstandingQuantity},
               #{inventoryOperationId},#{ledgerTransactionId},#{sourceBalanceId},#{inventoryAggregateVersion},
               #{status},#{remark},#{version},#{occurredAt},#{createdAt},#{updatedAt})
            """)
    int insertDispatchLine(SupplierReturnDispatchLineDO row);

    @Select("""
            SELECT * FROM cloudmold_supplier_return_dispatch_line
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            ORDER BY occurred_at ASC, execution_line_id ASC
            """)
    List<SupplierReturnDispatchLineDO> selectDispatchLines(@Param("tenantId") Long tenantId,
                                                           @Param("returnId") String returnId);

    @Insert("""
            INSERT INTO cloudmold_supplier_return_status_history
              (history_id,tenant_id,operation_id,business_object_type,business_object_id,status,status_version,
               stage_code,stage_label,remark,changed_at,created_at)
            VALUES
              (#{historyId},#{tenantId},#{operationId},#{businessObjectType},#{businessObjectId},#{status},
               #{statusVersion},#{stageCode},#{stageLabel},#{remark},#{changedAt},#{createdAt})
            """)
    int insertStatusHistory(SupplierReturnStatusHistoryDO row);

    @Select("""
            SELECT * FROM cloudmold_supplier_return_status_history
            WHERE tenant_id=#{tenantId}
              AND (business_object_id=#{returnId}
                   OR business_object_id IN (
                       SELECT return_line_id FROM cloudmold_supplier_return_line
                       WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
                   ))
            ORDER BY changed_at ASC, history_id ASC
            """)
    List<SupplierReturnStatusHistoryDO> selectStatusHistory(@Param("tenantId") Long tenantId,
                                                            @Param("returnId") String returnId);

    @Select("""
            SELECT COALESCE(SUM(return_quantity),0)
            FROM cloudmold_supplier_return_line
            WHERE tenant_id=#{tenantId}
              AND quality_decision_id=#{qualityDecisionId}
              AND decision_version=#{decisionVersion}
              AND inspection_split_id=#{inspectionSplitId}
              AND source_disposition=#{sourceDisposition}
              AND status<>'CANCELLED'
            """)
    BigDecimal sumActiveReturnQuantityByQualityKey(@Param("tenantId") Long tenantId,
                                                   @Param("qualityDecisionId") String qualityDecisionId,
                                                   @Param("decisionVersion") Long decisionVersion,
                                                   @Param("inspectionSplitId") String inspectionSplitId,
                                                   @Param("sourceDisposition") String sourceDisposition);

    @Select("""
            <script>
            SELECT COALESCE(SUM(return_quantity),0)
            FROM cloudmold_supplier_return_line
            WHERE tenant_id=#{tenantId}
              AND quality_decision_id=#{qualityDecisionId}
              AND decision_version=#{decisionVersion}
              AND inspection_split_id=#{inspectionSplitId}
              AND source_disposition=#{sourceDisposition}
              AND status&lt;&gt;'CANCELLED'
              <if test="excludeReturnId != null">
                AND return_id&lt;&gt;#{excludeReturnId}
              </if>
            </script>
            """)
    BigDecimal sumActiveReturnQuantityByQualityKeyExcludingReturn(@Param("tenantId") Long tenantId,
                                                                  @Param("qualityDecisionId") String qualityDecisionId,
                                                                  @Param("decisionVersion") Long decisionVersion,
                                                                  @Param("inspectionSplitId") String inspectionSplitId,
                                                                  @Param("sourceDisposition") String sourceDisposition,
                                                                  @Param("excludeReturnId") String excludeReturnId);
}
