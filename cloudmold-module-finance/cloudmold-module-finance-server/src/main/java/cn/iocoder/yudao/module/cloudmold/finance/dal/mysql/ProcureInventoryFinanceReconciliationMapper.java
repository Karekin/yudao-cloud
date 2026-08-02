package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureInventoryFinanceReconciliationRecords.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcureInventoryFinanceReconciliationMapper {
    @Insert("""
            INSERT INTO cloudmold_finance_procure_inventory_reconciliation_run
              (run_id,tenant_id,run_code,legal_entity_id,currency_code,reconciliation_policy_version,status,
               line_count,matched_count,different_count,missing_count,uncomparable_count,requested_by_principal_id,
               started_at,completed_at,created_at)
            VALUES (#{runId},#{tenantId},#{runCode},#{legalEntityId},#{currencyCode},
                    #{reconciliationPolicyVersion},#{status},#{lineCount},#{matchedCount},#{differentCount},
                    #{missingCount},#{uncomparableCount},#{requestedByPrincipalId},#{startedAt},#{completedAt},
                    #{createdAt})
            """)
    int insertRun(ReconciliationRun value);

    @Insert("""
            <script>
            INSERT INTO cloudmold_finance_procure_inventory_reconciliation_watermark
              (watermark_id,tenant_id,run_id,domain_code,source_table,max_aggregate_version,max_observed_at,record_count,created_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.watermarkId},#{value.tenantId},#{value.runId},#{value.domainCode},#{value.sourceTable},
               #{value.maxAggregateVersion},#{value.maxObservedAt},#{value.recordCount},#{value.createdAt})
            </foreach>
            </script>
            """)
    int insertWatermarks(@Param("values") List<ReconciliationWatermark> values);

    @Insert("""
            <script>
            INSERT INTO cloudmold_finance_procure_inventory_reconciliation_line
              (line_id,tenant_id,run_id,line_type,line_key,legal_entity_id,currency_code,purchase_order_id,
               purchase_order_item_id,delivery_schedule_id,receipt_line_id,inventory_movement_id,supplier_return_id,
               supplier_return_line_id,supplier_invoice_id,supplier_invoice_line_id,ap_open_item_id,journal_entry_id,
               procurement_version,inventory_version,finance_version,procurement_quantity,inventory_quantity,
               finance_quantity,procurement_amount_minor,inventory_amount_minor,finance_amount_minor,
               quantity_difference,amount_difference_minor,primary_difference_code,responsibility_domain,
               match_status,difference_count,created_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.lineId},#{value.tenantId},#{value.runId},#{value.lineType},#{value.lineKey},#{value.legalEntityId},
               #{value.currencyCode},#{value.purchaseOrderId},#{value.purchaseOrderItemId},#{value.deliveryScheduleId},
               #{value.receiptLineId},#{value.inventoryMovementId},#{value.supplierReturnId},#{value.supplierReturnLineId},
               #{value.supplierInvoiceId},#{value.supplierInvoiceLineId},#{value.apOpenItemId},#{value.journalEntryId},
               #{value.procurementVersion},#{value.inventoryVersion},#{value.financeVersion},#{value.procurementQuantity},
               #{value.inventoryQuantity},#{value.financeQuantity},#{value.procurementAmountMinor},
               #{value.inventoryAmountMinor},#{value.financeAmountMinor},#{value.quantityDifference},
               #{value.amountDifferenceMinor},#{value.primaryDifferenceCode},#{value.responsibilityDomain},
               #{value.matchStatus},#{value.differenceCount},#{value.createdAt})
            </foreach>
            </script>
            """)
    int insertLines(@Param("values") List<ReconciliationLine> values);

    @Insert("""
            <script>
            INSERT INTO cloudmold_finance_procure_inventory_reconciliation_difference
              (difference_id,tenant_id,run_id,line_id,difference_code,source_domain,expected_value,actual_value,blocking,created_at)
            VALUES
            <foreach collection="values" item="value" separator=",">
              (#{value.differenceId},#{value.tenantId},#{value.runId},#{value.lineId},#{value.differenceCode},
               #{value.sourceDomain},#{value.expectedValue},#{value.actualValue},#{value.blocking},#{value.createdAt})
            </foreach>
            </script>
            """)
    int insertDifferences(@Param("values") List<ReconciliationDifference> values);

    @Select("""
            SELECT run_id,tenant_id,run_code,legal_entity_id,currency_code,reconciliation_policy_version,status,
                   line_count,matched_count,different_count,missing_count,uncomparable_count,requested_by_principal_id,
                   started_at,completed_at,created_at
              FROM cloudmold_finance_procure_inventory_reconciliation_run
             WHERE tenant_id=#{tenantId} AND run_id=#{runId}
            """)
    ReconciliationRun selectRun(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT COUNT(*)
              FROM cloudmold_finance_procure_inventory_reconciliation_run
             WHERE tenant_id=#{tenantId}
               AND (#{status} IS NULL OR status=#{status})
               AND (#{legalEntityId} IS NULL OR legal_entity_id=#{legalEntityId})
               AND (#{currencyCode} IS NULL OR currency_code=#{currencyCode})
               AND (#{keyword} IS NULL OR #{keyword}=''
                    OR run_code LIKE CONCAT('%',#{keyword},'%')
                    OR run_id LIKE CONCAT('%',#{keyword},'%')
                    OR legal_entity_id LIKE CONCAT('%',#{keyword},'%'))
            """)
    long countRunPage(@Param("tenantId") Long tenantId, @Param("status") String status,
                      @Param("legalEntityId") String legalEntityId, @Param("currencyCode") String currencyCode,
                      @Param("keyword") String keyword);

    @Select("""
            SELECT run_id,tenant_id,run_code,legal_entity_id,currency_code,reconciliation_policy_version,status,
                   line_count,matched_count,different_count,missing_count,uncomparable_count,requested_by_principal_id,
                   started_at,completed_at,created_at
              FROM cloudmold_finance_procure_inventory_reconciliation_run
             WHERE tenant_id=#{tenantId}
               AND (#{status} IS NULL OR status=#{status})
               AND (#{legalEntityId} IS NULL OR legal_entity_id=#{legalEntityId})
               AND (#{currencyCode} IS NULL OR currency_code=#{currencyCode})
               AND (#{keyword} IS NULL OR #{keyword}=''
                    OR run_code LIKE CONCAT('%',#{keyword},'%')
                    OR run_id LIKE CONCAT('%',#{keyword},'%')
                    OR legal_entity_id LIKE CONCAT('%',#{keyword},'%'))
             ORDER BY created_at DESC,run_id DESC
             LIMIT #{offset},#{size}
            """)
    List<ReconciliationRun> selectRunPage(@Param("tenantId") Long tenantId, @Param("status") String status,
                                          @Param("legalEntityId") String legalEntityId,
                                          @Param("currencyCode") String currencyCode,
                                          @Param("keyword") String keyword,
                                          @Param("offset") long offset, @Param("size") int size);

    @Select("""
            SELECT watermark_id,tenant_id,run_id,domain_code,source_table,max_aggregate_version,max_observed_at,record_count,created_at
              FROM cloudmold_finance_procure_inventory_reconciliation_watermark
             WHERE tenant_id=#{tenantId} AND run_id=#{runId}
             ORDER BY domain_code,source_table
            """)
    List<ReconciliationWatermark> selectRunWatermarks(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT COUNT(*)
              FROM cloudmold_finance_procure_inventory_reconciliation_line
             WHERE tenant_id=#{tenantId} AND run_id=#{runId}
               AND (#{matchStatus} IS NULL OR match_status=#{matchStatus})
               AND (#{lineType} IS NULL OR line_type=#{lineType})
               AND (#{keyword} IS NULL OR #{keyword}=''
                    OR line_key LIKE CONCAT('%',#{keyword},'%')
                    OR purchase_order_id LIKE CONCAT('%',#{keyword},'%')
                    OR purchase_order_item_id LIKE CONCAT('%',#{keyword},'%')
                    OR receipt_line_id LIKE CONCAT('%',#{keyword},'%')
                    OR inventory_movement_id LIKE CONCAT('%',#{keyword},'%')
                    OR supplier_return_line_id LIKE CONCAT('%',#{keyword},'%')
                    OR supplier_invoice_line_id LIKE CONCAT('%',#{keyword},'%')
                    OR journal_entry_id LIKE CONCAT('%',#{keyword},'%'))
            """)
    long countLinePage(@Param("tenantId") Long tenantId, @Param("runId") String runId,
                       @Param("matchStatus") String matchStatus, @Param("lineType") String lineType,
                       @Param("keyword") String keyword);

    @Select("""
            SELECT line_id,tenant_id,run_id,line_type,line_key,legal_entity_id,currency_code,purchase_order_id,purchase_order_item_id,
                   delivery_schedule_id,receipt_line_id,inventory_movement_id,supplier_return_id,supplier_return_line_id,
                   supplier_invoice_id,supplier_invoice_line_id,ap_open_item_id,journal_entry_id,procurement_version,
                   inventory_version,finance_version,procurement_quantity,inventory_quantity,finance_quantity,
                   procurement_amount_minor,inventory_amount_minor,finance_amount_minor,quantity_difference,
                   amount_difference_minor,primary_difference_code,responsibility_domain,match_status,difference_count,created_at
              FROM cloudmold_finance_procure_inventory_reconciliation_line
             WHERE tenant_id=#{tenantId} AND run_id=#{runId}
               AND (#{matchStatus} IS NULL OR match_status=#{matchStatus})
               AND (#{lineType} IS NULL OR line_type=#{lineType})
               AND (#{keyword} IS NULL OR #{keyword}=''
                    OR line_key LIKE CONCAT('%',#{keyword},'%')
                    OR purchase_order_id LIKE CONCAT('%',#{keyword},'%')
                    OR purchase_order_item_id LIKE CONCAT('%',#{keyword},'%')
                    OR receipt_line_id LIKE CONCAT('%',#{keyword},'%')
                    OR inventory_movement_id LIKE CONCAT('%',#{keyword},'%')
                    OR supplier_return_line_id LIKE CONCAT('%',#{keyword},'%')
                    OR supplier_invoice_line_id LIKE CONCAT('%',#{keyword},'%')
                    OR journal_entry_id LIKE CONCAT('%',#{keyword},'%'))
             ORDER BY line_type,line_key
             LIMIT #{offset},#{size}
            """)
    List<ReconciliationLine> selectLinePage(@Param("tenantId") Long tenantId, @Param("runId") String runId,
                                            @Param("matchStatus") String matchStatus,
                                            @Param("lineType") String lineType,
                                            @Param("keyword") String keyword,
                                            @Param("offset") long offset, @Param("size") int size);

    @Select("""
            SELECT line_id,tenant_id,run_id,line_type,line_key,legal_entity_id,currency_code,purchase_order_id,purchase_order_item_id,
                   delivery_schedule_id,receipt_line_id,inventory_movement_id,supplier_return_id,supplier_return_line_id,
                   supplier_invoice_id,supplier_invoice_line_id,ap_open_item_id,journal_entry_id,procurement_version,
                   inventory_version,finance_version,procurement_quantity,inventory_quantity,finance_quantity,
                   procurement_amount_minor,inventory_amount_minor,finance_amount_minor,quantity_difference,
                   amount_difference_minor,primary_difference_code,responsibility_domain,match_status,difference_count,created_at
              FROM cloudmold_finance_procure_inventory_reconciliation_line
             WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND line_id=#{lineId}
            """)
    ReconciliationLine selectLine(@Param("tenantId") Long tenantId, @Param("runId") String runId,
                                  @Param("lineId") String lineId);

    @Select("""
            SELECT difference_id,tenant_id,run_id,line_id,difference_code,source_domain,expected_value,actual_value,blocking,created_at
              FROM cloudmold_finance_procure_inventory_reconciliation_difference
             WHERE tenant_id=#{tenantId} AND run_id=#{runId} AND line_id=#{lineId}
             ORDER BY difference_code,difference_id
            """)
    List<ReconciliationDifference> selectLineDifferences(@Param("tenantId") Long tenantId,
                                                         @Param("runId") String runId,
                                                         @Param("lineId") String lineId);

    @Select("""
            SELECT po.legal_entity_id,po.currency_code,po.purchase_order_id,po.purchase_order_item_id,po.delivery_schedule_id,
                   po.source_version purchase_order_line_version,re.receipt_line_id,re.source_version receipt_line_version,
                   qe.quality_disposition_id,qe.source_version quality_disposition_version,
                   ie.inventory_movement_id,ie.source_version inventory_movement_version,
                   re.received_quantity,qe.accepted_quantity,ie.movement_quantity,ie.movement_cost_amount_minor,
                   layer.valuation_layer_id,layer.version valuation_layer_version,layer.total_cost_amount_minor valuation_amount_minor,
                   effect.journal_entry_id,j.version journal_version,effect.amount_minor journal_amount_minor
              FROM cloudmold_finance_inventory_movement_evidence ie
              JOIN (SELECT tenant_id,inventory_movement_id,MAX(source_version) source_version
                      FROM cloudmold_finance_inventory_movement_evidence
                     GROUP BY tenant_id,inventory_movement_id) latest_ie
                ON latest_ie.tenant_id=ie.tenant_id AND latest_ie.inventory_movement_id=ie.inventory_movement_id
               AND latest_ie.source_version=ie.source_version
              JOIN cloudmold_finance_quality_disposition_evidence qe
                ON qe.tenant_id=ie.tenant_id AND qe.quality_disposition_id=ie.quality_disposition_id
               AND qe.source_version=ie.quality_disposition_version
              JOIN cloudmold_finance_receipt_line_evidence re
                ON re.tenant_id=ie.tenant_id AND re.receipt_line_id=ie.receipt_line_id
               AND re.source_version=qe.receipt_line_version
              JOIN cloudmold_finance_po_line_evidence po
                ON po.tenant_id=ie.tenant_id AND po.purchase_order_item_id=ie.purchase_order_item_id
               AND po.source_version=re.purchase_order_line_version
              LEFT JOIN cloudmold_finance_inventory_valuation_layer layer
                ON layer.tenant_id=ie.tenant_id AND layer.inventory_movement_id=ie.inventory_movement_id
               AND layer.inventory_movement_version=ie.source_version
              LEFT JOIN cloudmold_finance_inventory_valuation_effect effect
                ON effect.tenant_id=ie.tenant_id AND effect.inventory_movement_id=ie.inventory_movement_id
               AND effect.inventory_movement_version=ie.source_version AND effect.effect_type='QUALIFIED_RECEIPT'
              LEFT JOIN cloudmold_finance_journal_entry j
                ON j.tenant_id=effect.tenant_id AND j.journal_entry_id=effect.journal_entry_id
             WHERE ie.tenant_id=#{tenantId} AND ie.disposition='ACCEPTED'
               AND po.legal_entity_id=#{legalEntityId} AND po.currency_code=#{currencyCode}
             ORDER BY po.purchase_order_item_id,re.receipt_line_id,ie.inventory_movement_id
            """)
    List<QualifiedReceiptSource> selectQualifiedReceiptSources(@Param("tenantId") Long tenantId,
                                                               @Param("legalEntityId") String legalEntityId,
                                                               @Param("currencyCode") String currencyCode);

    @Select("""
            SELECT po.legal_entity_id,po.currency_code,po.purchase_order_id,po.purchase_order_item_id,
                   line.purchase_order_schedule_id delivery_schedule_id,line.receipt_line_id,line.quality_decision_id,
                   line.decision_version quality_decision_version,line.return_id supplier_return_id,
                   line.return_line_id supplier_return_line_id,line.version return_line_version,
                   dispatch.execution_line_id,dispatch.version execution_line_version,
                   dispatch.ledger_transaction_id inventory_ledger_transaction_id,
                   dispatch.inventory_aggregate_version,line.return_quantity,dispatch.dispatched_quantity,
                   CASE
                     WHEN dispatch.execution_line_id IS NULL THEN NULL
                     ELSE CAST(ROUND(line.unit_cost_amount_minor * dispatch.dispatched_quantity,0) AS SIGNED)
                   END movement_cost_amount_minor
              FROM cloudmold_supplier_return_line line
              JOIN (SELECT tenant_id,purchase_order_item_id,MAX(source_version) source_version
                      FROM cloudmold_finance_po_line_evidence
                     GROUP BY tenant_id,purchase_order_item_id) latest_po
                ON latest_po.tenant_id=line.tenant_id AND latest_po.purchase_order_item_id=line.purchase_order_item_id
              JOIN cloudmold_finance_po_line_evidence po
                ON po.tenant_id=latest_po.tenant_id AND po.purchase_order_item_id=latest_po.purchase_order_item_id
               AND po.source_version=latest_po.source_version
              LEFT JOIN cloudmold_supplier_return_dispatch_line dispatch
                ON dispatch.tenant_id=line.tenant_id AND dispatch.return_line_id=line.return_line_id
             WHERE line.tenant_id=#{tenantId}
               AND line.status<>'CANCELLED'
               AND po.legal_entity_id=#{legalEntityId} AND po.currency_code=#{currencyCode}
             ORDER BY line.return_line_id,dispatch.execution_line_id
            """)
    List<SupplierReturnSource> selectSupplierReturnSources(@Param("tenantId") Long tenantId,
                                                           @Param("legalEntityId") String legalEntityId,
                                                           @Param("currencyCode") String currencyCode);

    @Select("""
            SELECT i.legal_entity_id,i.currency_code,l.purchase_order_id,l.purchase_order_item_id,
                   ml.purchase_order_line_version,l.supplier_invoice_id,l.invoice_line_id supplier_invoice_line_id,
                   l.version invoice_line_version,ap.ap_open_item_id,ap.version ap_open_item_version,
                   i.posted_journal_entry_id journal_entry_id,j.version journal_version,
                   CASE WHEN COUNT(DISTINCT a.inventory_movement_id)=1 THEN MIN(a.inventory_movement_id) ELSE NULL END inventory_movement_id,
                   CASE WHEN COUNT(DISTINCT a.inventory_movement_version)=1 THEN MIN(a.inventory_movement_version) ELSE NULL END inventory_movement_version,
                   l.quantity invoice_quantity,COALESCE(SUM(a.allocated_quantity),0) allocated_quantity,
                   l.gross_amount_minor invoice_gross_amount_minor,
                   COALESCE(SUM(
                       CASE
                         WHEN ie.movement_quantity IS NULL OR ie.movement_quantity=0 THEN 0
                         ELSE CAST(ROUND((ie.movement_cost_amount_minor * a.allocated_quantity) / ie.movement_quantity,0) AS SIGNED)
                       END
                   ),0) matched_inventory_amount_minor,
                   ap.original_amount_minor ap_open_amount_minor,
                   CASE WHEN j.journal_entry_id IS NULL THEN NULL ELSE j.debit_total_minor END journal_amount_minor,
                   COALESCE(ml.result_status,'NOT_MATCHED') match_result_status,
                   SUM(CASE WHEN e.status='OPEN' THEN 1 ELSE 0 END) open_exception_count
              FROM cloudmold_finance_supplier_invoice_line l
              JOIN cloudmold_finance_supplier_invoice i
                ON i.tenant_id=l.tenant_id AND i.supplier_invoice_id=l.supplier_invoice_id
              LEFT JOIN (
                    SELECT r1.*
                      FROM cloudmold_finance_invoice_match_run r1
                      JOIN (
                            SELECT tenant_id,supplier_invoice_id,MAX(created_at) created_at
                              FROM cloudmold_finance_invoice_match_run
                             WHERE status IN ('MATCHED','EXCEPTION')
                             GROUP BY tenant_id,supplier_invoice_id
                      ) latest_run
                        ON latest_run.tenant_id=r1.tenant_id AND latest_run.supplier_invoice_id=r1.supplier_invoice_id
                       AND latest_run.created_at=r1.created_at
              ) run
                ON run.tenant_id=l.tenant_id AND run.supplier_invoice_id=l.supplier_invoice_id
              LEFT JOIN cloudmold_finance_invoice_match_line ml
                ON ml.tenant_id=l.tenant_id AND ml.match_run_id=run.match_run_id AND ml.invoice_line_id=l.invoice_line_id
              LEFT JOIN cloudmold_finance_invoice_match_receipt_allocation a
                ON a.tenant_id=l.tenant_id AND a.match_line_id=ml.match_line_id AND a.status IN ('ACTIVE','PENDING_OVERRIDE')
              LEFT JOIN cloudmold_finance_inventory_movement_evidence ie
                ON ie.tenant_id=a.tenant_id AND ie.inventory_movement_id=a.inventory_movement_id
               AND ie.source_version=a.inventory_movement_version
              LEFT JOIN cloudmold_finance_invoice_match_exception e
                ON e.tenant_id=l.tenant_id AND e.match_line_id=ml.match_line_id AND e.status='OPEN'
              LEFT JOIN cloudmold_finance_ap_open_item ap
                ON ap.tenant_id=i.tenant_id AND ap.supplier_invoice_id=i.supplier_invoice_id
              LEFT JOIN cloudmold_finance_journal_entry j
                ON j.tenant_id=i.tenant_id AND j.journal_entry_id=i.posted_journal_entry_id
             WHERE l.tenant_id=#{tenantId}
               AND i.legal_entity_id=#{legalEntityId} AND i.currency_code=#{currencyCode}
             GROUP BY i.legal_entity_id,i.currency_code,l.purchase_order_id,l.purchase_order_item_id,
                      ml.purchase_order_line_version,l.supplier_invoice_id,l.invoice_line_id,l.version,ap.ap_open_item_id,
                      ap.version,i.posted_journal_entry_id,j.version,l.quantity,l.gross_amount_minor,
                      ap.original_amount_minor,j.journal_entry_id,j.debit_total_minor,ml.result_status
             ORDER BY l.supplier_invoice_id,l.line_number
            """)
    List<SupplierInvoiceSource> selectSupplierInvoiceSources(@Param("tenantId") Long tenantId,
                                                             @Param("legalEntityId") String legalEntityId,
                                                             @Param("currencyCode") String currencyCode);
}
