package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.InventoryControlFinanceRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.JournalEntry;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.JournalLine;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.PostingAccount;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.JournalDimensionAssignment;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InventoryControlFinanceMapper {

    @Insert("""
            INSERT INTO cloudmold_finance_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();

    @Select("""
            SELECT operation_id,tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,
                   aggregate_type,aggregate_id,result_json
              FROM cloudmold_finance_operation
             WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} FOR UPDATE
            """)
    Operation selectOperationForUpdate(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId);

    @Update("""
            UPDATE cloudmold_finance_operation SET status=10,aggregate_type=#{aggregateType},
                   aggregate_id=#{aggregateId},result_json=#{resultJson},updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND operation_id=#{operationId} AND status=0
            """)
    int markOperationSucceeded(@Param("operationId") Long operationId, @Param("tenantId") Long tenantId,
                               @Param("aggregateType") String aggregateType, @Param("aggregateId") String aggregateId,
                               @Param("resultJson") String resultJson, @Param("now") LocalDateTime now);

    @Select("""
            SELECT period_id,tenant_id,period_code,period_start,period_end,currency_code,status,
                   opened_by_principal_id,closed_by_principal_id,close_evidence_sha256,reason_code,
                   version,opened_at,closed_at,created_at,updated_at
              FROM cloudmold_finance_accounting_period
             WHERE tenant_id=#{tenantId} AND period_id=#{periodId} FOR UPDATE
            """)
    AccountingPeriod selectPeriodForUpdate(@Param("tenantId") Long tenantId, @Param("periodId") String periodId);

    @Select("""
            SELECT ledger_id,tenant_id,legal_entity_id,functional_currency_code,status
              FROM cloudmold_finance_ledger
             WHERE tenant_id=#{tenantId} AND ledger_id=#{ledgerId} FOR UPDATE
            """)
    Ledger selectLedgerForUpdate(@Param("tenantId") Long tenantId, @Param("ledgerId") String ledgerId);

    @Select("""
            SELECT l.account_role,r.ledger_id,a.account_id,a.account_code
              FROM cloudmold_finance_posting_rule r
              JOIN cloudmold_finance_posting_rule_line l
                ON l.tenant_id=r.tenant_id AND l.posting_rule_id=r.posting_rule_id
               AND l.rule_version=r.rule_version AND l.ledger_id=r.ledger_id
              JOIN cloudmold_finance_account a
                ON a.tenant_id=l.tenant_id AND a.ledger_id=l.ledger_id AND a.account_id=l.account_id
             WHERE r.tenant_id=#{tenantId}
               AND r.posting_rule_id=#{ruleId}
               AND r.rule_version=#{ruleVersion}
               AND r.ledger_id=#{ledgerId}
               AND r.source_type=#{sourceType}
               AND r.status='ACTIVE'
               AND a.status='ACTIVE'
            """)
    List<PostingAccount> selectPostingAccounts(@Param("tenantId") Long tenantId, @Param("ruleId") String ruleId,
                                               @Param("ruleVersion") Long ruleVersion, @Param("ledgerId") String ledgerId,
                                               @Param("sourceType") String sourceType);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_dimension_value
             WHERE tenant_id=#{tenantId} AND dimension_type_id=#{typeId}
               AND dimension_value_id=#{valueId} AND status='ACTIVE'
            """)
    int countActiveDimensionValue(@Param("tenantId") Long tenantId, @Param("typeId") String typeId,
                                  @Param("valueId") String valueId);

    @Select("""
            SELECT sc.stock_count_id,sc.stock_count_code,sc.status AS stock_count_status,sc.version AS stock_count_version,
                   l.line_id,l.line_number,l.count_status AS line_status,l.version AS line_version,l.owner_type,l.owner_id,
                   l.canonical_sku_id,l.lot_id,l.base_uom_code,l.difference_quantity,l.adjustment_id,
                   l.adjustment_ledger_transaction_id
              FROM cloudmold_stock_count sc
              JOIN cloudmold_stock_count_line l ON l.stock_count_id=sc.stock_count_id AND l.tenant_id=sc.tenant_id
             WHERE sc.tenant_id=#{tenantId} AND sc.stock_count_id=#{stockCountId} AND l.line_id=#{lineId} FOR UPDATE
            """)
    StockCountSource selectStockCountSourceForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("stockCountId") String stockCountId,
                                                     @Param("lineId") String lineId);

    @Select("""
            SELECT stock_count_gain_basis_id,tenant_id,basis_code,stock_count_id,stock_count_line_id,stock_count_version,
                   stock_count_line_version,valuation_policy_id,valuation_policy_version,valuation_policy_hash,gain_quantity,
                   unit_of_measure,unit_cost_amount_minor,total_cost_amount_minor,currency_code,evidence_sha256,status,
                   submitted_by_principal_id,approved_by_principal_id,approved_at,version,created_at,updated_at
              FROM cloudmold_finance_stock_count_gain_basis
             WHERE tenant_id=#{tenantId} AND stock_count_line_id=#{lineId}
               AND stock_count_line_version=#{lineVersion} FOR UPDATE
            """)
    StockCountGainBasis selectGainBasisByLineForUpdate(@Param("tenantId") Long tenantId, @Param("lineId") String lineId,
                                                       @Param("lineVersion") Long lineVersion);

    @Select("""
            SELECT stock_count_gain_basis_id,tenant_id,basis_code,stock_count_id,stock_count_line_id,stock_count_version,
                   stock_count_line_version,valuation_policy_id,valuation_policy_version,valuation_policy_hash,gain_quantity,
                   unit_of_measure,unit_cost_amount_minor,total_cost_amount_minor,currency_code,evidence_sha256,status,
                   submitted_by_principal_id,approved_by_principal_id,approved_at,version,created_at,updated_at
              FROM cloudmold_finance_stock_count_gain_basis
             WHERE tenant_id=#{tenantId} AND stock_count_gain_basis_id=#{basisId}
            """)
    StockCountGainBasis selectGainBasis(@Param("tenantId") Long tenantId, @Param("basisId") String basisId);

    @Insert("""
            INSERT INTO cloudmold_finance_stock_count_gain_basis
              (stock_count_gain_basis_id,tenant_id,basis_code,stock_count_id,stock_count_line_id,stock_count_version,
               stock_count_line_version,valuation_policy_id,valuation_policy_version,valuation_policy_hash,gain_quantity,
               unit_of_measure,unit_cost_amount_minor,total_cost_amount_minor,currency_code,evidence_sha256,status,
               submitted_by_principal_id,approved_by_principal_id,approved_at,version,created_at,updated_at)
            VALUES (#{stockCountGainBasisId},#{tenantId},#{basisCode},#{stockCountId},#{stockCountLineId},#{stockCountVersion},
                    #{stockCountLineVersion},#{valuationPolicyId},#{valuationPolicyVersion},#{valuationPolicyHash},#{gainQuantity},
                    #{unitOfMeasure},#{unitCostAmountMinor},#{totalCostAmountMinor},#{currencyCode},#{evidenceSha256},#{status},
                    #{submittedByPrincipalId},#{approvedByPrincipalId},#{approvedAt},#{version},#{createdAt},#{updatedAt})
            """)
    int insertGainBasis(StockCountGainBasis value);

    @Update("""
            UPDATE cloudmold_finance_stock_count_gain_basis
               SET status='APPROVED',approved_by_principal_id=#{actor},approved_at=#{now},version=#{nextVersion},updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND stock_count_gain_basis_id=#{basisId}
               AND version=#{version} AND status='SUBMITTED'
            """)
    int approveGainBasis(@Param("tenantId") Long tenantId, @Param("basisId") String basisId,
                         @Param("version") Long version, @Param("nextVersion") Long nextVersion,
                         @Param("actor") String actor, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_stock_count_gain_basis_history
              (history_id,tenant_id,stock_count_gain_basis_id,previous_status,new_status,changed_by_principal_id,note,version,changed_at)
            VALUES (#{historyId},#{tenantId},#{basisId},#{previousStatus},#{newStatus},#{actor},#{note},#{version},#{now})
            """)
    int insertGainBasisHistory(@Param("historyId") String historyId, @Param("tenantId") Long tenantId,
                               @Param("basisId") String basisId, @Param("previousStatus") String previousStatus,
                               @Param("newStatus") String newStatus, @Param("actor") String actor,
                               @Param("note") String note, @Param("version") Long version, @Param("now") LocalDateTime now);

    @Select("""
            SELECT d.scrap_id,d.scrap_code,d.status AS scrap_status,d.version AS scrap_version,
                   l.line_id,l.line_number,l.status AS line_status,l.version AS line_version,
                   dl.disposition_line_id,d.owner_type,d.owner_id,l.canonical_sku_id,l.lot_id,l.base_uom_code,
                   dl.disposed_quantity,dl.inventory_ledger_transaction_id
              FROM cloudmold_inventory_scrap_document d
              JOIN cloudmold_inventory_scrap_line l ON l.scrap_id=d.scrap_id AND l.tenant_id=d.tenant_id
              JOIN cloudmold_inventory_scrap_disposition_line dl
                ON dl.scrap_id=d.scrap_id AND dl.scrap_line_id=l.line_id AND dl.tenant_id=d.tenant_id
             WHERE d.tenant_id=#{tenantId} AND d.scrap_id=#{scrapId}
               AND l.line_id=#{lineId} AND dl.disposition_line_id=#{dispositionLineId} FOR UPDATE
            """)
    InventoryScrapSource selectInventoryScrapSourceForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("scrapId") String scrapId,
                                                             @Param("lineId") String lineId,
                                                             @Param("dispositionLineId") String dispositionLineId);

    @Select("""
            SELECT valuation_layer_id,tenant_id,'PROCUREMENT_RECEIPT' AS valuation_layer_source_type,ledger_id,
                   valuation_policy_id,valuation_policy_version,NULL AS valuation_policy_hash,
                   remaining_quantity,remaining_cost_amount_minor,unit_cost_amount_minor,currency_code,
                   NULL AS owner_type,NULL AS owner_id,NULL AS canonical_sku_id,NULL AS lot_id,status,version,created_at
              FROM cloudmold_finance_inventory_valuation_layer
             WHERE tenant_id=#{tenantId} AND valuation_layer_id=#{valuationLayerId} FOR UPDATE
            """)
    ValuationLayerCandidate selectValuationLayerForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("valuationLayerId") String valuationLayerId);

    @Select("""
            SELECT valuation_layer_id,tenant_id,'INVENTORY_CONTROL_GAIN' AS valuation_layer_source_type,ledger_id,
                   valuation_policy_id,valuation_policy_version,valuation_policy_hash,remaining_quantity,
                   remaining_cost_amount_minor,unit_cost_amount_minor,currency_code,owner_type,owner_id,
                   canonical_sku_id,lot_id,status,version,created_at
              FROM cloudmold_finance_inventory_control_valuation_layer
             WHERE tenant_id=#{tenantId} AND valuation_layer_id=#{valuationLayerId} FOR UPDATE
            """)
    ValuationLayerCandidate selectControlValuationLayerForUpdate(@Param("tenantId") Long tenantId,
                                                                 @Param("valuationLayerId") String valuationLayerId);

    @Select("""
            SELECT l.valuation_layer_id,l.tenant_id,'PROCUREMENT_RECEIPT' AS valuation_layer_source_type,l.ledger_id,
                   l.valuation_policy_id,l.valuation_policy_version,NULL AS valuation_policy_hash,l.remaining_quantity,
                   l.remaining_cost_amount_minor,l.unit_cost_amount_minor,l.currency_code,
                   r.owner_type,r.owner_id,r.canonical_sku_id,r.lot_id,l.status,l.version,l.created_at
              FROM cloudmold_finance_inventory_valuation_layer l
              JOIN cloudmold_warehouse_procurement_receipt_line r
                ON r.tenant_id=l.tenant_id AND r.receipt_line_id=l.receipt_line_id
             WHERE l.tenant_id=#{tenantId} AND l.ledger_id=#{ledgerId} AND l.status='OPEN'
               AND l.remaining_quantity > 0
               AND r.owner_type=#{ownerType} AND r.owner_id=#{ownerId}
               AND r.canonical_sku_id=#{canonicalSkuId}
               AND ((#{lotId} IS NULL AND r.lot_id IS NULL) OR r.lot_id=#{lotId})
             ORDER BY l.created_at,l.valuation_layer_id FOR UPDATE
            """)
    List<ValuationLayerCandidate> selectOpenValuationLayersForSource(@Param("tenantId") Long tenantId,
                                                                     @Param("ledgerId") String ledgerId,
                                                                     @Param("ownerType") String ownerType,
                                                                     @Param("ownerId") String ownerId,
                                                                     @Param("canonicalSkuId") String canonicalSkuId,
                                                                     @Param("lotId") String lotId);

    @Select("""
            SELECT valuation_layer_id,tenant_id,'INVENTORY_CONTROL_GAIN' AS valuation_layer_source_type,ledger_id,
                   valuation_policy_id,valuation_policy_version,valuation_policy_hash,remaining_quantity,
                   remaining_cost_amount_minor,unit_cost_amount_minor,currency_code,owner_type,owner_id,
                   canonical_sku_id,lot_id,status,version,created_at
              FROM cloudmold_finance_inventory_control_valuation_layer
             WHERE tenant_id=#{tenantId} AND ledger_id=#{ledgerId} AND status='OPEN'
               AND remaining_quantity > 0 AND owner_type=#{ownerType} AND owner_id=#{ownerId}
               AND canonical_sku_id=#{canonicalSkuId}
               AND ((#{lotId} IS NULL AND lot_id IS NULL) OR lot_id=#{lotId})
             ORDER BY created_at,valuation_layer_id FOR UPDATE
            """)
    List<ValuationLayerCandidate> selectOpenControlValuationLayersForSource(@Param("tenantId") Long tenantId,
                                                                            @Param("ledgerId") String ledgerId,
                                                                            @Param("ownerType") String ownerType,
                                                                            @Param("ownerId") String ownerId,
                                                                            @Param("canonicalSkuId") String canonicalSkuId,
                                                                            @Param("lotId") String lotId);

    @Update("""
            UPDATE cloudmold_finance_inventory_valuation_layer
               SET remaining_quantity=#{remainingQuantity},
                   remaining_cost_amount_minor=#{remainingCostAmountMinor},
                   status=#{status},
                   version=#{nextVersion},
                   updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND valuation_layer_id=#{valuationLayerId}
               AND version=#{version}
            """)
    int updateValuationLayerRemainingCas(@Param("tenantId") Long tenantId,
                                         @Param("valuationLayerId") String valuationLayerId,
                                         @Param("version") Long version,
                                         @Param("nextVersion") Long nextVersion,
                                         @Param("remainingQuantity") java.math.BigDecimal remainingQuantity,
                                         @Param("remainingCostAmountMinor") Long remainingCostAmountMinor,
                                         @Param("status") String status,
                                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_inventory_control_valuation_layer
               SET remaining_quantity=#{remainingQuantity},remaining_cost_amount_minor=#{remainingCostAmountMinor},
                   status=#{status},version=#{nextVersion},updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND valuation_layer_id=#{valuationLayerId} AND version=#{version}
            """)
    int updateControlValuationLayerRemainingCas(@Param("tenantId") Long tenantId,
                                                @Param("valuationLayerId") String valuationLayerId,
                                                @Param("version") Long version,
                                                @Param("nextVersion") Long nextVersion,
                                                @Param("remainingQuantity") BigDecimal remainingQuantity,
                                                @Param("remainingCostAmountMinor") Long remainingCostAmountMinor,
                                                @Param("status") String status,
                                                @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_control_valuation_layer
              (valuation_layer_id,tenant_id,ledger_id,source_type,source_document_id,source_line_id,source_reference_id,
               inventory_ledger_transaction_id,owner_type,owner_id,canonical_sku_id,lot_id,valuation_policy_id,
               valuation_policy_version,valuation_policy_hash,quantity,unit_of_measure,unit_cost_amount_minor,
               total_cost_amount_minor,currency_code,remaining_quantity,remaining_cost_amount_minor,status,version,
               created_at,updated_at)
            VALUES (#{valuationLayerId},#{tenantId},#{ledgerId},#{valuationLayerSourceType},#{sourceDocumentId},
                    #{sourceLineId},#{sourceReferenceId},#{inventoryLedgerTransactionId},#{ownerType},#{ownerId},
                    #{canonicalSkuId},#{lotId},#{valuationPolicyId},#{valuationPolicyVersion},#{valuationPolicyHash},
                    #{quantity},#{unitOfMeasure},#{unitCostAmountMinor},#{totalCostAmountMinor},#{currencyCode},
                    #{remainingQuantity},#{remainingCostAmountMinor},#{status},#{version},#{createdAt},#{updatedAt})
            """)
    int insertControlValuationLayer(InventoryControlValuationLayer value);

    @Select("""
            SELECT inventory_control_posting_id,tenant_id,source_type,source_document_id,source_line_id,source_reference_id,
                   source_document_version,source_line_version,inventory_ledger_transaction_id,legal_entity_id,ledger_id,
                   accounting_period_id,accounting_date,posting_rule_id,posting_rule_version,quantity,unit_of_measure,
                   currency_code,total_amount_minor,journal_entry_id,journal_code,reversal_journal_entry_id,
                   posting_evidence_sha256,status,created_by_principal_id,reversed_by_principal_id,version,created_at,updated_at
              FROM cloudmold_finance_inventory_control_posting
             WHERE tenant_id=#{tenantId} AND inventory_control_posting_id=#{postingId} FOR UPDATE
            """)
    InventoryControlPosting selectPostingForUpdate(@Param("tenantId") Long tenantId, @Param("postingId") String postingId);

    @Select("""
            SELECT inventory_control_posting_id,tenant_id,source_type,source_document_id,source_line_id,source_reference_id,
                   source_document_version,source_line_version,inventory_ledger_transaction_id,legal_entity_id,ledger_id,
                   accounting_period_id,accounting_date,posting_rule_id,posting_rule_version,quantity,unit_of_measure,
                   currency_code,total_amount_minor,journal_entry_id,journal_code,reversal_journal_entry_id,
                   posting_evidence_sha256,status,created_by_principal_id,reversed_by_principal_id,version,created_at,updated_at
              FROM cloudmold_finance_inventory_control_posting
             WHERE tenant_id=#{tenantId} AND inventory_control_posting_id=#{postingId}
            """)
    InventoryControlPosting selectPosting(@Param("tenantId") Long tenantId, @Param("postingId") String postingId);

    @Select("""
            SELECT inventory_control_posting_id,tenant_id,source_type,source_document_id,source_line_id,source_reference_id,
                   source_document_version,source_line_version,inventory_ledger_transaction_id,legal_entity_id,ledger_id,
                   accounting_period_id,accounting_date,posting_rule_id,posting_rule_version,quantity,unit_of_measure,
                   currency_code,total_amount_minor,journal_entry_id,journal_code,reversal_journal_entry_id,
                   posting_evidence_sha256,status,created_by_principal_id,reversed_by_principal_id,version,created_at,updated_at
              FROM cloudmold_finance_inventory_control_posting
             WHERE tenant_id=#{tenantId} AND source_type=#{sourceType} AND source_reference_id=#{sourceReferenceId} FOR UPDATE
            """)
    InventoryControlPosting selectPostingBySourceForUpdate(@Param("tenantId") Long tenantId,
                                                           @Param("sourceType") String sourceType,
                                                           @Param("sourceReferenceId") String sourceReferenceId);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_control_posting
              (inventory_control_posting_id,tenant_id,source_type,source_document_id,source_line_id,source_reference_id,
               source_document_version,source_line_version,inventory_ledger_transaction_id,legal_entity_id,ledger_id,
               accounting_period_id,accounting_date,posting_rule_id,posting_rule_version,quantity,unit_of_measure,
               currency_code,total_amount_minor,journal_entry_id,journal_code,posting_evidence_sha256,status,
               created_by_principal_id,reversed_by_principal_id,version,created_at,updated_at)
            VALUES (#{inventoryControlPostingId},#{tenantId},#{sourceType},#{sourceDocumentId},#{sourceLineId},#{sourceReferenceId},
                    #{sourceDocumentVersion},#{sourceLineVersion},#{inventoryLedgerTransactionId},#{legalEntityId},#{ledgerId},
                    #{accountingPeriodId},#{accountingDate},#{postingRuleId},#{postingRuleVersion},#{quantity},#{unitOfMeasure},
                    #{currencyCode},#{totalAmountMinor},#{journalEntryId},#{journalCode},#{postingEvidenceSha256},#{status},
                    #{createdByPrincipalId},#{reversedByPrincipalId},#{version},#{createdAt},#{updatedAt})
            """)
    int insertPosting(InventoryControlPosting value);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_control_posting_allocation
              (allocation_id,tenant_id,inventory_control_posting_id,sequence_no,valuation_layer_source_type,
               valuation_layer_id,allocated_quantity,allocated_cost_amount_minor,created_at)
            VALUES (#{allocationId},#{tenantId},#{inventoryControlPostingId},#{sequenceNo},#{valuationLayerSourceType},
                    #{valuationLayerId},#{allocatedQuantity},#{allocatedCostAmountMinor},#{createdAt})
            """)
    int insertPostingAllocation(InventoryControlPostingAllocation value);

    @Select("""
            SELECT allocation_id,tenant_id,inventory_control_posting_id,sequence_no,valuation_layer_source_type,
                   valuation_layer_id,allocated_quantity,allocated_cost_amount_minor,created_at
              FROM cloudmold_finance_inventory_control_posting_allocation
             WHERE tenant_id=#{tenantId} AND inventory_control_posting_id=#{postingId}
             ORDER BY sequence_no,allocation_id
            """)
    List<InventoryControlPostingAllocation> selectPostingAllocations(@Param("tenantId") Long tenantId,
                                                                     @Param("postingId") String postingId);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_inventory_control_posting
             WHERE tenant_id=#{tenantId}
               <if test='sourceType!=null'>AND source_type=#{sourceType}</if>
               <if test='status!=null'>AND status=#{status}</if>
               <if test='keyword!=null'>
                 AND (source_document_id LIKE CONCAT('%',#{keyword},'%')
                  OR source_line_id LIKE CONCAT('%',#{keyword},'%')
                  OR source_reference_id LIKE CONCAT('%',#{keyword},'%')
                  OR journal_code LIKE CONCAT('%',#{keyword},'%'))
               </if>
            """)
    long countInventoryPostingPage(@Param("tenantId") Long tenantId, @Param("sourceType") String sourceType,
                                   @Param("status") String status, @Param("keyword") String keyword);

    @Select("""
            SELECT p.inventory_control_posting_id AS inventoryControlPostingId,
                   p.source_type AS sourceType,
                   p.source_document_id AS sourceDocumentId,
                   p.source_line_id AS sourceLineId,
                   p.source_reference_id AS sourceReferenceId,
                   CASE WHEN p.source_type='STOCK_COUNT_ADJUSTMENT' THEN 'SHRINKAGE' ELSE 'SCRAP' END AS impactType,
                   p.quantity AS quantity,
                   p.unit_of_measure AS unitOfMeasure,
                   p.currency_code AS currencyCode,
                   p.total_amount_minor AS valuationImpactAmountMinor,
                   0 AS apImpactAmountMinor,
                   p.total_amount_minor AS totalAmountMinor,
                   p.journal_entry_id AS journalEntryId,
                   p.journal_code AS journalCode,
                   p.accounting_period_id AS accountingPeriodId,
                   p.accounting_date AS accountingDate,
                   p.status AS postingStatus,
                   j.status AS journalStatus,
                   CASE WHEN j.debit_total_minor=j.credit_total_minor THEN 'BALANCED' ELSE 'UNBALANCED' END AS balanceStatus,
                   p.version AS aggregateVersion,
                   p.updated_at AS updatedAt
              FROM cloudmold_finance_inventory_control_posting p
              JOIN cloudmold_finance_journal_entry j
                ON j.tenant_id=p.tenant_id AND j.journal_entry_id=p.journal_entry_id
             WHERE p.tenant_id=#{tenantId}
               <if test='sourceType!=null'>AND p.source_type=#{sourceType}</if>
               <if test='status!=null'>AND p.status=#{status}</if>
               <if test='keyword!=null'>
                 AND (p.source_document_id LIKE CONCAT('%',#{keyword},'%')
                  OR p.source_line_id LIKE CONCAT('%',#{keyword},'%')
                  OR p.source_reference_id LIKE CONCAT('%',#{keyword},'%')
                  OR p.journal_code LIKE CONCAT('%',#{keyword},'%'))
               </if>
             ORDER BY p.updated_at DESC,p.inventory_control_posting_id DESC
             LIMIT #{offset},#{size}
            """)
    List<cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageItem>
    selectPostingPage(@Param("tenantId") Long tenantId, @Param("sourceType") String sourceType,
                      @Param("status") String status, @Param("keyword") String keyword,
                      @Param("offset") long offset, @Param("size") int size);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_supplier_debit_adjustment
             WHERE tenant_id=#{tenantId}
               <if test='status!=null'>AND status=#{status}</if>
               <if test='keyword!=null'>
                 AND (supplier_return_id LIKE CONCAT('%',#{keyword},'%')
                  OR adjustment_code LIKE CONCAT('%',#{keyword},'%')
                  OR journal_entry_id LIKE CONCAT('%',#{keyword},'%'))
               </if>
            """)
    long countSupplierReturnPage(@Param("tenantId") Long tenantId, @Param("status") String status,
                                 @Param("keyword") String keyword);

    @Select("""
            SELECT a.supplier_debit_adjustment_id AS inventoryControlPostingId,
                   'SUPPLIER_RETURN' AS sourceType,
                   a.supplier_return_id AS sourceDocumentId,
                   NULL AS sourceLineId,
                   a.supplier_debit_adjustment_id AS sourceReferenceId,
                   'SUPPLIER_RETURN_REVERSAL' AS impactType,
                   NULL AS quantity,
                   NULL AS unitOfMeasure,
                   a.currency_code AS currencyCode,
                   a.valuation_reversal_amount_minor AS valuationImpactAmountMinor,
                   a.ap_reversal_amount_minor AS apImpactAmountMinor,
                   a.ap_reversal_amount_minor AS totalAmountMinor,
                   a.journal_entry_id AS journalEntryId,
                   j.journal_code AS journalCode,
                   a.accounting_period_id AS accountingPeriodId,
                   a.accounting_date AS accountingDate,
                   a.status AS postingStatus,
                   j.status AS journalStatus,
                   CASE WHEN j.debit_total_minor=j.credit_total_minor THEN 'BALANCED' ELSE 'UNBALANCED' END AS balanceStatus,
                   a.version AS aggregateVersion,
                   a.updated_at AS updatedAt
              FROM cloudmold_finance_supplier_debit_adjustment a
              JOIN cloudmold_finance_journal_entry j
                ON j.tenant_id=a.tenant_id AND j.journal_entry_id=a.journal_entry_id
             WHERE a.tenant_id=#{tenantId}
               <if test='status!=null'>AND a.status=#{status}</if>
               <if test='keyword!=null'>
                 AND (a.supplier_return_id LIKE CONCAT('%',#{keyword},'%')
                  OR a.adjustment_code LIKE CONCAT('%',#{keyword},'%')
                  OR a.journal_entry_id LIKE CONCAT('%',#{keyword},'%'))
               </if>
             ORDER BY a.updated_at DESC,a.supplier_debit_adjustment_id DESC
             LIMIT #{offset},#{size}
            """)
    List<cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.PostingPageItem>
    selectSupplierReturnPage(@Param("tenantId") Long tenantId, @Param("status") String status,
                             @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    @Select("""
            SELECT supplier_debit_adjustment_id,tenant_id,adjustment_code,supplier_return_id,supplier_return_version,
                   legal_entity_id,ledger_id,accounting_period_id,accounting_date,supplier_id,currency_code,
                   ap_reversal_amount_minor,tax_reversal_amount_minor,valuation_reversal_amount_minor,
                   purchase_price_variance_amount_minor,posting_rule_id,posting_rule_version,journal_entry_id,
                   reversal_evidence_sha256,reason_code,status,posted_by_principal_id,posted_at,version,created_at,updated_at
              FROM cloudmold_finance_supplier_debit_adjustment
             WHERE tenant_id=#{tenantId} AND supplier_debit_adjustment_id=#{id}
            """)
    cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.SupplierReturnReversalRecords.SupplierDebitAdjustment
    selectSupplierDebitAdjustment(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("""
            SELECT supplier_return_line_id AS sourceLineId,
                   supplier_debit_adjustment_id AS sourceReferenceId,
                   purchase_order_id AS purchaseOrderId,
                   purchase_order_item_id AS purchaseOrderItemId,
                   purchase_order_schedule_id AS purchaseOrderScheduleId,
                   receipt_line_id AS receiptLineId,
                   quality_disposition_id AS qualityDispositionId,
                   valuation_layer_id AS valuationLayerId,
                   reversal_quantity AS quantity,
                   unit_of_measure AS unitOfMeasure,
                   valuation_reversal_amount_minor AS valuationImpactAmountMinor,
                   gross_reversal_amount_minor AS apImpactAmountMinor,
                   purchase_price_variance_amount_minor AS purchasePriceVarianceAmountMinor,
                   currency_code AS currencyCode
              FROM cloudmold_finance_supplier_debit_adjustment_line
             WHERE tenant_id=#{tenantId} AND supplier_debit_adjustment_id=#{id}
             ORDER BY line_number,adjustment_line_id
            """)
    List<cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.FinancialImpactSourceLine>
    selectSupplierReturnSourceLines(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("""
            SELECT supplier_invoice_id AS supplierInvoiceId,
                   invoice_line_id AS invoiceLineId,
                   ap_open_item_id AS apOpenItemId,
                   reversal_quantity AS reversalQuantity,
                   unit_of_measure AS unitOfMeasure,
                   net_reversal_amount_minor AS netReversalAmountMinor,
                   tax_reversal_amount_minor AS taxReversalAmountMinor,
                   gross_reversal_amount_minor AS grossReversalAmountMinor
              FROM cloudmold_finance_supplier_return_ap_reversal
             WHERE tenant_id=#{tenantId} AND supplier_debit_adjustment_id=#{id}
             ORDER BY ap_reversal_id
            """)
    List<cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.InventoryControlFinanceAdminVOs.FinancialImpactApLineage>
    selectSupplierReturnApLineage(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Update("""
            UPDATE cloudmold_finance_inventory_control_posting
               SET status='REVERSED',reversal_journal_entry_id=#{reversalJournalEntryId},
                   reversed_by_principal_id=#{actor},version=#{nextVersion},updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND inventory_control_posting_id=#{postingId}
               AND version=#{version} AND status='POSTED' AND reversal_journal_entry_id IS NULL
            """)
    int markPostingReversed(@Param("tenantId") Long tenantId, @Param("postingId") String postingId,
                            @Param("version") Long version, @Param("nextVersion") Long nextVersion,
                            @Param("reversalJournalEntryId") String reversalJournalEntryId,
                            @Param("actor") String actor, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_entry
              (journal_entry_id,tenant_id,legal_entity_id,ledger_id,period_id,accounting_date,journal_code,
               source_type,source_id,currency_code,document_currency_code,debit_total_minor,credit_total_minor,
               evidence_sha256,status,prepared_by_principal_id,posted_by_principal_id,version,prepared_at,
               posted_at,created_at,updated_at)
            VALUES (#{journalEntryId},#{tenantId},#{legalEntityId},#{ledgerId},#{periodId},#{accountingDate},
                    #{journalCode},#{sourceType},#{sourceId},#{currencyCode},#{documentCurrencyCode},
                    #{debitTotalMinor},#{creditTotalMinor},#{evidenceSha256},#{status},#{preparedByPrincipalId},
                    #{postedByPrincipalId},#{version},#{preparedAt},#{postedAt},#{createdAt},#{updatedAt})
            """)
    int insertJournalEntry(JournalEntry value);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_line
              (journal_line_id,tenant_id,journal_entry_id,ledger_id,line_number,account_id,account_code,
               debit_amount_minor,credit_amount_minor,transaction_currency_code,transaction_amount_minor,
               supplier_id,supplier_invoice_id,ap_open_item_id,purchase_order_id,purchase_order_item_id,
               receipt_line_id,inventory_movement_id,created_at)
            VALUES (#{journalLineId},#{tenantId},#{journalEntryId},#{ledgerId},#{lineNumber},#{accountId},
                    #{accountCode},#{debitAmountMinor},#{creditAmountMinor},#{transactionCurrencyCode},
                    #{transactionAmountMinor},#{supplierId},#{supplierInvoiceId},#{apOpenItemId},
                    #{purchaseOrderId},#{purchaseOrderItemId},#{receiptLineId},#{inventoryMovementId},#{createdAt})
            """)
    int insertJournalLine(JournalLine value);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_line_dimension
              (journal_line_dimension_id,tenant_id,journal_line_id,dimension_type_id,dimension_value_id,created_at)
            VALUES (#{id},#{tenantId},#{lineId},#{typeId},#{valueId},#{now})
            """)
    int insertJournalLineDimension(@Param("id") String id, @Param("tenantId") Long tenantId,
                                   @Param("lineId") String lineId, @Param("typeId") String typeId,
                                   @Param("valueId") String valueId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_source_effect
              (journal_source_effect_id,tenant_id,source_type,source_id,effect_type,journal_entry_id,occurred_at)
            VALUES (#{effectId},#{tenantId},#{sourceType},#{sourceId},#{effectType},#{journalId},#{now})
            """)
    int insertJournalSourceEffect(@Param("effectId") String effectId, @Param("tenantId") Long tenantId,
                                  @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                                  @Param("effectType") String effectType, @Param("journalId") String journalId,
                                  @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_history
              (journal_history_id,tenant_id,journal_entry_id,previous_status,new_status,changed_by_principal_id,
               reason_code,version,changed_at)
            VALUES (#{id},#{tenantId},#{journalId},#{previousStatus},#{newStatus},#{actor},#{reasonCode},#{version},#{now})
            """)
    int insertJournalHistory(@Param("id") String id, @Param("tenantId") Long tenantId, @Param("journalId") String journalId,
                             @Param("previousStatus") String previousStatus, @Param("newStatus") String newStatus,
                             @Param("actor") String actor, @Param("reasonCode") String reasonCode,
                             @Param("version") Long version, @Param("now") LocalDateTime now);

    @Select("""
            SELECT journal_entry_id,tenant_id,legal_entity_id,ledger_id,period_id,accounting_date,journal_code,source_type,
                   source_id,currency_code,document_currency_code,debit_total_minor,credit_total_minor,evidence_sha256,status,
                   prepared_by_principal_id,posted_by_principal_id,version,prepared_at,posted_at,created_at,updated_at
              FROM cloudmold_finance_journal_entry
             WHERE tenant_id=#{tenantId} AND journal_entry_id=#{id} FOR UPDATE
            """)
    JournalEntry selectJournalForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("""
            SELECT COUNT(*) FROM cloudmold_finance_journal_reversal_link
             WHERE tenant_id=#{tenantId} AND original_journal_entry_id=#{id}
            """)
    int countJournalReversal(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Select("""
            SELECT journal_line_id,tenant_id,journal_entry_id,ledger_id,line_number,account_id,account_code,
                   debit_amount_minor,credit_amount_minor,transaction_currency_code,transaction_amount_minor,supplier_id,
                   supplier_invoice_id,ap_open_item_id,purchase_order_id,purchase_order_item_id,receipt_line_id,
                   inventory_movement_id,created_at
              FROM cloudmold_finance_journal_line
             WHERE tenant_id=#{tenantId} AND journal_entry_id=#{journalId}
             ORDER BY line_number,journal_line_id
            """)
    List<JournalLine> selectJournalLines(@Param("tenantId") Long tenantId, @Param("journalId") String journalId);

    @Select("""
            SELECT dimension_type_id,dimension_value_id
              FROM cloudmold_finance_journal_line_dimension
             WHERE tenant_id=#{tenantId} AND journal_line_id=#{lineId}
             ORDER BY journal_line_dimension_id
            """)
    List<JournalDimensionAssignment> selectJournalLineDimensions(@Param("tenantId") Long tenantId,
                                                                 @Param("lineId") String lineId);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_reversal_link
              (reversal_link_id,tenant_id,original_journal_entry_id,reversal_journal_entry_id,
               reversal_evidence_sha256,reversed_by_principal_id,reversed_at)
            VALUES (#{linkId},#{tenantId},#{originalId},#{reversalId},#{evidence},#{actor},#{now})
            """)
    int insertJournalReversalLink(@Param("linkId") String linkId, @Param("tenantId") Long tenantId,
                                  @Param("originalId") String originalId, @Param("reversalId") String reversalId,
                                  @Param("evidence") String evidence, @Param("actor") String actor,
                                  @Param("now") LocalDateTime now);
}
