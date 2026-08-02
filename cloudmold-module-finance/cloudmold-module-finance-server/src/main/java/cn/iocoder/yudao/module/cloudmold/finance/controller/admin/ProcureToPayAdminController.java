package cn.iocoder.yudao.module.cloudmold.finance.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.*;
import cn.iocoder.yudao.module.cloudmold.finance.controller.admin.vo.ProcureToPayAdminVOs.*;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.finance.service.query.ProcureToPayQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name="CloudMold - Procure to Pay Finance")
@RestController
@RequestMapping("/cloudmold/finance/procure-to-pay")
public class ProcureToPayAdminController {
    @Resource private SupplierInvoiceCommandApi invoiceApi;
    @Resource private SupplierPaymentCommandApi paymentApi;
    @Resource private JournalCommandApi journalApi;
    @Resource private P2pEvidenceIngestionApi evidenceApi;
    @Resource private P2pFinanceSetupCommandApi setupApi;
    @Resource private ProcureToPayQueryService queryService;
    @Resource private FinanceActorPrincipalPort actorPort;

    @GetMapping("/supplier-invoices/page") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<PageResult<SupplierInvoicePageItem>> invoices(PageRequest request){return success(queryService.invoices(request));}
    @GetMapping("/supplier-invoices/{id}") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<SupplierInvoiceDetail> invoice(@PathVariable("id")String id){return success(queryService.invoice(id));}
    @PostMapping("/supplier-invoices/command") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')")
    public CommonResult<AdminCommandResult> invoiceCommand(@RequestBody SupplierInvoiceCommandRequest request){String a=actor();String operation=required(request.getOperation());SupplierInvoiceCommands.Transition transition=SupplierInvoiceCommands.Transition.builder().envelope(request.getEnvelope()).supplierInvoiceId(request.getSupplierInvoiceId()).expectedVersion(request.getExpectedVersion()).reasonCode(request.getReasonCode()).build();ProcureToPayResult r=switch(operation){case"SUBMIT_SUPPLIER_INVOICE"->invoiceApi.submit(transition,a);case"RUN_THREE_WAY_MATCH"->invoiceApi.runThreeWayMatch(SupplierInvoiceCommands.Match.builder().envelope(request.getEnvelope()).supplierInvoiceId(request.getSupplierInvoiceId()).expectedVersion(request.getExpectedVersion()).matchPolicyId(request.getMatchPolicyId()).matchPolicyVersion(request.getMatchPolicyVersion()).build(),a);case"APPROVE_SUPPLIER_INVOICE"->invoiceApi.approve(transition,a);case"POST_SUPPLIER_INVOICE"->invoiceApi.post(transition,a);default->throw new IllegalArgumentException("unsupported supplier invoice operation");};return success(admin(r));}

    @GetMapping("/match-exceptions/page") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<PageResult<MatchExceptionPageItem>> exceptions(PageRequest request){return success(queryService.matchExceptions(request));}
    @PostMapping("/match-exceptions/command") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')")
    public CommonResult<AdminCommandResult> exceptionCommand(@RequestBody MatchExceptionCommandRequest request){if(!"APPROVE_MATCH_OVERRIDE".equals(required(request.getOperation())))throw new IllegalArgumentException("unsupported match exception operation");return success(admin(invoiceApi.approveMatchOverride(SupplierInvoiceCommands.ApproveOverride.builder().envelope(request.getEnvelope()).exceptionId(request.getExceptionId()).expectedVersion(request.getExpectedVersion()).reasonCode(request.getReasonCode()).resolutionEvidenceSha256(request.getResolutionEvidenceSha256()).build(),actor())));}

    @GetMapping("/ap-installments/page") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<PageResult<ApInstallmentPageItem>> installments(PageRequest request){return success(queryService.apInstallments(request));}

    @GetMapping("/supplier-payments/page") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<PageResult<SupplierPaymentPageItem>> payments(PageRequest request){return success(queryService.payments(request));}
    @PostMapping("/supplier-payments/command") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')")
    public CommonResult<AdminCommandResult> paymentCommand(@RequestBody SupplierPaymentCommandRequest request){String a=actor();String operation=required(request.getOperation());SupplierPaymentCommands.Transition transition=SupplierPaymentCommands.Transition.builder().envelope(request.getEnvelope()).paymentInstructionId(request.getPaymentInstructionId()).expectedVersion(request.getExpectedVersion()).reasonCode(request.getReasonCode()).build();ProcureToPayResult r=switch(operation){case"APPROVE_SUPPLIER_PAYMENT"->paymentApi.approve(transition,a);case"RELEASE_SUPPLIER_PAYMENT"->paymentApi.release(transition,a);case"SETTLE_SUPPLIER_PAYMENT"->paymentApi.settle(SupplierPaymentCommands.Settlement.builder().envelope(request.getEnvelope()).paymentInstructionId(request.getPaymentInstructionId()).expectedVersion(request.getExpectedVersion()).settlementId(request.getSettlementId()).settlementDate(request.getSettlementDate()).settledAmountMinor(int64(request.getSettledAmountMinor(),"settledAmountMinor")).currencyCode(request.getCurrencyCode()).bankReference(request.getBankReference()).settlementEvidenceSha256(request.getSettlementEvidenceSha256()).build(),a);default->throw new IllegalArgumentException("unsupported supplier payment operation");};return success(admin(r));}

    @GetMapping("/journals/page") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<PageResult<JournalPageItem>> journals(PageRequest request){return success(queryService.journals(request));}
    @GetMapping("/journals/{id}") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:query')")
    public CommonResult<JournalDetail> journal(@PathVariable("id")String id){return success(queryService.journal(id));}
    @PostMapping("/journals/command") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')")
    public CommonResult<AdminCommandResult> journalCommand(@RequestBody JournalCommandRequest request){if(!"REVERSE_JOURNAL".equals(required(request.getOperation())))throw new IllegalArgumentException("unsupported journal operation");return success(admin(journalApi.reverse(JournalCommands.Reverse.builder().envelope(request.getEnvelope()).originalJournalEntryId(request.getOriginalJournalEntryId()).expectedVersion(request.getExpectedVersion()).reversalJournalEntryId(request.getReversalJournalEntryId()).reversalJournalCode(request.getReversalJournalCode()).accountingPeriodId(request.getAccountingPeriodId()).accountingDate(request.getAccountingDate()).reversalEvidenceSha256(request.getReversalEvidenceSha256()).reasonCode(request.getReasonCode()).build(),actor())));}

    @PostMapping("/qualified-receipts/command") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:command')")
    public CommonResult<AdminCommandResult> qualifiedReceipt(@RequestBody P2pEvidenceCommands.PostQualifiedReceipt command){return success(admin(evidenceApi.postQualifiedReceipt(command,actor())));}

    @GetMapping("/match-policies/page") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')")
    public CommonResult<PageResult<MatchPolicyItem>> policies(PageRequest request){return success(queryService.matchPolicies(request));}
    @PostMapping("/match-policies") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')")
    public CommonResult<AdminCommandResult> createPolicy(@RequestBody P2pFinanceSetupCommands.MatchPolicy command){return success(admin(setupApi.createMatchPolicy(command,actor())));}
    @PostMapping("/setup/ledgers") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')") public CommonResult<AdminCommandResult> ledger(@RequestBody P2pFinanceSetupCommands.Ledger c){return success(admin(setupApi.createLedger(c,actor())));}
    @PostMapping("/setup/accounts") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')") public CommonResult<AdminCommandResult> account(@RequestBody P2pFinanceSetupCommands.Account c){return success(admin(setupApi.createAccount(c,actor())));}
    @PostMapping("/setup/payee-instruments") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')") public CommonResult<AdminCommandResult> payee(@RequestBody P2pFinanceSetupCommands.SupplierPayeeInstrument c){return success(admin(setupApi.registerSupplierPayeeInstrument(c,actor())));}
    @PostMapping("/setup/payment-terms") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')") public CommonResult<AdminCommandResult> term(@RequestBody P2pFinanceSetupCommands.PaymentTerm c){return success(admin(setupApi.createPaymentTerm(c,actor())));}
    @PostMapping("/setup/posting-rules") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')") public CommonResult<AdminCommandResult> posting(@RequestBody P2pFinanceSetupCommands.PostingRule c){return success(admin(setupApi.createPostingRule(c,actor())));}
    @PostMapping("/setup/valuation-policies") @PreAuthorize("@ss.hasPermission('cloudmold:finance:procure-to-pay:govern')") public CommonResult<AdminCommandResult> valuation(@RequestBody P2pFinanceSetupCommands.InventoryValuationPolicy c){return success(admin(setupApi.createInventoryValuationPolicy(c,actor())));}

    private String actor(){return actorPort.resolveSystemAdmin(getLoginUserId());}
    private static String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("operation is required");return v.trim().toUpperCase(java.util.Locale.ROOT);}
    private static AdminCommandResult admin(ProcureToPayResult r){AdminCommandResult v=new AdminCommandResult();v.setOperationId(String.valueOf(r.getOperationId()));v.setDuplicate(r.getDuplicate());v.setAggregateType(r.getAggregateType());v.setAggregateId(r.getAggregateId());v.setAggregateVersion(r.getAggregateVersion());v.setStatus(r.getStatus());v.setSupplierInvoiceId(r.getSupplierInvoiceId());v.setMatchRunId(r.getMatchRunId());v.setExceptionId(r.getExceptionId());v.setApOpenItemId(r.getApOpenItemId());v.setPaymentInstructionId(r.getPaymentInstructionId());v.setJournalEntryId(r.getJournalEntryId());return v;}
    private static Long int64(String value,String field){try{return Long.valueOf(value);}catch(RuntimeException ex){throw new IllegalArgumentException(field+" must be a signed 64-bit integer string",ex);}}
}
