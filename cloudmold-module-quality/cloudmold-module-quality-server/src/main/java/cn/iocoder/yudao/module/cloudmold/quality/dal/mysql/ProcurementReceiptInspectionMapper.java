package cn.iocoder.yudao.module.cloudmold.quality.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.ProcurementReceiptInspectionRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.StandardVersion;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcurementReceiptInspectionMapper {

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_operation
              (tenant_id,idempotency_key,operation_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{operationType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("operationType") String operationType,
                                 @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,operation_type,request_hash,attempt_token,status,
                   inspection_id,aggregate_version,result_status,final_decision,received_quantity,
                   sampled_quantity,accepted_quantity,rejected_quantity,quarantined_quantity
            FROM cloudmold_procurement_receipt_inspection_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                       @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_procurement_receipt_inspection_operation
            SET status=10,inspection_id=#{inspectionId},aggregate_version=#{aggregateVersion},
                result_status=#{resultStatus},final_decision=#{finalDecision},
                received_quantity=#{receivedQuantity},sampled_quantity=#{sampledQuantity},
                accepted_quantity=#{acceptedQuantity},rejected_quantity=#{rejectedQuantity},
                quarantined_quantity=#{quarantinedQuantity},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("inspectionId") String inspectionId,
                               @Param("aggregateVersion") Long aggregateVersion,
                               @Param("resultStatus") String resultStatus,
                               @Param("finalDecision") String finalDecision,
                               @Param("receivedQuantity") java.math.BigDecimal receivedQuantity,
                               @Param("sampledQuantity") java.math.BigDecimal sampledQuantity,
                               @Param("acceptedQuantity") java.math.BigDecimal acceptedQuantity,
                               @Param("rejectedQuantity") java.math.BigDecimal rejectedQuantity,
                               @Param("quarantinedQuantity") java.math.BigDecimal quarantinedQuantity,
                               @Param("now") LocalDateTime now);

    @Select("""
            SELECT standard_version_id,tenant_id,standard_id,standard_version,content_sha256,
                   approver_principal_id,effective_at,created_at
            FROM cloudmold_quality_standard_version
            WHERE tenant_id=#{tenantId} AND standard_id=#{standardId}
              AND standard_version=#{standardVersion} AND standard_version_id=#{standardVersionId}
            """)
    StandardVersion selectStandardVersion(@Param("tenantId") Long tenantId,
                                          @Param("standardId") String standardId,
                                          @Param("standardVersion") Long standardVersion,
                                          @Param("standardVersionId") String standardVersionId);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection
              (inspection_id,tenant_id,inspection_code,receipt_id,purchase_order_id,supplier_id,owner_type,
               owner_id,business_no,standard_id,
               standard_version,standard_version_id,standard_content_sha256,created_by_principal_id,
               last_decision_actor_principal_id,completed_by_principal_id,status,final_decision,
               received_quantity,sampled_quantity,accepted_quantity,rejected_quantity,
               quarantined_quantity,version,created_at,updated_at)
            VALUES (#{inspectionId},#{tenantId},#{inspectionCode},#{receiptId},#{purchaseOrderId},
                    #{supplierId},#{ownerType},#{ownerId},#{businessNo},
                    #{standardId},#{standardVersion},#{standardVersionId},#{standardContentSha256},
                    #{createdByPrincipalId},#{lastDecisionActorPrincipalId},#{completedByPrincipalId},
                    #{status},#{finalDecision},#{receivedQuantity},#{sampledQuantity},
                    #{acceptedQuantity},#{rejectedQuantity},#{quarantinedQuantity},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertInspection(Inspection value);

    @Select("""
            SELECT inspection_id,tenant_id,inspection_code,receipt_id,purchase_order_id,standard_id,
                   supplier_id,owner_type,owner_id,business_no,
                   standard_version,standard_version_id,standard_content_sha256,created_by_principal_id,
                   last_decision_actor_principal_id,completed_by_principal_id,status,final_decision,
                   received_quantity,sampled_quantity,accepted_quantity,rejected_quantity,
                   quarantined_quantity,version,completed_at,created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            FOR UPDATE
            """)
    Inspection selectInspectionForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("inspectionId") String inspectionId);

    @Select("""
            SELECT inspection_id,tenant_id,inspection_code,receipt_id,purchase_order_id,standard_id,
                   supplier_id,owner_type,owner_id,business_no,
                   standard_version,standard_version_id,standard_content_sha256,created_by_principal_id,
                   last_decision_actor_principal_id,completed_by_principal_id,status,final_decision,
                   received_quantity,sampled_quantity,accepted_quantity,rejected_quantity,
                   quarantined_quantity,version,completed_at,created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            """)
    Inspection selectInspection(@Param("tenantId") Long tenantId,
                                @Param("inspectionId") String inspectionId);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_procurement_receipt_inspection
            WHERE tenant_id=#{tenantId}
              AND (#{status} IS NULL OR status=#{status})
              AND (#{receiptId} IS NULL OR receipt_id=#{receiptId})
              AND (#{purchaseOrderId} IS NULL OR purchase_order_id=#{purchaseOrderId})
              AND (#{supplierId} IS NULL OR supplier_id=#{supplierId})
              AND (#{ownerId} IS NULL OR owner_id=#{ownerId})
            """)
    long countInspections(@Param("tenantId") Long tenantId,
                          @Param("status") String status,
                          @Param("receiptId") String receiptId,
                          @Param("purchaseOrderId") String purchaseOrderId,
                          @Param("supplierId") String supplierId,
                          @Param("ownerId") String ownerId);

    @Select("""
            SELECT inspection_id,tenant_id,inspection_code,receipt_id,purchase_order_id,standard_id,
                   supplier_id,owner_type,owner_id,business_no,
                   standard_version,standard_version_id,standard_content_sha256,created_by_principal_id,
                   last_decision_actor_principal_id,completed_by_principal_id,status,final_decision,
                   received_quantity,sampled_quantity,accepted_quantity,rejected_quantity,
                   quarantined_quantity,version,completed_at,created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection
            WHERE tenant_id=#{tenantId}
              AND (#{status} IS NULL OR status=#{status})
              AND (#{receiptId} IS NULL OR receipt_id=#{receiptId})
              AND (#{purchaseOrderId} IS NULL OR purchase_order_id=#{purchaseOrderId})
              AND (#{supplierId} IS NULL OR supplier_id=#{supplierId})
              AND (#{ownerId} IS NULL OR owner_id=#{ownerId})
            ORDER BY updated_at DESC,inspection_id DESC
            LIMIT #{offset},#{pageSize}
            """)
    List<Inspection> selectInspections(@Param("tenantId") Long tenantId,
                                       @Param("status") String status,
                                       @Param("receiptId") String receiptId,
                                       @Param("purchaseOrderId") String purchaseOrderId,
                                       @Param("supplierId") String supplierId,
                                       @Param("ownerId") String ownerId,
                                       @Param("offset") long offset,
                                       @Param("pageSize") int pageSize);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_line
              (inspection_line_id,tenant_id,inspection_id,line_number,receipt_line_id,purchase_order_id,
               item_id,schedule_id,canonical_sku_id,uom_code,supplier_id,owner_type,owner_id,
               valuation_policy,valuation_policy_version,valuation_policy_hash,unit_cost_amount_minor,
               currency_code,received_quantity,sampled_quantity,
               accepted_quantity,rejected_quantity,quarantined_quantity,status,version,created_at,updated_at)
            VALUES (#{inspectionLineId},#{tenantId},#{inspectionId},#{lineNumber},#{receiptLineId},
                    #{purchaseOrderId},#{itemId},#{scheduleId},#{canonicalSkuId},#{uomCode},
                    #{supplierId},#{ownerType},#{ownerId},#{valuationPolicy},#{valuationPolicyVersion},
                    #{valuationPolicyHash},#{unitCostAmountMinor},#{currencyCode},
                    #{receivedQuantity},#{sampledQuantity},#{acceptedQuantity},#{rejectedQuantity},
                    #{quarantinedQuantity},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertLine(InspectionLine value);

    @Select("""
            SELECT inspection_line_id,tenant_id,inspection_id,line_number,receipt_line_id,purchase_order_id,
                   item_id,schedule_id,canonical_sku_id,uom_code,supplier_id,owner_type,owner_id,
                   valuation_policy,valuation_policy_version,valuation_policy_hash,unit_cost_amount_minor,
                   currency_code,received_quantity,sampled_quantity,
                   accepted_quantity,rejected_quantity,quarantined_quantity,status,version,completed_at,
                   created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection_line
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
              AND inspection_line_id=#{inspectionLineId}
            FOR UPDATE
            """)
    InspectionLine selectLineForUpdate(@Param("tenantId") Long tenantId,
                                       @Param("inspectionId") String inspectionId,
                                       @Param("inspectionLineId") String inspectionLineId);

    @Select("""
            SELECT inspection_line_id,tenant_id,inspection_id,line_number,receipt_line_id,purchase_order_id,
                   item_id,schedule_id,canonical_sku_id,uom_code,supplier_id,owner_type,owner_id,
                   valuation_policy,valuation_policy_version,valuation_policy_hash,unit_cost_amount_minor,
                   currency_code,received_quantity,sampled_quantity,
                   accepted_quantity,rejected_quantity,quarantined_quantity,status,version,completed_at,
                   created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection_line
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            ORDER BY line_number,inspection_line_id
            """)
    List<InspectionLine> selectLines(@Param("tenantId") Long tenantId,
                                     @Param("inspectionId") String inspectionId);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_split
              (inspection_split_id,tenant_id,inspection_id,inspection_line_id,split_number,warehouse_id,
               location_id,lot_id,uom_code,received_quantity,sampled_quantity,accepted_quantity,
               rejected_quantity,quarantined_quantity,status,version,created_at,updated_at)
            VALUES (#{inspectionSplitId},#{tenantId},#{inspectionId},#{inspectionLineId},#{splitNumber},
                    #{warehouseId},#{locationId},#{lotId},#{uomCode},#{receivedQuantity},#{sampledQuantity},
                    #{acceptedQuantity},#{rejectedQuantity},#{quarantinedQuantity},#{status},#{version},
                    #{createdAt},#{updatedAt})
            """)
    int insertSplit(InspectionSplit value);

    @Select("""
            SELECT inspection_split_id,tenant_id,inspection_id,inspection_line_id,split_number,warehouse_id,
                   location_id,lot_id,uom_code,received_quantity,sampled_quantity,accepted_quantity,
                   rejected_quantity,quarantined_quantity,status,version,completed_at,created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection_split
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
              AND inspection_line_id=#{inspectionLineId} AND inspection_split_id=#{inspectionSplitId}
            FOR UPDATE
            """)
    InspectionSplit selectSplitForUpdate(@Param("tenantId") Long tenantId,
                                         @Param("inspectionId") String inspectionId,
                                         @Param("inspectionLineId") String inspectionLineId,
                                         @Param("inspectionSplitId") String inspectionSplitId);

    @Select("""
            SELECT inspection_split_id,tenant_id,inspection_id,inspection_line_id,split_number,warehouse_id,
                   location_id,lot_id,uom_code,received_quantity,sampled_quantity,accepted_quantity,
                   rejected_quantity,quarantined_quantity,status,version,completed_at,created_at,updated_at
            FROM cloudmold_procurement_receipt_inspection_split
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            ORDER BY inspection_line_id,split_number,inspection_split_id
            """)
    List<InspectionSplit> selectSplits(@Param("tenantId") Long tenantId,
                                       @Param("inspectionId") String inspectionId);

    @Update("""
            UPDATE cloudmold_procurement_receipt_inspection_split
            SET sampled_quantity=sampled_quantity+#{sampledQuantity},
                accepted_quantity=accepted_quantity+#{acceptedQuantity},
                rejected_quantity=rejected_quantity+#{rejectedQuantity},
                quarantined_quantity=quarantined_quantity+#{quarantinedQuantity},
                status=#{status},version=version+1,completed_at=#{completedAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND inspection_split_id=#{inspectionSplitId}
              AND version=#{expectedVersion}
            """)
    int applySplitResult(@Param("tenantId") Long tenantId,
                         @Param("inspectionSplitId") String inspectionSplitId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("sampledQuantity") java.math.BigDecimal sampledQuantity,
                         @Param("acceptedQuantity") java.math.BigDecimal acceptedQuantity,
                         @Param("rejectedQuantity") java.math.BigDecimal rejectedQuantity,
                         @Param("quarantinedQuantity") java.math.BigDecimal quarantinedQuantity,
                         @Param("status") String status,
                         @Param("completedAt") LocalDateTime completedAt,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT COALESCE(SUM(received_quantity),0) received_quantity,
                   COALESCE(SUM(sampled_quantity),0) sampled_quantity,
                   COALESCE(SUM(accepted_quantity),0) accepted_quantity,
                   COALESCE(SUM(rejected_quantity),0) rejected_quantity,
                   COALESCE(SUM(quarantined_quantity),0) quarantined_quantity
            FROM cloudmold_procurement_receipt_inspection_split
            WHERE tenant_id=#{tenantId} AND inspection_line_id=#{inspectionLineId}
            """)
    QuantityTotals selectLineTotals(@Param("tenantId") Long tenantId,
                                    @Param("inspectionLineId") String inspectionLineId);

    @Update("""
            UPDATE cloudmold_procurement_receipt_inspection_line
            SET sampled_quantity=#{totals.sampledQuantity},accepted_quantity=#{totals.acceptedQuantity},
                rejected_quantity=#{totals.rejectedQuantity},
                quarantined_quantity=#{totals.quarantinedQuantity},status=#{status},version=version+1,
                completed_at=#{completedAt},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND inspection_line_id=#{inspectionLineId}
              AND version=#{expectedVersion}
            """)
    int updateLineTotals(@Param("tenantId") Long tenantId,
                         @Param("inspectionLineId") String inspectionLineId,
                         @Param("expectedVersion") Long expectedVersion,
                         @Param("totals") QuantityTotals totals,
                         @Param("status") String status,
                         @Param("completedAt") LocalDateTime completedAt,
                         @Param("now") LocalDateTime now);

    @Select("""
            SELECT COALESCE(SUM(received_quantity),0) received_quantity,
                   COALESCE(SUM(sampled_quantity),0) sampled_quantity,
                   COALESCE(SUM(accepted_quantity),0) accepted_quantity,
                   COALESCE(SUM(rejected_quantity),0) rejected_quantity,
                   COALESCE(SUM(quarantined_quantity),0) quarantined_quantity,
                   COUNT(*) total_lines,
                   COALESCE(SUM(CASE WHEN status='COMPLETED' THEN 1 ELSE 0 END),0) completed_lines
            FROM cloudmold_procurement_receipt_inspection_line
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            """)
    QuantityTotals selectInspectionTotals(@Param("tenantId") Long tenantId,
                                          @Param("inspectionId") String inspectionId);

    @Update("""
            UPDATE cloudmold_procurement_receipt_inspection
            SET sampled_quantity=#{totals.sampledQuantity},accepted_quantity=#{totals.acceptedQuantity},
                rejected_quantity=#{totals.rejectedQuantity},
                quarantined_quantity=#{totals.quarantinedQuantity},status=#{status},
                last_decision_actor_principal_id=#{actorPrincipalId},version=version+1,
                updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
              AND version=#{expectedVersion} AND status<>'COMPLETED'
            """)
    int updateInspectionTotals(@Param("tenantId") Long tenantId,
                               @Param("inspectionId") String inspectionId,
                               @Param("expectedVersion") Long expectedVersion,
                               @Param("totals") QuantityTotals totals,
                               @Param("status") String status,
                               @Param("actorPrincipalId") String actorPrincipalId,
                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_procurement_receipt_inspection
            SET status='COMPLETED',final_decision=#{finalDecision},completed_by_principal_id=#{actorPrincipalId},
                version=version+1,
                completed_at=#{now},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
              AND version=#{expectedVersion} AND status='READY_TO_COMPLETE'
            """)
    int completeInspection(@Param("tenantId") Long tenantId,
                           @Param("inspectionId") String inspectionId,
                           @Param("expectedVersion") Long expectedVersion,
                           @Param("finalDecision") String finalDecision,
                           @Param("actorPrincipalId") String actorPrincipalId,
                           @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_procurement_receipt_inspection_result_batch
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
              AND actor_principal_id=#{actorPrincipalId}
            """)
    long countResultBatchesByActor(@Param("tenantId") Long tenantId,
                                   @Param("inspectionId") String inspectionId,
                                   @Param("actorPrincipalId") String actorPrincipalId);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_result_batch
              (result_batch_id,tenant_id,inspection_id,inspection_version_before,inspection_version_after,
               decision_version,
               actor_principal_id,operation_id,occurred_at,created_at)
            VALUES (#{resultBatchId},#{tenantId},#{inspectionId},#{inspectionVersionBefore},
                    #{inspectionVersionAfter},#{decisionVersion},#{actorPrincipalId},#{operationId},
                    #{occurredAt},#{createdAt})
            """)
    int insertResultBatch(ResultBatch value);

    @Select("""
            SELECT result_batch_id,tenant_id,inspection_id,inspection_version_before,
                   inspection_version_after,decision_version,actor_principal_id,operation_id,occurred_at,created_at
            FROM cloudmold_procurement_receipt_inspection_result_batch
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            ORDER BY inspection_version_after,result_batch_id
            """)
    List<ResultBatch> selectResultBatches(@Param("tenantId") Long tenantId,
                                         @Param("inspectionId") String inspectionId);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_result_split
              (result_split_id,quality_decision_id,decision_version,tenant_id,result_batch_id,inspection_id,inspection_line_id,
               inspection_split_id,sampled_quantity,accepted_quantity,rejected_quantity,
               quarantined_quantity,accepted_disposition_code,rejected_disposition_code,
               quarantine_disposition_code,decision_evidence_sha256,evidence_ref,
               actor_principal_id,operation_id,finance_receipt_evidence_operation_id,finance_receipt_evidence_id,
               finance_receipt_evidence_version,finance_quality_operation_id,finance_quality_evidence_id,
               finance_quality_evidence_version,accepted_inventory_operation_id,accepted_ledger_transaction_id,
               accepted_inventory_aggregate_version,
               accepted_warehouse_operation_id,accepted_warehouse_receipt_version,
               accepted_warehouse_receipt_line_version,accepted_warehouse_schedule_fulfillment_version,
               accepted_finance_inventory_operation_id,accepted_finance_inventory_evidence_id,
               accepted_finance_inventory_evidence_version,
               rejected_inventory_operation_id,rejected_ledger_transaction_id,
               rejected_inventory_aggregate_version,
               rejected_warehouse_operation_id,rejected_warehouse_receipt_version,
               rejected_warehouse_receipt_line_version,rejected_warehouse_schedule_fulfillment_version,
               rejected_finance_inventory_operation_id,rejected_finance_inventory_evidence_id,
               rejected_finance_inventory_evidence_version,
               quarantined_inventory_operation_id,quarantined_ledger_transaction_id,
               quarantined_inventory_aggregate_version,
               quarantined_warehouse_operation_id,quarantined_warehouse_receipt_version,
               quarantined_warehouse_receipt_line_version,
               quarantined_warehouse_schedule_fulfillment_version,
               quarantined_finance_inventory_operation_id,quarantined_finance_inventory_evidence_id,
               quarantined_finance_inventory_evidence_version,occurred_at,created_at)
            VALUES (#{resultSplitId},#{qualityDecisionId},#{decisionVersion},#{tenantId},#{resultBatchId},#{inspectionId},#{inspectionLineId},
                    #{inspectionSplitId},#{sampledQuantity},#{acceptedQuantity},#{rejectedQuantity},
                    #{quarantinedQuantity},#{acceptedDispositionCode},#{rejectedDispositionCode},
                    #{quarantineDispositionCode},#{decisionEvidenceSha256},#{evidenceRef},
                    #{actorPrincipalId},#{operationId},#{financeReceiptEvidenceOperationId},#{financeReceiptEvidenceId},
                    #{financeReceiptEvidenceVersion},#{financeQualityOperationId},#{financeQualityEvidenceId},
                    #{financeQualityEvidenceVersion},#{acceptedInventoryOperationId},#{acceptedLedgerTransactionId},
                    #{acceptedInventoryAggregateVersion},
                    #{acceptedWarehouseOperationId},#{acceptedWarehouseReceiptVersion},
                    #{acceptedWarehouseReceiptLineVersion},#{acceptedWarehouseScheduleFulfillmentVersion},
                    #{acceptedFinanceInventoryOperationId},#{acceptedFinanceInventoryEvidenceId},
                    #{acceptedFinanceInventoryEvidenceVersion},
                    #{rejectedInventoryOperationId},#{rejectedLedgerTransactionId},
                    #{rejectedInventoryAggregateVersion},
                    #{rejectedWarehouseOperationId},#{rejectedWarehouseReceiptVersion},
                    #{rejectedWarehouseReceiptLineVersion},#{rejectedWarehouseScheduleFulfillmentVersion},
                    #{rejectedFinanceInventoryOperationId},#{rejectedFinanceInventoryEvidenceId},
                    #{rejectedFinanceInventoryEvidenceVersion},
                    #{quarantinedInventoryOperationId},#{quarantinedLedgerTransactionId},
                    #{quarantinedInventoryAggregateVersion},
                    #{quarantinedWarehouseOperationId},#{quarantinedWarehouseReceiptVersion},
                    #{quarantinedWarehouseReceiptLineVersion},
                    #{quarantinedWarehouseScheduleFulfillmentVersion},
                    #{quarantinedFinanceInventoryOperationId},#{quarantinedFinanceInventoryEvidenceId},
                    #{quarantinedFinanceInventoryEvidenceVersion},
                    #{occurredAt},#{createdAt})
            """)
    int insertResultSplit(ResultSplit value);

    @Select("""
            SELECT result_split_id,quality_decision_id,decision_version,tenant_id,result_batch_id,inspection_id,inspection_line_id,
                   inspection_split_id,sampled_quantity,accepted_quantity,rejected_quantity,
                   quarantined_quantity,accepted_disposition_code,rejected_disposition_code,
                   quarantine_disposition_code,decision_evidence_sha256,evidence_ref,
                   actor_principal_id,operation_id,finance_receipt_evidence_operation_id,finance_receipt_evidence_id,
                   finance_receipt_evidence_version,finance_quality_operation_id,finance_quality_evidence_id,
                   finance_quality_evidence_version,accepted_inventory_operation_id,accepted_ledger_transaction_id,
                   accepted_inventory_aggregate_version,
                   accepted_warehouse_operation_id,accepted_warehouse_receipt_version,
                   accepted_warehouse_receipt_line_version,accepted_warehouse_schedule_fulfillment_version,
                   accepted_finance_inventory_operation_id,accepted_finance_inventory_evidence_id,
                   accepted_finance_inventory_evidence_version,
                   rejected_inventory_operation_id,rejected_ledger_transaction_id,
                   rejected_inventory_aggregate_version,
                   rejected_warehouse_operation_id,rejected_warehouse_receipt_version,
                   rejected_warehouse_receipt_line_version,rejected_warehouse_schedule_fulfillment_version,
                   rejected_finance_inventory_operation_id,rejected_finance_inventory_evidence_id,
                   rejected_finance_inventory_evidence_version,
                   quarantined_inventory_operation_id,quarantined_ledger_transaction_id,
                   quarantined_inventory_aggregate_version,
                   quarantined_warehouse_operation_id,quarantined_warehouse_receipt_version,
                   quarantined_warehouse_receipt_line_version,
                   quarantined_warehouse_schedule_fulfillment_version,
                   quarantined_finance_inventory_operation_id,quarantined_finance_inventory_evidence_id,
                   quarantined_finance_inventory_evidence_version,occurred_at,created_at
            FROM cloudmold_procurement_receipt_inspection_result_split
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            ORDER BY occurred_at,result_split_id
            """)
    List<ResultSplit> selectResultSplits(@Param("tenantId") Long tenantId,
                                        @Param("inspectionId") String inspectionId);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_defect
              (defect_id,tenant_id,result_split_id,inspection_id,inspection_line_id,inspection_split_id,
               defect_code,defect_category,severity,affected_quantity,evidence_sha256,evidence_ref,created_at)
            VALUES (#{defectId},#{tenantId},#{resultSplitId},#{inspectionId},#{inspectionLineId},
                    #{inspectionSplitId},#{defectCode},#{defectCategory},#{severity},#{affectedQuantity},
                    #{evidenceSha256},#{evidenceRef},#{createdAt})
            """)
    int insertDefect(Defect value);

    @Select("""
            SELECT defect_id,tenant_id,result_split_id,inspection_id,inspection_line_id,inspection_split_id,
                   defect_code,defect_category,severity,affected_quantity,evidence_sha256,evidence_ref,created_at
            FROM cloudmold_procurement_receipt_inspection_defect
            WHERE tenant_id=#{tenantId} AND inspection_id=#{inspectionId}
            ORDER BY result_split_id,severity,defect_id
            """)
    List<Defect> selectDefects(@Param("tenantId") Long tenantId,
                               @Param("inspectionId") String inspectionId);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_history
              (tenant_id,inspection_id,inspection_version,previous_status,current_status,final_decision,
               actor_principal_id,operation_id,occurred_at,created_at)
            VALUES (#{tenantId},#{inspectionId},#{inspectionVersion},#{previousStatus},#{currentStatus},
                    #{finalDecision},#{actorPrincipalId},#{operationId},#{occurredAt},#{createdAt})
            """)
    int insertInspectionHistory(InspectionHistory value);

    @Insert("""
            INSERT INTO cloudmold_procurement_receipt_inspection_line_history
              (tenant_id,inspection_line_id,line_version,previous_status,current_status,sampled_quantity,
               accepted_quantity,rejected_quantity,quarantined_quantity,actor_principal_id,
               operation_id,occurred_at,created_at)
            VALUES (#{tenantId},#{inspectionLineId},#{lineVersion},#{previousStatus},#{currentStatus},
                    #{sampledQuantity},#{acceptedQuantity},#{rejectedQuantity},#{quarantinedQuantity},
                    #{actorPrincipalId},#{operationId},#{occurredAt},#{createdAt})
            """)
    int insertLineHistory(LineHistory value);
}
