package cn.iocoder.yudao.module.cloudmold.finance.dal.mysql;

import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.JournalDimensionAssignment;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureToPayAdminVOs.*;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProcureToPayMapper {
    @Select("<script>SELECT COUNT(*) FROM cloudmold_finance_supplier_invoice i WHERE i.tenant_id=#{tenantId}<if test='status!=null'> AND i.lifecycle_status=#{status}</if><if test='keyword!=null'> AND (i.invoice_code LIKE CONCAT('%',#{keyword},'%') OR i.supplier_invoice_number LIKE CONCAT('%',#{keyword},'%') OR i.supplier_id LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long countInvoicePage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("<script>SELECT i.supplier_invoice_id,i.invoice_code,i.supplier_id,i.supplier_invoice_number,i.currency_code,i.gross_amount_minor,i.lifecycle_status status,i.match_status,(SELECT COUNT(*) FROM cloudmold_finance_invoice_match_exception e JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=e.tenant_id AND r.match_run_id=e.match_run_id WHERE r.tenant_id=i.tenant_id AND r.supplier_invoice_id=i.supplier_invoice_id AND e.status='OPEN') exception_count,i.version aggregate_version,i.accounting_date,i.due_date,j.posted_at,i.updated_at FROM cloudmold_finance_supplier_invoice i LEFT JOIN cloudmold_finance_journal_entry j ON j.tenant_id=i.tenant_id AND j.journal_entry_id=i.posted_journal_entry_id WHERE i.tenant_id=#{tenantId}<if test='status!=null'> AND i.lifecycle_status=#{status}</if><if test='keyword!=null'> AND (i.invoice_code LIKE CONCAT('%',#{keyword},'%') OR i.supplier_invoice_number LIKE CONCAT('%',#{keyword},'%') OR i.supplier_id LIKE CONCAT('%',#{keyword},'%'))</if> ORDER BY i.updated_at DESC LIMIT #{offset},#{size}</script>")
    List<SupplierInvoicePageItem> selectInvoicePage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);
    @Select("SELECT i.supplier_invoice_id,i.invoice_code,i.supplier_id,i.supplier_invoice_number,i.currency_code,i.gross_amount_minor,i.lifecycle_status status,i.match_status,(SELECT COUNT(*) FROM cloudmold_finance_invoice_match_exception e JOIN cloudmold_finance_invoice_match_run r0 ON r0.tenant_id=e.tenant_id AND r0.match_run_id=e.match_run_id WHERE r0.tenant_id=i.tenant_id AND r0.supplier_invoice_id=i.supplier_invoice_id AND e.status='OPEN') exception_count,i.version aggregate_version,i.accounting_date,i.due_date,j.posted_at,i.updated_at,i.legal_entity_id,i.accounting_period_id,i.issue_date,i.net_amount_minor,i.tax_amount_minor,t.term_code payment_condition_code,r.match_run_id,r.match_policy_id,r.match_policy_version FROM cloudmold_finance_supplier_invoice i JOIN cloudmold_finance_payment_term t ON t.tenant_id=i.tenant_id AND t.payment_term_id=i.payment_term_id AND t.term_version=i.payment_term_version LEFT JOIN cloudmold_finance_journal_entry j ON j.tenant_id=i.tenant_id AND j.journal_entry_id=i.posted_journal_entry_id LEFT JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=i.tenant_id AND r.supplier_invoice_id=i.supplier_invoice_id AND r.status IN ('MATCHED','EXCEPTION') AND r.created_at=(SELECT MAX(r2.created_at) FROM cloudmold_finance_invoice_match_run r2 WHERE r2.tenant_id=i.tenant_id AND r2.supplier_invoice_id=i.supplier_invoice_id AND r2.status IN ('MATCHED','EXCEPTION')) WHERE i.tenant_id=#{tenantId} AND i.supplier_invoice_id=#{id}")
    SupplierInvoiceDetail selectInvoiceDetail(@Param("tenantId") Long tenantId,@Param("id") String id);
    @Select("""
            SELECT l.invoice_line_id,l.line_number,l.sku_id canonical_sku_id,
                   CAST(l.quantity AS CHAR) invoice_quantity,
                   CAST(COALESCE(SUM(a.allocated_quantity),0) AS CHAR) accepted_receipt_quantity,
                   l.unit_of_measure uom_code,CAST(l.unit_net_price AS CHAR) invoice_unit_net_price_minor,
                   CAST(po.unit_net_price AS CHAR) purchase_order_unit_net_price_minor,
                   l.gross_amount_minor line_gross_amount_minor,
                   ABS(COALESCE(ml.price_difference_amount_minor,0)+COALESCE(ml.tax_difference_amount_minor,0)) difference_amount_minor,
                   CASE WHEN MAX(e.exception_type) IS NULL THEN 'NONE'
                        WHEN SUM(e.exception_type='QUANTITY')>0 THEN 'QUANTITY'
                        WHEN SUM(e.exception_type='PRICE')>0 THEN 'PRICE' ELSE 'TAX' END difference_type,
                   l.invoice_line_id lineage_invoice_line_id,l.purchase_order_id lineage_purchase_order_id,
                   l.purchase_order_item_id lineage_purchase_order_item_id,
                   po.delivery_schedule_id lineage_delivery_schedule_id,
                   MIN(re.receipt_line_id) lineage_receipt_line_id,
                   MIN(qe.quality_disposition_id) lineage_quality_disposition_id,
                   MIN(ie.inventory_movement_id) lineage_inventory_movement_id
              FROM cloudmold_finance_supplier_invoice_line l
              LEFT JOIN cloudmold_finance_invoice_match_run r
                ON r.tenant_id=l.tenant_id AND r.supplier_invoice_id=l.supplier_invoice_id
               AND r.status IN ('MATCHED','EXCEPTION')
               AND r.created_at=(SELECT MAX(r2.created_at) FROM cloudmold_finance_invoice_match_run r2
                                  WHERE r2.tenant_id=l.tenant_id AND r2.supplier_invoice_id=l.supplier_invoice_id
                                    AND r2.status IN ('MATCHED','EXCEPTION'))
              LEFT JOIN cloudmold_finance_invoice_match_line ml
                ON ml.tenant_id=r.tenant_id AND ml.match_run_id=r.match_run_id AND ml.invoice_line_id=l.invoice_line_id
              LEFT JOIN cloudmold_finance_invoice_match_receipt_allocation a
                ON a.tenant_id=ml.tenant_id AND a.match_line_id=ml.match_line_id
               AND a.status IN ('ACTIVE','PENDING_OVERRIDE')
              LEFT JOIN cloudmold_finance_invoice_match_exception e
                ON e.tenant_id=ml.tenant_id AND e.match_line_id=ml.match_line_id
               AND e.status IN ('OPEN','OVERRIDE_APPROVED')
              LEFT JOIN cloudmold_finance_po_line_evidence po
                ON po.tenant_id=ml.tenant_id AND po.purchase_order_item_id=ml.purchase_order_item_id
               AND po.source_version=ml.purchase_order_line_version
              LEFT JOIN cloudmold_finance_receipt_line_evidence re
                ON re.tenant_id=a.tenant_id AND re.receipt_line_id=a.receipt_line_id
               AND re.source_version=a.receipt_line_version
              LEFT JOIN cloudmold_finance_quality_disposition_evidence qe
                ON qe.tenant_id=a.tenant_id AND qe.quality_disposition_id=a.quality_disposition_id
               AND qe.source_version=a.quality_disposition_version
              LEFT JOIN cloudmold_finance_inventory_movement_evidence ie
                ON ie.tenant_id=a.tenant_id AND ie.inventory_movement_id=a.inventory_movement_id
               AND ie.source_version=a.inventory_movement_version
             WHERE l.tenant_id=#{tenantId} AND l.supplier_invoice_id=#{id}
             GROUP BY l.invoice_line_id,l.line_number,l.sku_id,l.quantity,l.unit_of_measure,l.unit_net_price,
                      po.unit_net_price,l.gross_amount_minor,ml.price_difference_amount_minor,
                      ml.tax_difference_amount_minor,l.purchase_order_id,l.purchase_order_item_id,
                      po.delivery_schedule_id
             ORDER BY l.line_number
            """)
    List<SupplierInvoiceMatchLine> selectInvoiceLineViews(@Param("tenantId") Long tenantId,@Param("id") String id);
    @Select("SELECT j.journal_entry_id FROM cloudmold_finance_journal_entry j WHERE j.tenant_id=#{tenantId} AND ((j.source_type='SUPPLIER_INVOICE' AND j.source_id=#{invoiceId}) OR (j.source_type='SUPPLIER_PAYMENT' AND EXISTS (SELECT 1 FROM cloudmold_finance_supplier_payment_allocation pa JOIN cloudmold_finance_ap_open_item ap ON ap.tenant_id=pa.tenant_id AND ap.ap_open_item_id=pa.ap_open_item_id WHERE pa.tenant_id=j.tenant_id AND pa.payment_instruction_id=j.source_id AND ap.supplier_invoice_id=#{invoiceId}))) ORDER BY j.created_at")
    List<String> selectInvoiceJournalIds(@Param("tenantId") Long tenantId,@Param("invoiceId") String invoiceId);

    @Select("<script>SELECT COUNT(*) FROM cloudmold_finance_invoice_match_exception e JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=e.tenant_id AND r.match_run_id=e.match_run_id JOIN cloudmold_finance_supplier_invoice i ON i.tenant_id=r.tenant_id AND i.supplier_invoice_id=r.supplier_invoice_id WHERE e.tenant_id=#{tenantId}<if test='status!=null'> AND e.status=#{status}</if><if test='keyword!=null'> AND (i.invoice_code LIKE CONCAT('%',#{keyword},'%') OR i.supplier_invoice_id LIKE CONCAT('%',#{keyword},'%') OR e.exception_id LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long countMatchExceptionPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("<script>SELECT e.exception_id,e.exception_code,e.exception_type,e.status,e.match_run_id,r.supplier_invoice_id,i.invoice_code,ml.invoice_line_id,i.currency_code,e.expected_amount_minor,e.actual_amount_minor,CAST(e.expected_quantity AS CHAR) expected_quantity,CAST(e.actual_quantity AS CHAR) actual_quantity,e.version aggregate_version,COALESCE(e.resolved_at,e.opened_at) updated_at FROM cloudmold_finance_invoice_match_exception e JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=e.tenant_id AND r.match_run_id=e.match_run_id JOIN cloudmold_finance_supplier_invoice i ON i.tenant_id=r.tenant_id AND i.supplier_invoice_id=r.supplier_invoice_id JOIN cloudmold_finance_invoice_match_line ml ON ml.tenant_id=e.tenant_id AND ml.match_line_id=e.match_line_id WHERE e.tenant_id=#{tenantId}<if test='status!=null'> AND e.status=#{status}</if><if test='keyword!=null'> AND (i.invoice_code LIKE CONCAT('%',#{keyword},'%') OR i.supplier_invoice_id LIKE CONCAT('%',#{keyword},'%') OR e.exception_id LIKE CONCAT('%',#{keyword},'%'))</if> ORDER BY COALESCE(e.resolved_at,e.opened_at) DESC LIMIT #{offset},#{size}</script>")
    List<MatchExceptionPageItem> selectMatchExceptionPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM cloudmold_finance_ap_installment s JOIN cloudmold_finance_ap_open_item a ON a.tenant_id=s.tenant_id AND a.ap_open_item_id=s.ap_open_item_id JOIN cloudmold_finance_supplier_invoice i ON i.tenant_id=a.tenant_id AND i.supplier_invoice_id=a.supplier_invoice_id WHERE s.tenant_id=#{tenantId}<if test='status!=null'> AND s.status=#{status}</if><if test='keyword!=null'> AND (i.invoice_code LIKE CONCAT('%',#{keyword},'%') OR a.supplier_id LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long countApInstallmentPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("<script>SELECT s.ap_open_item_id,a.supplier_invoice_id,i.invoice_code,a.supplier_id,s.installment_number,s.due_date,a.currency_code,s.amount_minor original_amount_minor,s.settled_amount_minor paid_amount_minor,(s.amount_minor-s.settled_amount_minor) open_amount_minor,s.status,s.updated_at FROM cloudmold_finance_ap_installment s JOIN cloudmold_finance_ap_open_item a ON a.tenant_id=s.tenant_id AND a.ap_open_item_id=s.ap_open_item_id JOIN cloudmold_finance_supplier_invoice i ON i.tenant_id=a.tenant_id AND i.supplier_invoice_id=a.supplier_invoice_id WHERE s.tenant_id=#{tenantId}<if test='status!=null'> AND s.status=#{status}</if><if test='keyword!=null'> AND (i.invoice_code LIKE CONCAT('%',#{keyword},'%') OR a.supplier_id LIKE CONCAT('%',#{keyword},'%'))</if> ORDER BY s.due_date,s.installment_number LIMIT #{offset},#{size}</script>")
    List<ApInstallmentPageItem> selectApInstallmentPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);
    @Select("SELECT s.ap_open_item_id,a.supplier_invoice_id,i.invoice_code,a.supplier_id,s.installment_number,s.due_date,a.currency_code,s.amount_minor original_amount_minor,s.settled_amount_minor paid_amount_minor,(s.amount_minor-s.settled_amount_minor) open_amount_minor,s.status,s.updated_at FROM cloudmold_finance_ap_installment s JOIN cloudmold_finance_ap_open_item a ON a.tenant_id=s.tenant_id AND a.ap_open_item_id=s.ap_open_item_id JOIN cloudmold_finance_supplier_invoice i ON i.tenant_id=a.tenant_id AND i.supplier_invoice_id=a.supplier_invoice_id WHERE s.tenant_id=#{tenantId} AND a.supplier_invoice_id=#{invoiceId} ORDER BY s.due_date,s.installment_number")
    List<ApInstallmentPageItem> selectInvoiceInstallments(@Param("tenantId") Long tenantId,@Param("invoiceId") String invoiceId);

    @Select("<script>SELECT COUNT(*) FROM cloudmold_finance_supplier_payment_instruction p WHERE p.tenant_id=#{tenantId}<if test='status!=null'> AND p.status=#{status}</if><if test='keyword!=null'> AND (p.payment_code LIKE CONCAT('%',#{keyword},'%') OR p.supplier_id LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long countPaymentPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("<script>SELECT p.payment_instruction_id,p.payment_code,p.supplier_id,p.currency_code,p.total_amount_minor,COALESCE((SELECT SUM(a.amount_minor) FROM cloudmold_finance_supplier_payment_allocation a WHERE a.tenant_id=p.tenant_id AND a.payment_instruction_id=p.payment_instruction_id),0) allocated_amount_minor,COALESCE((SELECT SUM(s.settled_amount_minor) FROM cloudmold_finance_supplier_payment_settlement s WHERE s.tenant_id=p.tenant_id AND s.payment_instruction_id=p.payment_instruction_id),0) settled_amount_minor,p.requested_execution_date,(SELECT MAX(e.executed_at) FROM cloudmold_finance_supplier_payment_execution e WHERE e.tenant_id=p.tenant_id AND e.payment_instruction_id=p.payment_instruction_id AND e.status='EXECUTED') executed_at,p.status,p.version aggregate_version,p.updated_at FROM cloudmold_finance_supplier_payment_instruction p WHERE p.tenant_id=#{tenantId}<if test='status!=null'> AND p.status=#{status}</if><if test='keyword!=null'> AND (p.payment_code LIKE CONCAT('%',#{keyword},'%') OR p.supplier_id LIKE CONCAT('%',#{keyword},'%'))</if> ORDER BY p.updated_at DESC LIMIT #{offset},#{size}</script>")
    List<SupplierPaymentPageItem> selectPaymentPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);
    @Select("SELECT DISTINCT p.payment_instruction_id,p.payment_code,p.supplier_id,p.currency_code,p.total_amount_minor,COALESCE((SELECT SUM(a2.amount_minor) FROM cloudmold_finance_supplier_payment_allocation a2 WHERE a2.tenant_id=p.tenant_id AND a2.payment_instruction_id=p.payment_instruction_id),0) allocated_amount_minor,COALESCE((SELECT SUM(s.settled_amount_minor) FROM cloudmold_finance_supplier_payment_settlement s WHERE s.tenant_id=p.tenant_id AND s.payment_instruction_id=p.payment_instruction_id),0) settled_amount_minor,p.requested_execution_date,(SELECT MAX(e.executed_at) FROM cloudmold_finance_supplier_payment_execution e WHERE e.tenant_id=p.tenant_id AND e.payment_instruction_id=p.payment_instruction_id AND e.status='EXECUTED') executed_at,p.status,p.version aggregate_version,p.updated_at FROM cloudmold_finance_supplier_payment_instruction p JOIN cloudmold_finance_supplier_payment_allocation pa ON pa.tenant_id=p.tenant_id AND pa.payment_instruction_id=p.payment_instruction_id JOIN cloudmold_finance_ap_open_item ap ON ap.tenant_id=pa.tenant_id AND ap.ap_open_item_id=pa.ap_open_item_id WHERE p.tenant_id=#{tenantId} AND ap.supplier_invoice_id=#{invoiceId} ORDER BY p.updated_at DESC")
    List<SupplierPaymentPageItem> selectInvoicePayments(@Param("tenantId") Long tenantId,@Param("invoiceId") String invoiceId);

    @Select("<script>SELECT COUNT(*) FROM cloudmold_finance_journal_entry j WHERE j.tenant_id=#{tenantId}<if test='status!=null'> AND j.status=#{status}</if><if test='keyword!=null'> AND (j.journal_code LIKE CONCAT('%',#{keyword},'%') OR j.source_id LIKE CONCAT('%',#{keyword},'%'))</if></script>")
    long countJournalPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword);
    @Select("<script>SELECT j.journal_entry_id,j.journal_code,j.source_type journal_type,j.source_type source_aggregate_type,j.source_id source_aggregate_id,j.accounting_date,j.currency_code,j.debit_total_minor total_debit_amount_minor,j.credit_total_minor total_credit_amount_minor,j.status,rl.reversal_journal_entry_id reversed_by_journal_entry_id,j.version aggregate_version,j.posted_at,j.updated_at FROM cloudmold_finance_journal_entry j LEFT JOIN cloudmold_finance_journal_reversal_link rl ON rl.tenant_id=j.tenant_id AND rl.original_journal_entry_id=j.journal_entry_id WHERE j.tenant_id=#{tenantId}<if test='status!=null'> AND j.status=#{status}</if><if test='keyword!=null'> AND (j.journal_code LIKE CONCAT('%',#{keyword},'%') OR j.source_id LIKE CONCAT('%',#{keyword},'%'))</if> ORDER BY j.updated_at DESC LIMIT #{offset},#{size}</script>")
    List<JournalPageItem> selectJournalPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("keyword") String keyword,@Param("offset") long offset,@Param("size") int size);
    @Select("SELECT j.journal_entry_id,j.journal_code,j.source_type journal_type,j.source_type source_aggregate_type,j.source_id source_aggregate_id,j.accounting_date,j.currency_code,j.debit_total_minor total_debit_amount_minor,j.credit_total_minor total_credit_amount_minor,j.status,rl.reversal_journal_entry_id reversed_by_journal_entry_id,j.version aggregate_version,j.posted_at,j.updated_at,CASE WHEN j.source_type='JOURNAL_REVERSAL' THEN j.source_id END original_journal_entry_id,j.evidence_sha256 posting_evidence_sha256 FROM cloudmold_finance_journal_entry j LEFT JOIN cloudmold_finance_journal_reversal_link rl ON rl.tenant_id=j.tenant_id AND rl.original_journal_entry_id=j.journal_entry_id WHERE j.tenant_id=#{tenantId} AND j.journal_entry_id=#{id}")
    JournalDetail selectJournalDetail(@Param("tenantId") Long tenantId,@Param("id") String id);
    @Select("SELECT l.journal_line_id,l.line_number,l.account_code,a.account_name,l.debit_amount_minor,l.credit_amount_minor FROM cloudmold_finance_journal_line l JOIN cloudmold_finance_account a ON a.tenant_id=l.tenant_id AND a.account_id=l.account_id WHERE l.tenant_id=#{tenantId} AND l.journal_entry_id=#{id} ORDER BY l.line_number")
    List<cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureToPayAdminVOs.JournalLine> selectJournalLineViews(@Param("tenantId") Long tenantId,@Param("id") String id);
    @Select("SELECT t.dimension_code dimension_type,v.value_code dimension_value FROM cloudmold_finance_journal_line_dimension d JOIN cloudmold_finance_dimension_type t ON t.tenant_id=d.tenant_id AND t.dimension_type_id=d.dimension_type_id JOIN cloudmold_finance_dimension_value v ON v.tenant_id=d.tenant_id AND v.dimension_type_id=d.dimension_type_id AND v.dimension_value_id=d.dimension_value_id WHERE d.tenant_id=#{tenantId} AND d.journal_line_id=#{lineId} ORDER BY t.dimension_code")
    List<JournalDimension> selectJournalLineDimensionViews(@Param("tenantId") Long tenantId,@Param("lineId") String lineId);
    @Select("SELECT dimension_type_id,dimension_value_id FROM cloudmold_finance_journal_line_dimension WHERE tenant_id=#{tenantId} AND journal_line_id=#{lineId}")
    List<JournalDimensionAssignment> selectJournalLineDimensions(@Param("tenantId") Long tenantId,@Param("lineId") String lineId);

    @Select("<script>SELECT COUNT(*) FROM cloudmold_finance_match_policy WHERE tenant_id=#{tenantId}<if test='status!=null'> AND status=#{status}</if></script>")
    long countMatchPolicyPage(@Param("tenantId") Long tenantId,@Param("status") String status);
    @Select("<script>SELECT match_policy_id,policy_code,legal_entity_id,policy_version,price_tolerance_amount_minor,tax_tolerance_amount_minor,quantity_tolerance,status FROM cloudmold_finance_match_policy WHERE tenant_id=#{tenantId}<if test='status!=null'> AND status=#{status}</if> ORDER BY policy_code,policy_version DESC LIMIT #{offset},#{size}</script>")
    List<MatchPolicyItem> selectMatchPolicyPage(@Param("tenantId") Long tenantId,@Param("status") String status,@Param("offset") long offset,@Param("size") int size);
    @Insert("INSERT INTO cloudmold_finance_ledger (ledger_id,tenant_id,legal_entity_id,ledger_code,functional_currency_code,status,version,created_at,updated_at) VALUES (#{id},#{tenantId},#{entityId},#{code},#{currency},'ACTIVE',1,#{now},#{now})")
    int insertLedger(@Param("id") String id, @Param("tenantId") Long tenantId,
                     @Param("entityId") String entityId, @Param("code") String code,
                     @Param("currency") String currency, @Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_finance_account (account_id,tenant_id,ledger_id,account_code,account_name,account_type,normal_balance,status,version,created_at,updated_at) VALUES (#{id},#{tenantId},#{ledgerId},#{code},#{name},#{type},#{normal},'ACTIVE',1,#{now},#{now})")
    int insertAccount(@Param("id") String id, @Param("tenantId") Long tenantId,
                      @Param("ledgerId") String ledgerId, @Param("code") String code,
                      @Param("name") String name, @Param("type") String type,
                      @Param("normal") String normal, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_payee_instrument
              (payee_instrument_id,tenant_id,legal_entity_id,supplier_id,instrument_token,masked_account,
               bank_country_code,bank_code,currency_code,verification_status,verification_evidence_sha256,
               valid_from,valid_until,status,version,created_at,updated_at)
            VALUES (#{id},#{tenantId},#{entityId},#{supplierId},#{token},#{masked},#{country},#{bankCode},
                    #{currency},'VERIFIED',#{evidence},#{validFrom},#{validUntil},'ACTIVE',1,#{now},#{now})
            """)
    int insertPayeeInstrument(@Param("id") String id, @Param("tenantId") Long tenantId,
                              @Param("entityId") String entityId, @Param("supplierId") String supplierId,
                              @Param("token") String token, @Param("masked") String masked,
                              @Param("country") String country, @Param("bankCode") String bankCode,
                              @Param("currency") String currency, @Param("evidence") String evidence,
                              @Param("validFrom") LocalDate validFrom, @Param("validUntil") LocalDate validUntil,
                              @Param("now") LocalDateTime now);

    @Select("SELECT payee_instrument_id,tenant_id,legal_entity_id,supplier_id,currency_code,status,verification_status,valid_from,valid_until FROM cloudmold_finance_supplier_payee_instrument WHERE tenant_id=#{tenantId} AND payee_instrument_id=#{id} FOR UPDATE")
    PayeeInstrument selectPayeeInstrumentForUpdate(@Param("tenantId") Long tenantId, @Param("id") String id);

    @Insert("""
            INSERT INTO cloudmold_finance_match_policy
              (match_policy_id,tenant_id,policy_code,legal_entity_id,policy_version,price_tolerance_amount_minor,
               tax_tolerance_amount_minor,quantity_tolerance,status,created_at)
            VALUES (#{matchPolicyId},#{tenantId},#{policyCode},#{legalEntityId},#{policyVersion},
                    #{priceToleranceAmountMinor},#{taxToleranceAmountMinor},#{quantityTolerance},#{status},#{createdAt})
            """) int insertMatchPolicy(MatchPolicy value);

    @Select("""
            SELECT match_policy_id,tenant_id,policy_code,legal_entity_id,policy_version,
                   price_tolerance_amount_minor,tax_tolerance_amount_minor,quantity_tolerance,status,created_at
              FROM cloudmold_finance_match_policy
             WHERE tenant_id=#{tenantId} AND match_policy_id=#{id} AND policy_version=#{version}
            """) MatchPolicy selectMatchPolicy(@Param("tenantId") Long tenantId, @Param("id") String id,
                                                 @Param("version") Long version);

    @Insert("INSERT INTO cloudmold_finance_payment_term (payment_term_id,tenant_id,term_code,legal_entity_id,term_version,status,created_at) VALUES (#{id},#{tenantId},#{code},#{entityId},#{version},'ACTIVE',#{now})")
    int insertPaymentTerm(@Param("id") String id, @Param("tenantId") Long tenantId,
                          @Param("code") String code, @Param("entityId") String entityId,
                          @Param("version") Long version, @Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_finance_payment_term_installment_rule (installment_rule_id,tenant_id,payment_term_id,term_version,installment_number,due_days_after_issue,allocation_basis_points) VALUES (#{installmentRuleId},#{tenantId},#{paymentTermId},#{termVersion},#{installmentNumber},#{dueDaysAfterIssue},#{allocationBasisPoints})")
    int insertPaymentTermRule(PaymentTermRule value);

    @Select("SELECT installment_rule_id,tenant_id,payment_term_id,term_version,installment_number,due_days_after_issue,allocation_basis_points FROM cloudmold_finance_payment_term_installment_rule WHERE tenant_id=#{tenantId} AND payment_term_id=#{id} AND term_version=#{version} ORDER BY installment_number")
    List<PaymentTermRule> selectPaymentTermRules(@Param("tenantId") Long tenantId,
                                                 @Param("id") String id, @Param("version") Long version);

    @Select("SELECT COUNT(*) FROM cloudmold_finance_payment_term WHERE tenant_id=#{tenantId} AND payment_term_id=#{id} AND term_version=#{version} AND legal_entity_id=#{entityId} AND status='ACTIVE'")
    int countActivePaymentTerm(@Param("tenantId") Long tenantId, @Param("id") String id,
                               @Param("version") Long version, @Param("entityId") String entityId);

    @Insert("INSERT INTO cloudmold_finance_posting_rule (posting_rule_id,tenant_id,rule_code,ledger_id,source_type,rule_version,status,created_at) VALUES (#{id},#{tenantId},#{code},#{ledgerId},#{sourceType},#{version},'ACTIVE',#{now})")
    int insertPostingRule(@Param("id") String id, @Param("tenantId") Long tenantId,
                          @Param("code") String code, @Param("ledgerId") String ledgerId,
                          @Param("sourceType") String sourceType, @Param("version") Long version,
                          @Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_finance_posting_rule_line (posting_rule_line_id,tenant_id,posting_rule_id,rule_version,ledger_id,account_role,account_id) VALUES (#{lineId},#{tenantId},#{ruleId},#{version},#{ledgerId},#{role},#{accountId})")
    int insertPostingRuleLine(@Param("lineId") String lineId, @Param("tenantId") Long tenantId,
                              @Param("ruleId") String ruleId, @Param("version") Long version,
                              @Param("ledgerId") String ledgerId, @Param("role") String role,
                              @Param("accountId") String accountId);

    @Select("""
            SELECT l.account_role,r.ledger_id,a.account_id,a.account_code
              FROM cloudmold_finance_posting_rule r
              JOIN cloudmold_finance_posting_rule_line l
                ON l.tenant_id=r.tenant_id AND l.posting_rule_id=r.posting_rule_id AND l.rule_version=r.rule_version
              JOIN cloudmold_finance_account a ON a.tenant_id=l.tenant_id AND a.ledger_id=l.ledger_id
               AND a.account_id=l.account_id
             WHERE r.tenant_id=#{tenantId} AND r.posting_rule_id=#{ruleId} AND r.rule_version=#{version}
               AND r.ledger_id=#{ledgerId} AND r.source_type=#{sourceType} AND r.status='ACTIVE' AND a.status='ACTIVE'
            """)
    List<PostingAccount> selectPostingAccounts(@Param("tenantId") Long tenantId,
                                               @Param("ruleId") String ruleId,
                                               @Param("version") Long version,
                                               @Param("ledgerId") String ledgerId,
                                               @Param("sourceType") String sourceType);

    @Insert("INSERT INTO cloudmold_finance_dimension_type (dimension_type_id,tenant_id,dimension_code,dimension_name,status,version,created_at) VALUES (#{id},#{tenantId},#{code},#{name},'ACTIVE',1,#{now})")
    int insertDimensionType(@Param("id") String id, @Param("tenantId") Long tenantId,
                            @Param("code") String code, @Param("name") String name,
                            @Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_finance_dimension_value (dimension_value_id,tenant_id,dimension_type_id,value_code,value_name,status,version,created_at) VALUES (#{id},#{tenantId},#{typeId},#{code},#{name},'ACTIVE',1,#{now})")
    int insertDimensionValue(@Param("id") String id, @Param("tenantId") Long tenantId,
                             @Param("typeId") String typeId, @Param("code") String code,
                             @Param("name") String name, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM cloudmold_finance_dimension_value WHERE tenant_id=#{tenantId} AND dimension_type_id=#{typeId} AND dimension_value_id=#{valueId} AND status='ACTIVE'")
    int countActiveDimensionValue(@Param("tenantId") Long tenantId, @Param("typeId") String typeId,
                                  @Param("valueId") String valueId);

    @Insert("INSERT INTO cloudmold_finance_inventory_valuation_policy (valuation_policy_id,tenant_id,policy_code,ledger_id,policy_version,cost_method,status,created_at) VALUES (#{id},#{tenantId},#{code},#{ledgerId},#{version},#{method},'ACTIVE',#{now})")
    int insertValuationPolicy(@Param("id") String id, @Param("tenantId") Long tenantId,
                              @Param("code") String code, @Param("ledgerId") String ledgerId,
                              @Param("version") String version, @Param("method") String method,
                              @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM cloudmold_finance_inventory_valuation_policy WHERE tenant_id=#{tenantId} AND valuation_policy_id=#{id} AND policy_version=#{version} AND ledger_id=#{ledgerId} AND status='ACTIVE'")
    int countActiveValuationPolicy(@Param("tenantId") Long tenantId, @Param("id") String id,
                                   @Param("version") String version, @Param("ledgerId") String ledgerId);
    @Insert("""
            INSERT INTO cloudmold_finance_operation
              (tenant_id,idempotency_key,command_type,request_hash,attempt_token,status,created_at,updated_at)
            VALUES (#{tenantId},#{idempotencyKey},#{commandType},#{requestHash},#{attemptToken},0,#{now},#{now})
            ON DUPLICATE KEY UPDATE operation_id=LAST_INSERT_ID(operation_id)
            """)
    int insertOrResolveOperation(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey,
                                 @Param("commandType") String commandType, @Param("requestHash") String requestHash,
                                 @Param("attemptToken") String attemptToken, @Param("now") LocalDateTime now);

    @Select("SELECT LAST_INSERT_ID()") Long selectLastInsertId();

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

    @Insert("""
            INSERT INTO cloudmold_finance_event_inbox
              (tenant_id,source_event_id,source_event_type,source_schema_version,source_aggregate_id,
               source_aggregate_version,evidence_sha256,attempt_token,source_occurred_at,received_at)
            VALUES (#{tenantId},#{sourceEventId},#{sourceEventType},#{sourceSchemaVersion},#{sourceAggregateId},
                    #{sourceAggregateVersion},#{evidenceSha256},#{attemptToken},#{sourceOccurredAt},#{receivedAt})
            ON DUPLICATE KEY UPDATE inbox_id=LAST_INSERT_ID(inbox_id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "inboxId")
    int insertInbox(EventInbox value);

    @Select("""
            SELECT inbox_id,tenant_id,source_event_id,source_event_type,source_schema_version,
                   source_aggregate_id,source_aggregate_version,evidence_sha256,attempt_token,
                   source_occurred_at,received_at
              FROM cloudmold_finance_event_inbox
             WHERE tenant_id=#{tenantId} AND inbox_id=#{inboxId} FOR UPDATE
            """)
    EventInbox selectInboxForUpdate(@Param("tenantId") Long tenantId, @Param("inboxId") Long inboxId);

    @Select("SELECT po_line_evidence_id FROM cloudmold_finance_po_line_evidence WHERE tenant_id=#{tenantId} AND inbox_id=#{inboxId}")
    String selectPoEvidenceIdByInbox(@Param("tenantId") Long tenantId, @Param("inboxId") Long inboxId);

    @Select("SELECT receipt_line_evidence_id FROM cloudmold_finance_receipt_line_evidence WHERE tenant_id=#{tenantId} AND inbox_id=#{inboxId}")
    String selectReceiptEvidenceIdByInbox(@Param("tenantId") Long tenantId, @Param("inboxId") Long inboxId);

    @Select("SELECT quality_evidence_id FROM cloudmold_finance_quality_disposition_evidence WHERE tenant_id=#{tenantId} AND inbox_id=#{inboxId}")
    String selectQualityEvidenceIdByInbox(@Param("tenantId") Long tenantId, @Param("inboxId") Long inboxId);

    @Select("SELECT inventory_evidence_id FROM cloudmold_finance_inventory_movement_evidence WHERE tenant_id=#{tenantId} AND inbox_id=#{inboxId}")
    String selectInventoryEvidenceIdByInbox(@Param("tenantId") Long tenantId, @Param("inboxId") Long inboxId);

    @Select("SELECT MAX(source_version) FROM cloudmold_finance_po_line_evidence WHERE tenant_id=#{tenantId} AND purchase_order_item_id=#{aggregateId}")
    Long selectMaxPoEvidenceVersion(@Param("tenantId") Long tenantId, @Param("aggregateId") String aggregateId);

    @Select("SELECT MAX(source_version) FROM cloudmold_finance_receipt_line_evidence WHERE tenant_id=#{tenantId} AND receipt_line_id=#{aggregateId}")
    Long selectMaxReceiptEvidenceVersion(@Param("tenantId") Long tenantId, @Param("aggregateId") String aggregateId);

    @Select("SELECT MAX(source_version) FROM cloudmold_finance_quality_disposition_evidence WHERE tenant_id=#{tenantId} AND quality_disposition_id=#{aggregateId}")
    Long selectMaxQualityEvidenceVersion(@Param("tenantId") Long tenantId, @Param("aggregateId") String aggregateId);

    @Select("SELECT MAX(source_version) FROM cloudmold_finance_inventory_movement_evidence WHERE tenant_id=#{tenantId} AND inventory_movement_id=#{aggregateId}")
    Long selectMaxInventoryEvidenceVersion(@Param("tenantId") Long tenantId, @Param("aggregateId") String aggregateId);

    @Select("""
            SELECT po_line_evidence_id,tenant_id,inbox_id,purchase_order_id,purchase_order_item_id,
                   delivery_schedule_id,legal_entity_id,supplier_id,currency_code,ordered_quantity,
                   unit_of_measure,unit_net_price,net_amount_minor,tax_amount_minor,gross_amount_minor,
                   source_version,created_at
              FROM cloudmold_finance_po_line_evidence
             WHERE tenant_id=#{tenantId} AND purchase_order_item_id=#{aggregateId}
               AND source_version=#{version} FOR UPDATE
            """)
    PurchaseOrderLineEvidence selectPoEvidenceForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("aggregateId") String aggregateId,
                                                        @Param("version") Long version);

    @Select("""
            SELECT receipt_line_evidence_id,tenant_id,inbox_id,receipt_id,receipt_line_id,purchase_order_id,
                   purchase_order_item_id,purchase_order_line_version,delivery_schedule_id,received_quantity,
                   unit_of_measure,source_version,created_at
              FROM cloudmold_finance_receipt_line_evidence
             WHERE tenant_id=#{tenantId} AND receipt_line_id=#{aggregateId}
               AND source_version=#{version} FOR UPDATE
            """)
    ReceiptLineEvidence selectReceiptEvidenceForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("aggregateId") String aggregateId,
                                                       @Param("version") Long version);

    @Select("""
            SELECT quality_evidence_id,tenant_id,inbox_id,quality_disposition_id,receipt_line_id,
                   receipt_line_version,purchase_order_item_id,inspected_quantity,accepted_quantity,
                   rejected_quantity,held_quantity,unit_of_measure,source_version,created_at
              FROM cloudmold_finance_quality_disposition_evidence
             WHERE tenant_id=#{tenantId} AND quality_disposition_id=#{aggregateId}
               AND source_version=#{version} FOR UPDATE
            """)
    QualityDispositionEvidence selectQualityEvidenceForUpdate(@Param("tenantId") Long tenantId,
                                                              @Param("aggregateId") String aggregateId,
                                                              @Param("version") Long version);

    @Insert("""
            INSERT INTO cloudmold_finance_po_line_evidence
              (po_line_evidence_id,tenant_id,inbox_id,purchase_order_id,purchase_order_item_id,
               delivery_schedule_id,legal_entity_id,supplier_id,currency_code,ordered_quantity,unit_of_measure,
               unit_net_price,net_amount_minor,tax_amount_minor,gross_amount_minor,source_version,created_at)
            VALUES (#{poLineEvidenceId},#{tenantId},#{inboxId},#{purchaseOrderId},#{purchaseOrderItemId},
                    #{deliveryScheduleId},#{legalEntityId},#{supplierId},#{currencyCode},#{orderedQuantity},
                    #{unitOfMeasure},#{unitNetPrice},#{netAmountMinor},#{taxAmountMinor},#{grossAmountMinor},
                    #{sourceVersion},#{createdAt})
            """) int insertPurchaseOrderLineEvidence(PurchaseOrderLineEvidence value);

    @Insert("""
            INSERT INTO cloudmold_finance_receipt_line_evidence
              (receipt_line_evidence_id,tenant_id,inbox_id,receipt_id,receipt_line_id,purchase_order_id,
               purchase_order_item_id,purchase_order_line_version,delivery_schedule_id,received_quantity,
               unit_of_measure,source_version,created_at)
            VALUES (#{receiptLineEvidenceId},#{tenantId},#{inboxId},#{receiptId},#{receiptLineId},#{purchaseOrderId},
                    #{purchaseOrderItemId},#{purchaseOrderLineVersion},#{deliveryScheduleId},#{receivedQuantity},
                    #{unitOfMeasure},#{sourceVersion},#{createdAt})
            """) int insertReceiptLineEvidence(ReceiptLineEvidence value);

    @Insert("""
            INSERT INTO cloudmold_finance_quality_disposition_evidence
              (quality_evidence_id,tenant_id,inbox_id,quality_disposition_id,receipt_line_id,receipt_line_version,purchase_order_item_id,
               inspected_quantity,accepted_quantity,rejected_quantity,held_quantity,unit_of_measure,source_version,created_at)
            VALUES (#{qualityEvidenceId},#{tenantId},#{inboxId},#{qualityDispositionId},#{receiptLineId},
                    #{receiptLineVersion},#{purchaseOrderItemId},#{inspectedQuantity},#{acceptedQuantity},#{rejectedQuantity},
                    #{heldQuantity},#{unitOfMeasure},#{sourceVersion},#{createdAt})
            """) int insertQualityDispositionEvidence(QualityDispositionEvidence value);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_movement_evidence
              (inventory_evidence_id,tenant_id,inbox_id,inventory_movement_id,receipt_line_id,
               quality_disposition_id,quality_disposition_version,disposition,purchase_order_item_id,movement_quantity,unit_of_measure,
               unit_cost_amount_minor,movement_cost_amount_minor,currency_code,valuation_policy_id,
               valuation_policy_version,source_version,created_at)
            VALUES (#{inventoryEvidenceId},#{tenantId},#{inboxId},#{inventoryMovementId},#{receiptLineId},
                    #{qualityDispositionId},#{qualityDispositionVersion},#{disposition},#{purchaseOrderItemId},#{movementQuantity},#{unitOfMeasure},
                    #{unitCostAmountMinor},#{movementCostAmountMinor},#{currencyCode},#{valuationPolicyId},
                    #{valuationPolicyVersion},#{sourceVersion},#{createdAt})
            """) int insertInventoryMovementEvidence(InventoryMovementEvidence value);

    @Select("""
            SELECT inventory_evidence_id,tenant_id,inbox_id,inventory_movement_id,receipt_line_id,
                   quality_disposition_id,quality_disposition_version,disposition,purchase_order_item_id,movement_quantity,unit_of_measure,
                   unit_cost_amount_minor,movement_cost_amount_minor,currency_code,valuation_policy_id,
                   valuation_policy_version,source_version,created_at
              FROM cloudmold_finance_inventory_movement_evidence
             WHERE tenant_id=#{tenantId} AND inventory_movement_id=#{movementId}
               AND source_version=#{version} FOR UPDATE
            """)
    InventoryMovementEvidence selectInventoryEvidenceForUpdate(@Param("tenantId") Long tenantId,
                                                               @Param("movementId") String movementId,
                                                               @Param("version") Long version);

    @Select("""
            SELECT po.po_line_evidence_id,po.tenant_id,po.inbox_id,po.purchase_order_id,
                   po.purchase_order_item_id,po.delivery_schedule_id,po.legal_entity_id,po.supplier_id,
                   po.currency_code,po.ordered_quantity,po.unit_of_measure,po.unit_net_price,
                   po.net_amount_minor,po.tax_amount_minor,po.gross_amount_minor,po.source_version,po.created_at
              FROM cloudmold_finance_inventory_movement_evidence i
              JOIN cloudmold_finance_quality_disposition_evidence q
                ON q.tenant_id=i.tenant_id AND q.quality_disposition_id=i.quality_disposition_id
               AND q.source_version=i.quality_disposition_version
              JOIN cloudmold_finance_receipt_line_evidence r
                ON r.tenant_id=q.tenant_id AND r.receipt_line_id=q.receipt_line_id
               AND r.source_version=q.receipt_line_version
              JOIN cloudmold_finance_po_line_evidence po
                ON po.tenant_id=r.tenant_id AND po.purchase_order_item_id=r.purchase_order_item_id
               AND po.source_version=r.purchase_order_line_version
             WHERE i.tenant_id=#{tenantId} AND i.inventory_movement_id=#{movementId}
               AND i.source_version=#{version} FOR UPDATE
            """)
    PurchaseOrderLineEvidence selectPoEvidenceForInventory(@Param("tenantId") Long tenantId,
                                                           @Param("movementId") String movementId,
                                                           @Param("version") Long version);

    @Select("SELECT inbox_id,tenant_id,source_event_id,source_event_type,source_schema_version,source_aggregate_id,source_aggregate_version,evidence_sha256,attempt_token,source_occurred_at,received_at FROM cloudmold_finance_event_inbox WHERE tenant_id=#{tenantId} AND inbox_id=#{inboxId}")
    EventInbox selectInbox(@Param("tenantId") Long tenantId, @Param("inboxId") Long inboxId);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_invoice
              (supplier_invoice_id,tenant_id,invoice_code,legal_entity_id,ledger_id,accounting_period_id,
               supplier_id,supplier_invoice_number,invoice_type,currency_code,issue_date,accounting_date,due_date,
               net_amount_minor,tax_amount_minor,gross_amount_minor,lifecycle_status,match_status,settlement_status,
               evidence_sha256,payment_term_id,payment_term_version,posting_rule_id,posting_rule_version,
               created_by_principal_id,version,created_at,updated_at)
            VALUES (#{supplierInvoiceId},#{tenantId},#{invoiceCode},#{legalEntityId},#{ledgerId},#{accountingPeriodId},
                    #{supplierId},#{supplierInvoiceNumber},#{invoiceType},#{currencyCode},#{issueDate},#{accountingDate},
                    #{dueDate},#{netAmountMinor},#{taxAmountMinor},#{grossAmountMinor},#{lifecycleStatus},#{matchStatus},
                    #{settlementStatus},#{evidenceSha256},#{paymentTermId},#{paymentTermVersion},
                    #{postingRuleId},#{postingRuleVersion},
                    #{createdByPrincipalId},#{version},#{createdAt},#{updatedAt})
            """) int insertSupplierInvoice(SupplierInvoice value);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_invoice_line
              (invoice_line_id,tenant_id,supplier_invoice_id,line_number,purchase_order_id,purchase_order_item_id,
               sku_id,quantity,unit_of_measure,unit_net_price,net_amount_minor,tax_code,tax_rate,tax_amount_minor,
               gross_amount_minor,version,created_at,updated_at)
            VALUES (#{invoiceLineId},#{tenantId},#{supplierInvoiceId},#{lineNumber},#{purchaseOrderId},
                    #{purchaseOrderItemId},#{skuId},#{quantity},#{unitOfMeasure},#{unitNetPrice},#{netAmountMinor},
                    #{taxCode},#{taxRate},#{taxAmountMinor},#{grossAmountMinor},#{version},#{createdAt},#{updatedAt})
            """) int insertSupplierInvoiceLine(SupplierInvoiceLine value);

    @Select("""
            SELECT supplier_invoice_id,tenant_id,invoice_code,legal_entity_id,ledger_id,accounting_period_id,
                   supplier_id,supplier_invoice_number,invoice_type,currency_code,issue_date,accounting_date,due_date,
                   net_amount_minor,tax_amount_minor,gross_amount_minor,lifecycle_status,match_status,settlement_status,
                   evidence_sha256,payment_term_id,payment_term_version,posting_rule_id,posting_rule_version,
                   created_by_principal_id,
                   approved_by_principal_id,posted_journal_entry_id,
                   version,created_at,updated_at
              FROM cloudmold_finance_supplier_invoice
             WHERE tenant_id=#{tenantId} AND supplier_invoice_id=#{invoiceId} FOR UPDATE
            """) SupplierInvoice selectInvoiceForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("invoiceId") String invoiceId);

    @Select("""
            SELECT invoice_line_id,tenant_id,supplier_invoice_id,line_number,purchase_order_id,purchase_order_item_id,
                   sku_id,quantity,unit_of_measure,unit_net_price,net_amount_minor,tax_code,tax_rate,tax_amount_minor,
                   gross_amount_minor,version,created_at,updated_at
              FROM cloudmold_finance_supplier_invoice_line
             WHERE tenant_id=#{tenantId} AND supplier_invoice_id=#{invoiceId} ORDER BY line_number
            """) List<SupplierInvoiceLine> selectInvoiceLines(@Param("tenantId") Long tenantId,
                                                               @Param("invoiceId") String invoiceId);

    @Update("""
            UPDATE cloudmold_finance_supplier_invoice SET lifecycle_status=#{toStatus},
                   approved_by_principal_id=CASE WHEN #{toStatus}='APPROVED' THEN #{actor} ELSE approved_by_principal_id END,
                   version=version+1,updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND supplier_invoice_id=#{invoiceId}
               AND lifecycle_status=#{fromStatus} AND version=#{expectedVersion}
            """)
    int transitionInvoice(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId,
                          @Param("expectedVersion") Long expectedVersion, @Param("fromStatus") String fromStatus,
                          @Param("toStatus") String toStatus, @Param("actor") String actor,
                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_supplier_invoice SET match_status=#{toStatus},version=version+1,updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND supplier_invoice_id=#{invoiceId}
               AND match_status=#{fromStatus} AND version=#{expectedVersion}
            """)
    int transitionInvoiceMatch(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId,
                               @Param("expectedVersion") Long expectedVersion, @Param("fromStatus") String fromStatus,
                               @Param("toStatus") String toStatus, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_invoice_status_history
              (tenant_id,supplier_invoice_id,from_status,to_status,match_status,actor_principal_id,
               reason_code,aggregate_version,occurred_at)
            VALUES (#{tenantId},#{invoiceId},#{fromStatus},#{toStatus},#{matchStatus},#{actor},
                    #{reason},#{version},#{now})
            """)
    int insertInvoiceHistory(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId,
                             @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                             @Param("matchStatus") String matchStatus, @Param("actor") String actor,
                             @Param("reason") String reason, @Param("version") Long version,
                             @Param("now") LocalDateTime now);

    @Select("""
            SELECT po_line_evidence_id,tenant_id,inbox_id,purchase_order_id,purchase_order_item_id,
                   delivery_schedule_id,legal_entity_id,supplier_id,currency_code,ordered_quantity,
                   unit_of_measure,unit_net_price,net_amount_minor,tax_amount_minor,gross_amount_minor,
                   source_version,created_at
              FROM cloudmold_finance_po_line_evidence
             WHERE tenant_id=#{tenantId} AND purchase_order_item_id=#{poItemId}
             ORDER BY source_version DESC LIMIT 1 FOR UPDATE
            """)
    PurchaseOrderLineEvidence selectLatestPoLineForUpdate(@Param("tenantId") Long tenantId,
                                                          @Param("poItemId") String poItemId);

    @Select("""
            SELECT q.receipt_line_id,q.receipt_line_version,q.quality_disposition_id,
                   q.source_version quality_disposition_version,i.inventory_movement_id,
                   i.source_version inventory_movement_version,q.purchase_order_item_id,
                   i.movement_quantity accepted_quantity,
                   COALESCE((SELECT SUM(a.allocated_quantity)
                               FROM cloudmold_finance_invoice_match_receipt_allocation a
                              WHERE a.tenant_id=i.tenant_id AND a.inventory_movement_id=i.inventory_movement_id
                                AND a.inventory_movement_version=i.source_version
                                AND a.status='ACTIVE'),0) AS previously_allocated_quantity,
                   q.unit_of_measure
              FROM cloudmold_finance_quality_disposition_evidence q
              JOIN cloudmold_finance_inventory_movement_evidence i
                ON i.tenant_id=q.tenant_id AND i.quality_disposition_id=q.quality_disposition_id
               AND i.quality_disposition_version=q.source_version
               AND i.source_version=(SELECT MAX(i2.source_version)
                                       FROM cloudmold_finance_inventory_movement_evidence i2
                                      WHERE i2.tenant_id=i.tenant_id
                                        AND i2.inventory_movement_id=i.inventory_movement_id)
             WHERE q.tenant_id=#{tenantId} AND q.purchase_order_item_id=#{poItemId}
               AND i.disposition='ACCEPTED'
               AND q.source_version=(SELECT MAX(q2.source_version)
                                       FROM cloudmold_finance_quality_disposition_evidence q2
                                      WHERE q2.tenant_id=q.tenant_id
                                        AND q2.quality_disposition_id=q.quality_disposition_id)
             ORDER BY q.created_at,q.quality_disposition_id
            """)
    List<MatchCandidate> selectMatchCandidates(@Param("tenantId") Long tenantId,
                                               @Param("poItemId") String poItemId);

    @Insert("""
            INSERT INTO cloudmold_finance_invoice_match_run
              (match_run_id,tenant_id,supplier_invoice_id,invoice_version,match_policy_id,match_policy_version,
               price_tolerance_amount_minor,tax_tolerance_amount_minor,quantity_tolerance,status,
               requested_by_principal_id,version,created_at,completed_at)
            VALUES (#{matchRunId},#{tenantId},#{supplierInvoiceId},#{invoiceVersion},#{matchPolicyId},
                    #{matchPolicyVersion},#{priceToleranceAmountMinor},#{taxToleranceAmountMinor},
                    #{quantityTolerance},#{status},#{requestedByPrincipalId},#{version},#{createdAt},#{completedAt})
            """) int insertMatchRun(MatchRun value);

    @Insert("""
            INSERT INTO cloudmold_finance_invoice_match_line
              (match_line_id,tenant_id,match_run_id,invoice_line_id,purchase_order_item_id,
               purchase_order_line_version,invoice_quantity,
               eligible_quantity,invoice_net_amount_minor,expected_po_net_amount_minor,
               price_difference_amount_minor,tax_difference_amount_minor,result_status,created_at)
            VALUES (#{matchLineId},#{tenantId},#{matchRunId},#{invoiceLineId},#{purchaseOrderItemId},
                    #{purchaseOrderLineVersion},
                    #{invoiceQuantity},#{eligibleQuantity},#{invoiceNetAmountMinor},#{expectedPoNetAmountMinor},
                    #{priceDifferenceAmountMinor},#{taxDifferenceAmountMinor},#{resultStatus},#{createdAt})
            """) int insertMatchLine(MatchLine value);

    @Insert("""
            INSERT INTO cloudmold_finance_invoice_match_receipt_allocation
              (match_allocation_id,tenant_id,match_run_id,match_line_id,invoice_line_id,receipt_line_id,
               receipt_line_version,quality_disposition_id,quality_disposition_version,
               inventory_movement_id,inventory_movement_version,allocated_quantity,unit_of_measure,status,created_at)
            VALUES (#{matchAllocationId},#{tenantId},#{matchRunId},#{matchLineId},#{invoiceLineId},
                    #{receiptLineId},#{receiptLineVersion},#{qualityDispositionId},#{qualityDispositionVersion},
                    #{inventoryMovementId},#{inventoryMovementVersion},#{allocatedQuantity},
                    #{unitOfMeasure},#{status},#{createdAt})
            """) int insertMatchAllocation(MatchAllocation value);

    @Insert("""
            INSERT INTO cloudmold_finance_invoice_match_exception
              (exception_id,tenant_id,match_run_id,match_line_id,exception_code,exception_type,blocking,expected_quantity,
               actual_quantity,expected_amount_minor,actual_amount_minor,expected_code,actual_code,
               status,opened_by_principal_id,version,opened_at)
            VALUES (#{exceptionId},#{tenantId},#{matchRunId},#{matchLineId},#{exceptionCode},#{exceptionType},#{blocking},
                    #{expectedQuantity},#{actualQuantity},#{expectedAmountMinor},#{actualAmountMinor},
                    #{expectedCode},#{actualCode},#{status},#{openedByPrincipalId},#{version},#{openedAt})
            """) int insertMatchException(MatchException value);

    @Update("""
            UPDATE cloudmold_finance_invoice_match_run SET status=#{status},version=version+1,completed_at=#{now}
             WHERE tenant_id=#{tenantId} AND match_run_id=#{runId} AND status='RUNNING' AND version=1
            """)
    int completeMatchRun(@Param("tenantId") Long tenantId, @Param("runId") String runId,
                         @Param("status") String status, @Param("now") LocalDateTime now);

    @Update("UPDATE cloudmold_finance_invoice_match_run SET status='SUPERSEDED',version=version+1 WHERE tenant_id=#{tenantId} AND supplier_invoice_id=#{invoiceId} AND status='EXCEPTION'")
    int supersedeExceptionMatchRuns(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId);

    @Update("UPDATE cloudmold_finance_invoice_match_receipt_allocation a JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=a.tenant_id AND r.match_run_id=a.match_run_id SET a.status='SUPERSEDED' WHERE a.tenant_id=#{tenantId} AND r.supplier_invoice_id=#{invoiceId} AND r.status='SUPERSEDED' AND a.status='PENDING_OVERRIDE'")
    int supersedePendingMatchAllocations(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId);

    @Update("UPDATE cloudmold_finance_invoice_match_exception e JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=e.tenant_id AND r.match_run_id=e.match_run_id SET e.status='SUPERSEDED',e.version=e.version+1,e.resolved_at=#{now} WHERE e.tenant_id=#{tenantId} AND r.supplier_invoice_id=#{invoiceId} AND r.status='SUPERSEDED' AND e.status='OPEN'")
    int supersedeOpenMatchExceptions(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId,
                                     @Param("now") LocalDateTime now);

    @Select("""
            SELECT exception_id,tenant_id,match_run_id,match_line_id,exception_code,exception_type,blocking,
                   expected_quantity,actual_quantity,expected_amount_minor,actual_amount_minor,
                   expected_code,actual_code,status,opened_by_principal_id,resolved_by_principal_id,resolution_evidence_sha256,
                   reason_code,version,opened_at,resolved_at
              FROM cloudmold_finance_invoice_match_exception
             WHERE tenant_id=#{tenantId} AND exception_id=#{exceptionId} FOR UPDATE
            """)
    MatchException selectMatchExceptionForUpdate(@Param("tenantId") Long tenantId,
                                                  @Param("exceptionId") String exceptionId);

    @Select("SELECT COUNT(*) FROM cloudmold_finance_invoice_match_exception e JOIN cloudmold_finance_invoice_match_run r ON r.tenant_id=e.tenant_id AND r.match_run_id=e.match_run_id JOIN cloudmold_finance_supplier_invoice i ON i.tenant_id=r.tenant_id AND i.supplier_invoice_id=r.supplier_invoice_id WHERE e.tenant_id=#{tenantId} AND e.exception_id=#{exceptionId} AND e.status='OPEN' AND r.status='EXCEPTION' AND i.match_status='EXCEPTION'")
    int countCurrentlyOverridableException(@Param("tenantId") Long tenantId,
                                           @Param("exceptionId") String exceptionId);

    @Update("""
            UPDATE cloudmold_finance_invoice_match_exception
               SET status='OVERRIDE_APPROVED',resolved_by_principal_id=#{actor},
                   resolution_evidence_sha256=#{evidence},reason_code=#{reason},version=version+1,
                   resolved_at=#{now}
             WHERE tenant_id=#{tenantId} AND exception_id=#{exceptionId}
               AND status='OPEN' AND version=#{expectedVersion}
            """)
    int approveMatchException(@Param("tenantId") Long tenantId, @Param("exceptionId") String exceptionId,
                              @Param("expectedVersion") Long expectedVersion, @Param("actor") String actor,
                              @Param("evidence") String evidence, @Param("reason") String reason,
                              @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_invoice_match_exception_resolution
              (resolution_id,tenant_id,exception_id,resolution_type,actor_principal_id,evidence_sha256,
               reason_code,occurred_at)
            VALUES (#{resolutionId},#{tenantId},#{exceptionId},'OVERRIDE_APPROVED',#{actor},#{evidence},#{reason},#{now})
            """)
    int insertOverrideResolution(@Param("resolutionId") String resolutionId, @Param("tenantId") Long tenantId,
                                 @Param("exceptionId") String exceptionId, @Param("actor") String actor,
                                 @Param("evidence") String evidence, @Param("reason") String reason,
                                 @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM cloudmold_finance_invoice_match_exception WHERE tenant_id=#{tenantId} AND match_run_id=#{runId} AND status='OPEN'")
    int countOpenMatchExceptions(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Update("UPDATE cloudmold_finance_invoice_match_receipt_allocation SET status='ACTIVE' WHERE tenant_id=#{tenantId} AND match_run_id=#{runId} AND status='PENDING_OVERRIDE'")
    int activateMatchAllocations(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("SELECT supplier_invoice_id FROM cloudmold_finance_invoice_match_run WHERE tenant_id=#{tenantId} AND match_run_id=#{runId}")
    String selectInvoiceIdByMatchRun(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Update("UPDATE cloudmold_finance_invoice_match_run SET status='MATCHED',version=version+1 WHERE tenant_id=#{tenantId} AND match_run_id=#{runId} AND status='EXCEPTION'")
    int markMatchRunOverridden(@Param("tenantId") Long tenantId, @Param("runId") String runId);

    @Select("""
            SELECT COALESCE(SUM(l.expected_po_net_amount_minor),0)
              FROM cloudmold_finance_invoice_match_line l
              JOIN cloudmold_finance_invoice_match_run r ON r.match_run_id=l.match_run_id
             WHERE l.tenant_id=#{tenantId} AND r.supplier_invoice_id=#{invoiceId} AND r.status='MATCHED'
            """)
    Long sumMatchedPoNet(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId);

    @Insert("""
            INSERT INTO cloudmold_finance_ap_open_item
              (ap_open_item_id,tenant_id,supplier_invoice_id,legal_entity_id,ledger_id,supplier_id,currency_code,
               original_amount_minor,settled_amount_minor,open_amount_minor,due_date,status,version,created_at,updated_at)
            VALUES (#{apOpenItemId},#{tenantId},#{supplierInvoiceId},#{legalEntityId},#{ledgerId},#{supplierId},
                    #{currencyCode},#{originalAmountMinor},#{settledAmountMinor},#{openAmountMinor},#{dueDate},
                    #{status},#{version},#{createdAt},#{updatedAt})
            """) int insertApOpenItem(ApOpenItem value);

    @Insert("""
            INSERT INTO cloudmold_finance_ap_installment
              (ap_installment_id,tenant_id,ap_open_item_id,installment_number,due_date,amount_minor,
               settled_amount_minor,status,version,created_at,updated_at)
            VALUES (#{installmentId},#{tenantId},#{apId},1,#{dueDate},#{amount},0,'OPEN',1,#{now},#{now})
            """)
    int insertSingleInstallment(@Param("installmentId") String installmentId, @Param("tenantId") Long tenantId,
                                @Param("apId") String apId, @Param("dueDate") LocalDate dueDate,
                                @Param("amount") Long amount, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_ap_installment
              (ap_installment_id,tenant_id,ap_open_item_id,installment_number,due_date,amount_minor,
               settled_amount_minor,status,version,created_at,updated_at)
            VALUES (#{installmentId},#{tenantId},#{apId},#{number},#{dueDate},#{amount},0,'OPEN',1,#{now},#{now})
            """)
    int insertApInstallment(@Param("installmentId") String installmentId, @Param("tenantId") Long tenantId,
                            @Param("apId") String apId, @Param("number") Integer number,
                            @Param("dueDate") LocalDate dueDate, @Param("amount") Long amount,
                            @Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_finance_ap_status_history (tenant_id,ap_open_item_id,from_status,to_status,amount_minor,source_type,source_id,aggregate_version,occurred_at) VALUES (#{tenantId},#{apId},#{fromStatus},#{toStatus},#{amount},#{sourceType},#{sourceId},#{version},#{now})")
    int insertApHistory(@Param("tenantId") Long tenantId, @Param("apId") String apId,
                        @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                        @Param("amount") Long amount, @Param("sourceType") String sourceType,
                        @Param("sourceId") String sourceId, @Param("version") Long version,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_supplier_invoice SET lifecycle_status='POSTED',posted_journal_entry_id=#{journalId},
                   version=version+1,updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND supplier_invoice_id=#{invoiceId}
               AND lifecycle_status='APPROVED' AND match_status='MATCHED' AND version=#{expectedVersion}
            """)
    int markInvoicePosted(@Param("tenantId") Long tenantId, @Param("invoiceId") String invoiceId,
                          @Param("expectedVersion") Long expectedVersion, @Param("journalId") String journalId,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_payment_instruction
              (payment_instruction_id,tenant_id,payment_code,legal_entity_id,ledger_id,accounting_period_id,
               supplier_id,payee_instrument_id,currency_code,requested_execution_date,total_amount_minor,
               posting_rule_id,posting_rule_version,status,
               created_by_principal_id,version,created_at,updated_at)
            VALUES (#{paymentInstructionId},#{tenantId},#{paymentCode},#{legalEntityId},#{ledgerId},
                    #{accountingPeriodId},#{supplierId},#{payeeInstrumentId},#{currencyCode},
                    #{requestedExecutionDate},#{totalAmountMinor},#{postingRuleId},#{postingRuleVersion},
                    #{status},#{createdByPrincipalId},
                    #{version},#{createdAt},#{updatedAt})
            """) int insertPaymentInstruction(PaymentInstruction value);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_payment_allocation
              (payment_allocation_id,tenant_id,payment_instruction_id,ap_open_item_id,amount_minor,created_at)
            VALUES (#{paymentAllocationId},#{tenantId},#{paymentInstructionId},#{apOpenItemId},#{amountMinor},#{createdAt})
            """) int insertPaymentAllocation(PaymentAllocation value);

    @Select("""
            SELECT ap_open_item_id,tenant_id,supplier_invoice_id,legal_entity_id,ledger_id,supplier_id,currency_code,
                   original_amount_minor,settled_amount_minor,open_amount_minor,due_date,status,version,created_at,updated_at
              FROM cloudmold_finance_ap_open_item
             WHERE tenant_id=#{tenantId} AND ap_open_item_id=#{apId} FOR UPDATE
            """) ApOpenItem selectApForUpdate(@Param("tenantId") Long tenantId, @Param("apId") String apId);

    @Select("""
            SELECT COALESCE(SUM(a.amount_minor),0)
              FROM cloudmold_finance_supplier_payment_allocation a
              JOIN cloudmold_finance_supplier_payment_instruction p
                ON p.payment_instruction_id=a.payment_instruction_id
             WHERE a.tenant_id=#{tenantId} AND a.ap_open_item_id=#{apId}
               AND p.status IN ('DRAFT','SUBMITTED_FOR_APPROVAL','APPROVED','RELEASED','PROCESSING','EXECUTED')
            """) Long sumReservedPaymentAmount(@Param("tenantId") Long tenantId, @Param("apId") String apId);

    @Select("""
            SELECT payment_instruction_id,tenant_id,payment_code,legal_entity_id,ledger_id,accounting_period_id,
                   supplier_id,payee_instrument_id,currency_code,requested_execution_date,total_amount_minor,
                   posting_rule_id,posting_rule_version,status,
                   created_by_principal_id,approved_by_principal_id,released_by_principal_id,version,created_at,updated_at
              FROM cloudmold_finance_supplier_payment_instruction
             WHERE tenant_id=#{tenantId} AND payment_instruction_id=#{paymentId} FOR UPDATE
            """) PaymentInstruction selectPaymentForUpdate(@Param("tenantId") Long tenantId,
                                                             @Param("paymentId") String paymentId);

    @Update("""
            UPDATE cloudmold_finance_supplier_payment_instruction
               SET status=#{toStatus},
                   approved_by_principal_id=CASE WHEN #{toStatus}='APPROVED' THEN #{actor} ELSE approved_by_principal_id END,
                   released_by_principal_id=CASE WHEN #{toStatus}='RELEASED' THEN #{actor} ELSE released_by_principal_id END,
                   version=version+1,updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND payment_instruction_id=#{paymentId}
               AND status=#{fromStatus} AND version=#{expectedVersion}
            """)
    int transitionPayment(@Param("tenantId") Long tenantId, @Param("paymentId") String paymentId,
                          @Param("expectedVersion") Long expectedVersion, @Param("fromStatus") String fromStatus,
                          @Param("toStatus") String toStatus, @Param("actor") String actor,
                          @Param("now") LocalDateTime now);

    @Insert("INSERT INTO cloudmold_finance_supplier_payment_status_history (tenant_id,payment_instruction_id,from_status,to_status,actor_principal_id,reason_code,aggregate_version,occurred_at) VALUES (#{tenantId},#{paymentId},#{fromStatus},#{toStatus},#{actor},#{reason},#{version},#{now})")
    int insertPaymentHistory(@Param("tenantId") Long tenantId, @Param("paymentId") String paymentId,
                             @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                             @Param("actor") String actor, @Param("reason") String reason,
                             @Param("version") Long version, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_payment_execution
              (execution_id,tenant_id,payment_instruction_id,provider_code,provider_reference,
               execution_evidence_sha256,status,executed_at)
            VALUES (#{executionId},#{tenantId},#{paymentId},#{provider},#{providerRef},#{evidence},'EXECUTED',#{now})
            """)
    int insertPaymentExecution(@Param("executionId") String executionId, @Param("tenantId") Long tenantId,
                               @Param("paymentId") String paymentId, @Param("provider") String provider,
                               @Param("providerRef") String providerRef, @Param("evidence") String evidence,
                               @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_supplier_payment_settlement
              (settlement_id,tenant_id,payment_instruction_id,settlement_date,settled_amount_minor,currency_code,
               bank_reference,settlement_evidence_sha256,created_at)
            VALUES (#{settlementId},#{tenantId},#{paymentId},#{settlementDate},#{amount},#{currency},
                    #{bankRef},#{evidence},#{now})
            """)
    int insertPaymentSettlement(@Param("settlementId") String settlementId, @Param("tenantId") Long tenantId,
                                @Param("paymentId") String paymentId, @Param("settlementDate") LocalDate settlementDate,
                                @Param("amount") Long amount, @Param("currency") String currency,
                                @Param("bankRef") String bankRef, @Param("evidence") String evidence,
                                @Param("now") LocalDateTime now);

    @Select("""
            SELECT payment_allocation_id,tenant_id,payment_instruction_id,ap_open_item_id,amount_minor,created_at
              FROM cloudmold_finance_supplier_payment_allocation
             WHERE tenant_id=#{tenantId} AND payment_instruction_id=#{paymentId} ORDER BY payment_allocation_id
            """) List<PaymentAllocation> selectPaymentAllocations(@Param("tenantId") Long tenantId,
                                                                    @Param("paymentId") String paymentId);

    @Insert("""
            INSERT INTO cloudmold_finance_ap_application
              (ap_application_id,tenant_id,ap_open_item_id,source_type,source_id,application_type,
               amount_minor,journal_entry_id,applied_at)
            VALUES (#{applicationId},#{tenantId},#{apId},'PAYMENT_SETTLEMENT',#{settlementId},'SETTLE',
                    #{amount},#{journalId},#{now})
            """)
    int insertApSettlementApplication(@Param("applicationId") String applicationId,
                                      @Param("tenantId") Long tenantId, @Param("apId") String apId,
                                      @Param("settlementId") String settlementId, @Param("amount") Long amount,
                                      @Param("journalId") String journalId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_ap_open_item
               SET settled_amount_minor=settled_amount_minor+#{amount},open_amount_minor=open_amount_minor-#{amount},
                   status=CASE WHEN open_amount_minor-#{amount}=0 THEN 'SETTLED' ELSE 'PARTIALLY_SETTLED' END,
                   version=version+1,updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND ap_open_item_id=#{apId} AND version=#{expectedVersion}
               AND open_amount_minor>=#{amount}
            """)
    int applyApSettlement(@Param("tenantId") Long tenantId, @Param("apId") String apId,
                          @Param("expectedVersion") Long expectedVersion, @Param("amount") Long amount,
                          @Param("now") LocalDateTime now);

    @Select("""
            SELECT ap_installment_id,tenant_id,ap_open_item_id,installment_number,due_date,amount_minor,
                   settled_amount_minor,status,version,created_at,updated_at
              FROM cloudmold_finance_ap_installment
             WHERE tenant_id=#{tenantId} AND ap_open_item_id=#{apId} AND status!='SETTLED'
             ORDER BY due_date,installment_number FOR UPDATE
            """)
    List<ApInstallment> selectOpenInstallmentsForUpdate(@Param("tenantId") Long tenantId,
                                                        @Param("apId") String apId);

    @Update("""
            UPDATE cloudmold_finance_ap_installment
               SET settled_amount_minor=settled_amount_minor+#{amount},
                   status=CASE WHEN settled_amount_minor+#{amount}=amount_minor
                               THEN 'SETTLED' ELSE 'PARTIALLY_SETTLED' END,
                   version=version+1,updated_at=#{now}
             WHERE tenant_id=#{tenantId} AND ap_installment_id=#{installmentId} AND version=#{expectedVersion}
               AND amount_minor-settled_amount_minor>=#{amount}
            """)
    int applyInstallmentSettlement(@Param("tenantId") Long tenantId,
                                   @Param("installmentId") String installmentId,
                                   @Param("expectedVersion") Long expectedVersion,
                                   @Param("amount") Long amount, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE cloudmold_finance_supplier_invoice i
              JOIN cloudmold_finance_ap_open_item a ON a.supplier_invoice_id=i.supplier_invoice_id
               SET i.settlement_status=CASE WHEN a.open_amount_minor=0 THEN 'PAID'
                                            WHEN a.settled_amount_minor>0 THEN 'PARTIALLY_PAID'
                                            ELSE 'UNPAID' END,
                   i.version=i.version+1,i.updated_at=#{now}
             WHERE i.tenant_id=#{tenantId} AND a.ap_open_item_id=#{apId}
            """)
    int updateInvoiceSettlementFromAp(@Param("tenantId") Long tenantId, @Param("apId") String apId,
                                      @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_entry
              (journal_entry_id,tenant_id,journal_code,legal_entity_id,ledger_id,period_id,accounting_date,
               source_type,source_id,currency_code,document_currency_code,debit_total_minor,credit_total_minor,
               evidence_sha256,status,prepared_by_principal_id,posted_by_principal_id,reason_code,version,
               prepared_at,posted_at,created_at,updated_at)
            VALUES (#{journalEntryId},#{tenantId},#{journalCode},#{legalEntityId},#{ledgerId},#{periodId},
                    #{accountingDate},#{sourceType},#{sourceId},#{currencyCode},#{documentCurrencyCode},
                    #{debitTotalMinor},#{creditTotalMinor},#{evidenceSha256},#{status},#{preparedByPrincipalId},
                    #{postedByPrincipalId},#{reasonCode},#{version},#{preparedAt},#{postedAt},#{createdAt},#{updatedAt})
            """) int insertJournalEntry(JournalEntry value);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_line
              (journal_line_id,tenant_id,journal_entry_id,ledger_id,line_number,account_id,account_code,debit_amount_minor,
               credit_amount_minor,transaction_currency_code,transaction_amount_minor,supplier_id,
               supplier_invoice_id,ap_open_item_id,purchase_order_id,purchase_order_item_id,receipt_line_id,
               inventory_movement_id,created_at)
            VALUES (#{journalLineId},#{tenantId},#{journalEntryId},#{ledgerId},#{lineNumber},#{accountId},#{accountCode},#{debitAmountMinor},
                    #{creditAmountMinor},#{transactionCurrencyCode},#{transactionAmountMinor},#{supplierId},
                    #{supplierInvoiceId},#{apOpenItemId},#{purchaseOrderId},#{purchaseOrderItemId},#{receiptLineId},
                    #{inventoryMovementId},#{createdAt})
            """) int insertJournalLine(ProcureToPayRecords.JournalLine value);

    @Insert("INSERT INTO cloudmold_finance_journal_line_dimension (journal_line_dimension_id,tenant_id,journal_line_id,dimension_type_id,dimension_value_id,created_at) VALUES (#{id},#{tenantId},#{lineId},#{typeId},#{valueId},#{now})")
    int insertJournalLineDimension(@Param("id") String id, @Param("tenantId") Long tenantId,
                                   @Param("lineId") String lineId, @Param("typeId") String typeId,
                                   @Param("valueId") String valueId, @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_source_effect
              (source_effect_id,tenant_id,source_type,source_id,effect_type,journal_entry_id,created_at)
            VALUES (#{effectId},#{tenantId},#{sourceType},#{sourceId},#{effectType},#{journalId},#{now})
            """)
    int insertJournalSourceEffect(@Param("effectId") String effectId, @Param("tenantId") Long tenantId,
                                  @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                                  @Param("effectType") String effectType, @Param("journalId") String journalId,
                                  @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO cloudmold_finance_journal_status_history
              (tenant_id,journal_entry_id,from_status,to_status,actor_principal_id,reason_code,
               aggregate_version,occurred_at)
            VALUES (#{tenantId},#{journalId},#{fromStatus},#{toStatus},#{actor},#{reason},#{version},#{now})
            """)
    int insertJournalHistory(@Param("tenantId") Long tenantId, @Param("journalId") String journalId,
                             @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                             @Param("actor") String actor, @Param("reason") String reason,
                             @Param("version") Long version, @Param("now") LocalDateTime now);

    @Select("""
            SELECT journal_entry_id,tenant_id,journal_code,legal_entity_id,ledger_id,period_id,accounting_date,
                   source_type,source_id,currency_code,document_currency_code,debit_total_minor,credit_total_minor,
                   evidence_sha256,status,prepared_by_principal_id,posted_by_principal_id,reason_code,version,
                   prepared_at,posted_at,created_at,updated_at
              FROM cloudmold_finance_journal_entry
             WHERE tenant_id=#{tenantId} AND journal_entry_id=#{journalId} FOR UPDATE
            """) JournalEntry selectJournalForUpdate(@Param("tenantId") Long tenantId,
                                                       @Param("journalId") String journalId);

    @Select("""
            SELECT journal_line_id,tenant_id,journal_entry_id,ledger_id,line_number,account_id,account_code,debit_amount_minor,
                   credit_amount_minor,transaction_currency_code,transaction_amount_minor,supplier_id,
                   supplier_invoice_id,ap_open_item_id,purchase_order_id,purchase_order_item_id,receipt_line_id,
                   inventory_movement_id,created_at
              FROM cloudmold_finance_journal_line
             WHERE tenant_id=#{tenantId} AND journal_entry_id=#{journalId} ORDER BY line_number
            """) List<ProcureToPayRecords.JournalLine> selectJournalLines(@Param("tenantId") Long tenantId,
                                                                           @Param("journalId") String journalId);

    @Select("SELECT COUNT(*) FROM cloudmold_finance_journal_reversal_link WHERE tenant_id=#{tenantId} AND original_journal_entry_id=#{journalId}")
    int countJournalReversal(@Param("tenantId") Long tenantId, @Param("journalId") String journalId);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_valuation_layer
              (valuation_layer_id,tenant_id,ledger_id,inventory_movement_id,inventory_movement_version,receipt_line_id,
               quality_disposition_id,purchase_order_item_id,valuation_policy_id,valuation_policy_version,
               quantity,unit_of_measure,unit_cost_amount_minor,total_cost_amount_minor,currency_code,
               remaining_quantity,remaining_cost_amount_minor,status,version,created_at,updated_at)
            VALUES (#{valuationLayerId},#{tenantId},#{ledgerId},#{inventoryMovementId},#{inventoryMovementVersion},#{receiptLineId},
                    #{qualityDispositionId},#{purchaseOrderItemId},#{valuationPolicyId},#{valuationPolicyVersion},
                    #{quantity},#{unitOfMeasure},#{unitCostAmountMinor},#{totalCostAmountMinor},#{currencyCode},
                    #{remainingQuantity},#{remainingCostAmountMinor},#{status},#{version},#{createdAt},#{updatedAt})
            """) int insertValuationLayer(InventoryValuationLayer value);

    @Insert("""
            INSERT INTO cloudmold_finance_inventory_valuation_effect
              (valuation_effect_id,tenant_id,valuation_layer_id,effect_type,inventory_movement_id,inventory_movement_version,
               quantity,amount_minor,currency_code,journal_entry_id,occurred_at)
            VALUES (#{effectId},#{tenantId},#{layerId},'QUALIFIED_RECEIPT',#{movementId},#{movementVersion},#{quantity},
                    #{amount},#{currency},#{journalId},#{now})
            """)
    int insertQualifiedReceiptValuationEffect(@Param("effectId") String effectId,
                                               @Param("tenantId") Long tenantId,
                                               @Param("layerId") String layerId,
                                               @Param("movementId") String movementId,
                                               @Param("movementVersion") Long movementVersion,
                                               @Param("quantity") java.math.BigDecimal quantity,
                                               @Param("amount") Long amount,
                                               @Param("currency") String currency,
                                               @Param("journalId") String journalId,
                                               @Param("now") LocalDateTime now);

    @Select("""
            SELECT COALESCE(SUM(ROUND(v.amount_minor * a.allocated_quantity / l.quantity,0)),0)
              FROM cloudmold_finance_invoice_match_receipt_allocation a
              JOIN cloudmold_finance_invoice_match_run r
                ON r.match_run_id=a.match_run_id AND r.tenant_id=a.tenant_id
              JOIN cloudmold_finance_inventory_valuation_effect v
                ON v.tenant_id=a.tenant_id AND v.inventory_movement_id=a.inventory_movement_id
               AND v.inventory_movement_version=a.inventory_movement_version
               AND v.effect_type='QUALIFIED_RECEIPT'
              JOIN cloudmold_finance_inventory_valuation_layer l
                ON l.tenant_id=v.tenant_id AND l.valuation_layer_id=v.valuation_layer_id
             WHERE a.tenant_id=#{tenantId} AND r.supplier_invoice_id=#{invoiceId}
               AND r.status='MATCHED' AND a.status='ACTIVE'
            """)
    Long sumMatchedReceiptValuation(@Param("tenantId") Long tenantId,
                                    @Param("invoiceId") String invoiceId);

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

    @Select("""
            SELECT period_id,tenant_id,period_code,period_start,period_end,currency_code,status,
                   opened_by_principal_id,closed_by_principal_id,close_evidence_sha256,reason_code,
                   version,opened_at,closed_at,created_at,updated_at
              FROM cloudmold_finance_accounting_period
             WHERE tenant_id=#{tenantId} AND period_id=#{periodId} FOR UPDATE
            """) AccountingPeriod selectPeriodForUpdate(@Param("tenantId") Long tenantId,
                                                           @Param("periodId") String periodId);
}
