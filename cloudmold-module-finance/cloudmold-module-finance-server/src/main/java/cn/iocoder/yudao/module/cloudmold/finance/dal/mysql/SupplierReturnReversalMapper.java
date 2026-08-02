package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.SupplierReturnReversalRecords.*;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupplierReturnReversalMapper {

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_return_reversal_operation
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
                   aggregate_id,result_json
            FROM cloudmold_finance_supplier_return_reversal_operation
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId}
            FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("tenantId") Long tenantId,
                                       @Param("operationId") Long operationId);

    @Update("""
            UPDATE cloudmold_finance_supplier_return_reversal_operation
            SET status=10,aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("tenantId") Long tenantId,
                               @Param("operationId") Long operationId,
                               @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson,
                               @Param("now") LocalDateTime now);

    @Select("""
            SELECT return_id,tenant_id,return_code,purchase_order_id,receipt_id,supplier_id,owner_type,owner_id,
                   warehouse_id,status,version,reason_code,remark
            FROM cloudmold_supplier_return
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            """)
    WarehouseReturn selectWarehouseReturn(@Param("tenantId") Long tenantId,
                                          @Param("returnId") String returnId);

    @Select("""
            SELECT return_line_id,tenant_id,return_id,line_number,receipt_line_id,purchase_order_item_id,
                   purchase_order_schedule_id,quality_decision_id,decision_version,inspection_split_id,source_disposition,
                   canonical_sku_id,warehouse_id,location_id,lot_id,return_quantity,dispatched_quantity,outstanding_quantity,
                   uom_code,valuation_policy_id,valuation_policy_version,valuation_policy_hash,unit_cost_amount_minor,
                   currency_code,quality_evidence_ref,status
            FROM cloudmold_supplier_return_line
            WHERE tenant_id=#{tenantId} AND return_id=#{returnId}
            ORDER BY line_number,return_line_id
            """)
    List<WarehouseReturnLine> selectWarehouseReturnLines(@Param("tenantId") Long tenantId,
                                                         @Param("returnId") String returnId);

    @Select("""
            SELECT po_line_evidence_id,purchase_order_id,purchase_order_item_id,delivery_schedule_id,legal_entity_id,
                   supplier_id,currency_code,ordered_quantity,unit_of_measure,unit_net_price,net_amount_minor,
                   tax_amount_minor,gross_amount_minor,source_version,created_at
            FROM cloudmold_finance_po_line_evidence
            WHERE tenant_id=#{tenantId} AND purchase_order_item_id=#{purchaseOrderItemId}
            ORDER BY source_version DESC LIMIT 1
            """)
    PurchaseOrderLineEvidence selectLatestPoEvidence(@Param("tenantId") Long tenantId,
                                                     @Param("purchaseOrderItemId") String purchaseOrderItemId);

    @Select("""
            SELECT receipt_line_evidence_id,tenant_id,inbox_id,receipt_id,receipt_line_id,purchase_order_id,
                   purchase_order_item_id,purchase_order_line_version,delivery_schedule_id,received_quantity,
                   unit_of_measure,source_version,created_at
            FROM cloudmold_finance_receipt_line_evidence
            WHERE tenant_id=#{tenantId} AND receipt_line_id=#{receiptLineId}
            ORDER BY source_version DESC LIMIT 1
            """)
    ReceiptLineEvidence selectLatestReceiptEvidence(@Param("tenantId") Long tenantId,
                                                    @Param("receiptLineId") String receiptLineId);

    @Select("""
            SELECT quality_evidence_id,tenant_id,inbox_id,quality_disposition_id,receipt_line_id,receipt_line_version,
                   purchase_order_item_id,inspected_quantity,accepted_quantity,rejected_quantity,held_quantity,
                   unit_of_measure,source_version,created_at
            FROM cloudmold_finance_quality_disposition_evidence
            WHERE tenant_id=#{tenantId} AND quality_disposition_id=#{qualityDispositionId}
              AND source_version=#{qualityDispositionVersion}
            """)
    QualityDispositionEvidence selectQualityEvidence(@Param("tenantId") Long tenantId,
                                                     @Param("qualityDispositionId") String qualityDispositionId,
                                                     @Param("qualityDispositionVersion") Long qualityDispositionVersion);

    @Select("""
            SELECT valuation_layer_id,tenant_id,ledger_id,inventory_movement_id,inventory_movement_version,receipt_line_id,
                   quality_disposition_id,purchase_order_item_id,valuation_policy_id,valuation_policy_version,quantity,
                   unit_of_measure,unit_cost_amount_minor,total_cost_amount_minor,currency_code,remaining_quantity,
                   remaining_cost_amount_minor,status,version,created_at,updated_at
            FROM cloudmold_finance_inventory_valuation_layer
            WHERE tenant_id=#{tenantId} AND ledger_id=#{ledgerId} AND receipt_line_id=#{receiptLineId}
              AND quality_disposition_id=#{qualityDispositionId} AND purchase_order_item_id=#{purchaseOrderItemId}
            FOR UPDATE
            """)
    InventoryValuationLayer selectValuationLayerForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("ledgerId") String ledgerId,
                                                          @Param("receiptLineId") String receiptLineId,
                                                          @Param("qualityDispositionId") String qualityDispositionId,
                                                          @Param("purchaseOrderItemId") String purchaseOrderItemId);

    @Select("""
            SELECT r.supplier_invoice_id,al.invoice_line_id,ap.ap_open_item_id,i.legal_entity_id,i.supplier_id,
                   i.currency_code,al.quantity invoice_line_quantity,al.net_amount_minor invoice_line_net_amount_minor,
                   al.tax_amount_minor invoice_line_tax_amount_minor,al.gross_amount_minor invoice_line_gross_amount_minor,
                   a.allocated_quantity,a.unit_of_measure,ap.open_amount_minor available_open_amount_minor
            FROM cloudmold_finance_invoice_match_receipt_allocation a
            JOIN cloudmold_finance_invoice_match_run r
              ON r.tenant_id=a.tenant_id AND r.match_run_id=a.match_run_id AND r.status='MATCHED'
            JOIN cloudmold_finance_supplier_invoice i
              ON i.tenant_id=r.tenant_id AND i.supplier_invoice_id=r.supplier_invoice_id AND i.lifecycle_status='POSTED'
            JOIN cloudmold_finance_supplier_invoice_line al
              ON al.tenant_id=i.tenant_id AND al.supplier_invoice_id=i.supplier_invoice_id
             AND al.invoice_line_id=a.invoice_line_id
            JOIN cloudmold_finance_ap_open_item ap
              ON ap.tenant_id=i.tenant_id AND ap.supplier_invoice_id=i.supplier_invoice_id AND ap.status IN ('OPEN','PARTIALLY_SETTLED')
            WHERE a.tenant_id=#{tenantId} AND a.receipt_line_id=#{receiptLineId}
              AND a.quality_disposition_id=#{qualityDispositionId}
              AND a.status='ACTIVE'
            ORDER BY a.match_allocation_id
            """)
    List<SupplierReturnAllocationCandidate> selectActiveAllocationCandidates(@Param("tenantId") Long tenantId,
                                                                             @Param("receiptLineId") String receiptLineId,
                                                                             @Param("qualityDispositionId") String qualityDispositionId);

    @Select("""
            SELECT period_id,tenant_id,period_code,period_start,period_end,currency_code,status,
                   opened_by_principal_id,closed_by_principal_id,close_evidence_sha256,reason_code,
                   version,opened_at,closed_at,created_at,updated_at
            FROM cloudmold_finance_accounting_period
            WHERE tenant_id=#{tenantId} AND period_id=#{periodId}
            FOR UPDATE
            """)
    AccountingPeriod selectPeriodForUpdate(@Param("tenantId") Long tenantId,
                                           @Param("periodId") String periodId);

    @Select("""
            SELECT p.account_role,p.ledger_id,p.account_id,a.account_code
            FROM cloudmold_finance_posting_rule_line p
            JOIN cloudmold_finance_posting_rule r
              ON r.tenant_id=p.tenant_id AND r.posting_rule_id=p.posting_rule_id
             AND r.rule_version=p.rule_version AND r.ledger_id=p.ledger_id
            JOIN cloudmold_finance_account a
              ON a.tenant_id=p.tenant_id AND a.ledger_id=p.ledger_id AND a.account_id=p.account_id
            WHERE p.tenant_id=#{tenantId} AND p.posting_rule_id=#{ruleId}
              AND p.rule_version=#{ruleVersion} AND p.ledger_id=#{ledgerId}
              AND r.source_type=#{sourceType} AND r.status='ACTIVE'
            ORDER BY p.account_role
            """)
    List<PostingAccount> selectPostingAccounts(@Param("tenantId") Long tenantId,
                                               @Param("ruleId") String ruleId,
                                               @Param("ruleVersion") Long ruleVersion,
                                               @Param("ledgerId") String ledgerId,
                                               @Param("sourceType") String sourceType);

    @Select("""
            SELECT COUNT(*)
            FROM cloudmold_finance_dimension_value v
            JOIN cloudmold_finance_dimension_type t
              ON t.tenant_id=v.tenant_id AND t.dimension_type_id=v.dimension_type_id
            WHERE v.tenant_id=#{tenantId} AND v.dimension_type_id=#{dimensionTypeId}
              AND v.dimension_value_id=#{dimensionValueId}
              AND v.status='ACTIVE' AND t.status='ACTIVE'
            """)
    int countActiveDimensionValue(@Param("tenantId") Long tenantId,
                                  @Param("dimensionTypeId") String dimensionTypeId,
                                  @Param("dimensionValueId") String dimensionValueId);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_debit_adjustment
              (supplier_debit_adjustment_id,tenant_id,adjustment_code,supplier_return_id,supplier_return_version,
               legal_entity_id,ledger_id,accounting_period_id,accounting_date,supplier_id,currency_code,
               ap_reversal_amount_minor,tax_reversal_amount_minor,valuation_reversal_amount_minor,
               purchase_price_variance_amount_minor,posting_rule_id,posting_rule_version,journal_entry_id,
               reversal_evidence_sha256,reason_code,status,posted_by_principal_id,posted_at,version,created_at,updated_at)
            VALUES
              (#{supplierDebitAdjustmentId},#{tenantId},#{adjustmentCode},#{supplierReturnId},#{supplierReturnVersion},
               #{legalEntityId},#{ledgerId},#{accountingPeriodId},#{accountingDate},#{supplierId},#{currencyCode},
               #{apReversalAmountMinor},#{taxReversalAmountMinor},#{valuationReversalAmountMinor},
               #{purchasePriceVarianceAmountMinor},#{postingRuleId},#{postingRuleVersion},#{journalEntryId},
               #{reversalEvidenceSha256},#{reasonCode},#{status},#{postedByPrincipalId},#{postedAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertSupplierDebitAdjustment(SupplierDebitAdjustment value);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_debit_adjustment_line
              (adjustment_line_id,tenant_id,supplier_debit_adjustment_id,supplier_return_line_id,line_number,
               receipt_line_id,quality_disposition_id,quality_disposition_version,purchase_order_id,purchase_order_item_id,
               purchase_order_schedule_id,valuation_layer_id,canonical_sku_id,reversal_quantity,unit_of_measure,
               unit_cost_amount_minor,valuation_reversal_amount_minor,net_reversal_amount_minor,tax_reversal_amount_minor,
               gross_reversal_amount_minor,purchase_price_variance_amount_minor,currency_code,created_at)
            VALUES
              (#{adjustmentLineId},#{tenantId},#{supplierDebitAdjustmentId},#{supplierReturnLineId},#{lineNumber},
               #{receiptLineId},#{qualityDispositionId},#{qualityDispositionVersion},#{purchaseOrderId},#{purchaseOrderItemId},
               #{purchaseOrderScheduleId},#{valuationLayerId},#{canonicalSkuId},#{reversalQuantity},#{unitOfMeasure},
               #{unitCostAmountMinor},#{valuationReversalAmountMinor},#{netReversalAmountMinor},#{taxReversalAmountMinor},
               #{grossReversalAmountMinor},#{purchasePriceVarianceAmountMinor},#{currencyCode},#{createdAt})
            """)
    int insertSupplierDebitAdjustmentLine(SupplierDebitAdjustmentLine value);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_return_ap_reversal
              (ap_reversal_id,tenant_id,supplier_debit_adjustment_id,supplier_return_line_id,supplier_invoice_id,
               invoice_line_id,ap_open_item_id,reversal_quantity,unit_of_measure,net_reversal_amount_minor,
               tax_reversal_amount_minor,gross_reversal_amount_minor,journal_entry_version,created_at)
            VALUES
              (#{apReversalId},#{tenantId},#{supplierDebitAdjustmentId},#{supplierReturnLineId},#{supplierInvoiceId},
               #{invoiceLineId},#{apOpenItemId},#{reversalQuantity},#{unitOfMeasure},#{netReversalAmountMinor},
               #{taxReversalAmountMinor},#{grossReversalAmountMinor},#{journalEntryVersion},#{createdAt})
            """)
    int insertSupplierReturnApReversal(SupplierReturnApReversal value);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_entry
              (journal_entry_id,tenant_id,journal_code,legal_entity_id,ledger_id,period_id,accounting_date,
               source_type,source_id,currency_code,document_currency_code,debit_total_minor,credit_total_minor,
               evidence_sha256,status,prepared_by_principal_id,posted_by_principal_id,reason_code,version,
               prepared_at,posted_at,created_at,updated_at)
            VALUES
              (#{journalEntryId},#{tenantId},#{journalCode},#{legalEntityId},#{ledgerId},#{periodId},#{accountingDate},
               #{sourceType},#{sourceId},#{currencyCode},#{documentCurrencyCode},#{debitTotalMinor},#{creditTotalMinor},
               #{evidenceSha256},#{status},#{preparedByPrincipalId},#{postedByPrincipalId},#{reasonCode},#{version},
               #{preparedAt},#{postedAt},#{createdAt},#{updatedAt})
            """)
    int insertJournalEntry(JournalEntry value);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_line
              (journal_line_id,tenant_id,journal_entry_id,ledger_id,line_number,account_id,account_code,debit_amount_minor,
               credit_amount_minor,transaction_currency_code,transaction_amount_minor,supplier_id,
               supplier_invoice_id,ap_open_item_id,purchase_order_id,purchase_order_item_id,receipt_line_id,
               inventory_movement_id,created_at)
            VALUES
              (#{journalLineId},#{tenantId},#{journalEntryId},#{ledgerId},#{lineNumber},#{accountId},#{accountCode},
               #{debitAmountMinor},#{creditAmountMinor},#{transactionCurrencyCode},#{transactionAmountMinor},
               #{supplierId},#{supplierInvoiceId},#{apOpenItemId},#{purchaseOrderId},#{purchaseOrderItemId},
               #{receiptLineId},#{inventoryMovementId},#{createdAt})
            """)
    int insertJournalLine(JournalLine value);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_line_dimension
              (journal_line_dimension_id,tenant_id,journal_line_id,dimension_type_id,dimension_value_id,created_at)
            VALUES
              (#{id},#{tenantId},#{lineId},#{typeId},#{valueId},#{now})
            """)
    int insertJournalLineDimension(@Param("id") String id,
                                   @Param("tenantId") Long tenantId,
                                   @Param("lineId") String lineId,
                                   @Param("typeId") String typeId,
                                   @Param("valueId") String valueId,
                                   @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_source_effect
              (source_effect_id,tenant_id,source_type,source_id,effect_type,journal_entry_id,created_at)
            VALUES
              (#{effectId},#{tenantId},#{sourceType},#{sourceId},#{effectType},#{journalId},#{now})
            """)
    int insertJournalSourceEffect(@Param("effectId") String effectId,
                                  @Param("tenantId") Long tenantId,
                                  @Param("sourceType") String sourceType,
                                  @Param("sourceId") String sourceId,
                                  @Param("effectType") String effectType,
                                  @Param("journalId") String journalId,
                                  @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_status_history
              (tenant_id,journal_entry_id,from_status,to_status,actor_principal_id,reason_code,aggregate_version,occurred_at)
            VALUES
              (#{tenantId},#{journalId},#{fromStatus},#{toStatus},#{actor},#{reason},#{version},#{now})
            """)
    int insertJournalHistory(@Param("tenantId") Long tenantId,
                             @Param("journalId") String journalId,
                             @Param("fromStatus") String fromStatus,
                             @Param("toStatus") String toStatus,
                             @Param("actor") String actor,
                             @Param("reason") String reason,
                             @Param("version") Long version,
                             @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_ap_application
              (ap_application_id,tenant_id,ap_open_item_id,source_type,source_id,application_type,
               amount_minor,journal_entry_id,applied_at)
            VALUES
              (#{applicationId},#{tenantId},#{apOpenItemId},'SUPPLIER_RETURN',#{sourceId},'SETTLE',
               #{amountMinor},#{journalEntryId},#{appliedAt})
            """)
    int insertSupplierReturnApApplication(@Param("applicationId") String applicationId,
                                          @Param("tenantId") Long tenantId,
                                          @Param("apOpenItemId") String apOpenItemId,
                                          @Param("sourceId") String sourceId,
                                          @Param("amountMinor") Long amountMinor,
                                          @Param("journalEntryId") String journalEntryId,
                                          @Param("appliedAt") LocalDateTime appliedAt);

    @Update("""
            UPDATE cloudmold_finance_ap_open_item
            SET status=CASE WHEN open_amount_minor=#{amountMinor} THEN 'SETTLED' ELSE 'PARTIALLY_SETTLED' END,
                settled_amount_minor=settled_amount_minor+#{amountMinor},
                open_amount_minor=open_amount_minor-#{amountMinor},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND ap_open_item_id=#{apOpenItemId}
              AND version=#{expectedVersion} AND open_amount_minor>=#{amountMinor}
            """)
    int applyApSettlement(@Param("tenantId") Long tenantId,
                          @Param("apOpenItemId") String apOpenItemId,
                          @Param("expectedVersion") Long expectedVersion,
                          @Param("amountMinor") Long amountMinor,
                          @Param("now") LocalDateTime now);

    @Select("""
            SELECT ap_open_item_id,tenant_id,supplier_invoice_id,legal_entity_id,ledger_id,supplier_id,currency_code,
                   original_amount_minor,settled_amount_minor,open_amount_minor,due_date,status,version,created_at,updated_at
            FROM cloudmold_finance_ap_open_item
            WHERE tenant_id=#{tenantId} AND ap_open_item_id=#{apOpenItemId}
            FOR UPDATE
            """)
    ApOpenItem selectApForUpdate(@Param("tenantId") Long tenantId,
                                 @Param("apOpenItemId") String apOpenItemId);

    @Select("""
            SELECT ap_installment_id,tenant_id,ap_open_item_id,installment_number,due_date,amount_minor,
                   settled_amount_minor,status,version,created_at,updated_at
            FROM cloudmold_finance_ap_installment
            WHERE tenant_id=#{tenantId} AND ap_open_item_id=#{apOpenItemId} AND status!='SETTLED'
            ORDER BY due_date,installment_number
            FOR UPDATE
            """)
    List<ApInstallment> selectOpenInstallmentsForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("apOpenItemId") String apOpenItemId);

    @Update("""
            UPDATE cloudmold_finance_ap_installment
            SET status=CASE WHEN settled_amount_minor+#{amountMinor}=amount_minor THEN 'SETTLED'
                            ELSE 'PARTIALLY_SETTLED' END,
                settled_amount_minor=settled_amount_minor+#{amountMinor},
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND ap_installment_id=#{apInstallmentId}
              AND version=#{expectedVersion}
              AND amount_minor-settled_amount_minor>=#{amountMinor}
            """)
    int applyInstallmentSettlement(@Param("tenantId") Long tenantId,
                                   @Param("apInstallmentId") String apInstallmentId,
                                   @Param("expectedVersion") Long expectedVersion,
                                   @Param("amountMinor") Long amountMinor,
                                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_supplier_invoice i
              JOIN cloudmold_finance_ap_open_item a ON a.supplier_invoice_id=i.supplier_invoice_id
            SET i.settlement_status=CASE WHEN a.open_amount_minor=0 THEN 'PAID'
                                         WHEN a.settled_amount_minor>0 THEN 'PARTIALLY_PAID'
                                         ELSE 'UNPAID' END,
                i.version=i.version+1,i.updated_at=#{now}
            WHERE i.tenant_id=#{tenantId} AND a.ap_open_item_id=#{apOpenItemId}
            """)
    int updateInvoiceSettlementFromAp(@Param("tenantId") Long tenantId,
                                      @Param("apOpenItemId") String apOpenItemId,
                                      @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_inventory_valuation_layer
            SET remaining_quantity=remaining_quantity-#{quantity},
                remaining_cost_amount_minor=remaining_cost_amount_minor-#{amountMinor},
                status=CASE WHEN remaining_quantity-#{quantity}=0 THEN 'CLOSED' ELSE 'OPEN' END,
                version=version+1,updated_at=#{now}
            WHERE tenant_id=#{tenantId} AND valuation_layer_id=#{valuationLayerId}
              AND version=#{expectedVersion}
              AND remaining_quantity>=#{quantity}
              AND remaining_cost_amount_minor>=#{amountMinor}
            """)
    int applyValuationReturn(@Param("tenantId") Long tenantId,
                             @Param("valuationLayerId") String valuationLayerId,
                             @Param("expectedVersion") Long expectedVersion,
                             @Param("quantity") BigDecimal quantity,
                             @Param("amountMinor") Long amountMinor,
                             @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_valuation_effect
              (valuation_effect_id,tenant_id,valuation_layer_id,effect_type,supplier_return_line_id,
               inventory_movement_id,inventory_movement_version,
               quantity,amount_minor,currency_code,journal_entry_id,occurred_at)
            VALUES
              (#{effectId},#{tenantId},#{valuationLayerId},'SUPPLIER_RETURN',#{supplierReturnLineId},#{inventoryMovementId},
               #{inventoryMovementVersion},#{quantity},#{amountMinor},#{currencyCode},#{journalEntryId},#{occurredAt})
            """)
    int insertSupplierReturnValuationEffect(@Param("effectId") String effectId,
                                            @Param("tenantId") Long tenantId,
                                            @Param("valuationLayerId") String valuationLayerId,
                                            @Param("supplierReturnLineId") String supplierReturnLineId,
                                            @Param("inventoryMovementId") String inventoryMovementId,
                                            @Param("inventoryMovementVersion") Long inventoryMovementVersion,
                                            @Param("quantity") BigDecimal quantity,
                                            @Param("amountMinor") Long amountMinor,
                                            @Param("currencyCode") String currencyCode,
                                            @Param("journalEntryId") String journalEntryId,
                                            @Param("occurredAt") LocalDateTime occurredAt);
}
