package cn.iocoder.yudao.module.cloudmold.finance.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.AccountingPeriod;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.FinanceCloseRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.ProcureToPayRecords.*;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.ProcureToPayMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProcureToPayServiceImpl implements P2pEvidenceIngestionApi, SupplierInvoiceCommandApi,
        SupplierPaymentCommandApi, JournalCommandApi, P2pFinanceSetupCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-finance";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final ProcureToPayMapper mapper;
    private final OutboxAppender outboxAppender;
    private final FinanceActorPrincipalPort actorPrincipalPort;

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createLedger(P2pFinanceSetupCommands.Ledger command, String actor) {
        return execute(command.getEnvelope(), "CREATE_FINANCE_LEDGER", actor, command, c -> {
            requireRef(command.getLegalEntityId(), "legalEntityId"); requireRef(command.getLedgerCode(), "ledgerCode");
            requireCurrency(command.getFunctionalCurrencyCode()); String id = valueOrUuid(command.getLedgerId());
            require(mapper.insertLedger(id, c.tenantId(), command.getLegalEntityId(), upper(command.getLedgerCode()),
                    upper(command.getFunctionalCurrencyCode()), c.now()) == 1, "failed to create ledger");
            return outcome("finance.ledger.created", "finance_ledger", id, 1L, "ACTIVE", payload("ledger_id", id));
        });
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createAccount(P2pFinanceSetupCommands.Account command, String actor) {
        return execute(command.getEnvelope(), "CREATE_FINANCE_ACCOUNT", actor, command, c -> {
            requireRef(command.getLedgerId(), "ledgerId"); requireRef(command.getAccountCode(), "accountCode");
            require(command.getAccountName() != null && !command.getAccountName().isBlank(), "accountName is required");
            require(Set.of("ASSET","LIABILITY","EQUITY","REVENUE","EXPENSE").contains(upper(command.getAccountType())),
                    "accountType is invalid"); require(Set.of("DEBIT","CREDIT").contains(upper(command.getNormalBalance())),
                    "normalBalance is invalid"); String id = valueOrUuid(command.getAccountId());
            require(mapper.insertAccount(id, c.tenantId(), command.getLedgerId(), upper(command.getAccountCode()),
                    command.getAccountName(), upper(command.getAccountType()), upper(command.getNormalBalance()), c.now()) == 1,
                    "failed to create account");
            return outcome("finance.account.created", "finance_account", id, 1L, "ACTIVE", payload("account_id", id));
        });
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult registerSupplierPayeeInstrument(P2pFinanceSetupCommands.SupplierPayeeInstrument command,
                                                              String actor) {
        return execute(command.getEnvelope(), "REGISTER_SUPPLIER_PAYEE", actor, command, c -> {
            requireRef(command.getLegalEntityId(), "legalEntityId"); requireRef(command.getSupplierId(), "supplierId");
            require(command.getInstrumentToken() != null && command.getInstrumentToken().length() >= 16,
                    "instrumentToken is invalid"); require(command.getMaskedAccount() != null
                    && command.getMaskedAccount().matches("[*Xx0-9 -]{4,64}"), "maskedAccount is invalid");
            require(command.getBankCountryCode() != null && command.getBankCountryCode().matches("[A-Za-z]{2}"),
                    "bankCountryCode is invalid"); requireRef(command.getBankCode(), "bankCode");
            requireCurrency(command.getCurrencyCode()); requireSha256(command.getVerificationEvidenceSha256(),
                    "verificationEvidenceSha256"); require(command.getValidFrom() != null, "validFrom is required");
            require(command.getValidUntil() == null || !command.getValidUntil().isBefore(command.getValidFrom()),
                    "payee validity is invalid"); String id = valueOrUuid(command.getPayeeInstrumentId());
            require(mapper.insertPayeeInstrument(id, c.tenantId(), command.getLegalEntityId(), command.getSupplierId(),
                    command.getInstrumentToken(), command.getMaskedAccount(), upper(command.getBankCountryCode()),
                    command.getBankCode(), upper(command.getCurrencyCode()), command.getVerificationEvidenceSha256(),
                    command.getValidFrom(), command.getValidUntil(), c.now()) == 1, "failed to register payee instrument");
            return outcome("finance.supplier_payee_instrument.registered", "finance_supplier_payee_instrument",
                    id, 1L, "ACTIVE", payload("payee_instrument_id", id));
        });
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createMatchPolicy(P2pFinanceSetupCommands.MatchPolicy command, String actor) {
        return execute(command.getEnvelope(), "CREATE_MATCH_POLICY", actor, command, c -> {
            requireRef(command.getPolicyCode(), "policyCode"); requireRef(command.getLegalEntityId(), "legalEntityId");
            require(command.getPolicyVersion() != null && command.getPolicyVersion() > 0, "policyVersion must be positive");
            requireNonNegative(command.getPriceToleranceAmountMinor(), "priceToleranceAmountMinor");
            requireNonNegative(command.getTaxToleranceAmountMinor(), "taxToleranceAmountMinor");
            requireNonNegative(command.getQuantityTolerance(), "quantityTolerance"); String id=valueOrUuid(command.getMatchPolicyId());
            MatchPolicy row=new MatchPolicy().setMatchPolicyId(id).setTenantId(c.tenantId()).setPolicyCode(upper(command.getPolicyCode()))
                    .setLegalEntityId(command.getLegalEntityId()).setPolicyVersion(command.getPolicyVersion())
                    .setPriceToleranceAmountMinor(command.getPriceToleranceAmountMinor())
                    .setTaxToleranceAmountMinor(command.getTaxToleranceAmountMinor()).setQuantityTolerance(command.getQuantityTolerance())
                    .setStatus("ACTIVE").setCreatedAt(c.now()); require(mapper.insertMatchPolicy(row)==1,"failed to create match policy");
            return outcome("finance.match_policy.created","finance_match_policy",id,command.getPolicyVersion(),"ACTIVE",payload("match_policy_id",id));
        });
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createPaymentTerm(P2pFinanceSetupCommands.PaymentTerm command, String actor) {
        return execute(command.getEnvelope(), "CREATE_PAYMENT_TERM", actor, command, c -> {
            requireRef(command.getTermCode(),"termCode"); requireRef(command.getLegalEntityId(),"legalEntityId");
            require(command.getTermVersion()!=null&&command.getTermVersion()>0,"termVersion must be positive");
            require(command.getInstallments()!=null&&!command.getInstallments().isEmpty(),"installment rules are required");
            int basis=0; Set<Integer> numbers=new HashSet<>(); for (P2pFinanceSetupCommands.InstallmentRule rule:command.getInstallments()) {
                require(rule.getInstallmentNumber()!=null&&rule.getInstallmentNumber()>0&&numbers.add(rule.getInstallmentNumber()),"installmentNumber is invalid");
                require(rule.getDueDaysAfterIssue()!=null&&rule.getDueDaysAfterIssue()>=0,"dueDaysAfterIssue is invalid");
                require(rule.getAllocationBasisPoints()!=null&&rule.getAllocationBasisPoints()>0,"allocationBasisPoints is invalid"); basis+=rule.getAllocationBasisPoints(); }
            require(basis==10000,"payment term allocation must total 10000 basis points"); String id=valueOrUuid(command.getPaymentTermId());
            require(mapper.insertPaymentTerm(id,c.tenantId(),upper(command.getTermCode()),command.getLegalEntityId(),command.getTermVersion(),c.now())==1,"failed to create payment term");
            for (P2pFinanceSetupCommands.InstallmentRule input:command.getInstallments()) require(mapper.insertPaymentTermRule(new PaymentTermRule()
                    .setInstallmentRuleId(UUID.randomUUID().toString()).setTenantId(c.tenantId()).setPaymentTermId(id)
                    .setTermVersion(command.getTermVersion()).setInstallmentNumber(input.getInstallmentNumber())
                    .setDueDaysAfterIssue(input.getDueDaysAfterIssue()).setAllocationBasisPoints(input.getAllocationBasisPoints()))==1,"failed to create installment rule");
            return outcome("finance.payment_term.created","finance_payment_term",id,command.getTermVersion(),"ACTIVE",payload("payment_term_id",id));
        });
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createPostingRule(P2pFinanceSetupCommands.PostingRule command, String actor) {
        return execute(command.getEnvelope(),"CREATE_POSTING_RULE",actor,command,c->{
            requireRef(command.getRuleCode(),"ruleCode"); requireRef(command.getLedgerId(),"ledgerId");
            String sourceType = upper(command.getSourceType());
            Map<String, Set<String>> requiredRoles = Map.of(
                    "QUALIFIED_RECEIPT", Set.of("INVENTORY", "GRIR"),
                    "SUPPLIER_INVOICE", Set.of("GRIR", "INPUT_TAX", "PURCHASE_PRICE_VARIANCE", "AP"),
                    "SUPPLIER_PAYMENT", Set.of("AP", "BANK_CLEARING"));
            require(requiredRoles.containsKey(sourceType),"sourceType is invalid");
            require(command.getRuleVersion()!=null&&command.getRuleVersion()>0,"ruleVersion must be positive");
            require(command.getLines()!=null&&!command.getLines().isEmpty(),"posting rule lines are required"); String id=valueOrUuid(command.getPostingRuleId());
            Set<String> suppliedRoles = command.getLines().stream().map(line -> upper(line.getAccountRole()))
                    .collect(java.util.stream.Collectors.toSet());
            require(suppliedRoles.equals(requiredRoles.get(sourceType)),
                    "posting rule roles must exactly equal " + requiredRoles.get(sourceType));
            require(mapper.insertPostingRule(id,c.tenantId(),upper(command.getRuleCode()),command.getLedgerId(),sourceType,command.getRuleVersion(),c.now())==1,"failed to create posting rule");
            Set<String> roles=new HashSet<>(); for(P2pFinanceSetupCommands.PostingRuleLine line:command.getLines()) { requireRef(line.getAccountId(),"accountId");
                String role=upper(line.getAccountRole()); require(roles.add(role),"posting account role must be unique"); require(mapper.insertPostingRuleLine(valueOrUuid(line.getPostingRuleLineId()),c.tenantId(),id,command.getRuleVersion(),command.getLedgerId(),role,line.getAccountId())==1,"failed to create posting rule line"); }
            return outcome("finance.posting_rule.created","finance_posting_rule",id,command.getRuleVersion(),"ACTIVE",payload("posting_rule_id",id));
        });
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createDimensionType(P2pFinanceSetupCommands.DimensionType command,String actor){return execute(command.getEnvelope(),"CREATE_DIMENSION_TYPE",actor,command,c->{String id=valueOrUuid(command.getDimensionTypeId());requireRef(command.getDimensionCode(),"dimensionCode");require(command.getDimensionName()!=null&&!command.getDimensionName().isBlank(),"dimensionName is required");require(mapper.insertDimensionType(id,c.tenantId(),upper(command.getDimensionCode()),command.getDimensionName(),c.now())==1,"failed to create dimension type");return outcome("finance.dimension_type.created","finance_dimension_type",id,1L,"ACTIVE",payload("dimension_type_id",id));});}

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createDimensionValue(P2pFinanceSetupCommands.DimensionValue command,String actor){return execute(command.getEnvelope(),"CREATE_DIMENSION_VALUE",actor,command,c->{String id=valueOrUuid(command.getDimensionValueId());requireRef(command.getDimensionTypeId(),"dimensionTypeId");requireRef(command.getValueCode(),"valueCode");require(command.getValueName()!=null&&!command.getValueName().isBlank(),"valueName is required");require(mapper.insertDimensionValue(id,c.tenantId(),command.getDimensionTypeId(),command.getValueCode(),command.getValueName(),c.now())==1,"failed to create dimension value");return outcome("finance.dimension_value.created","finance_dimension_value",id,1L,"ACTIVE",payload("dimension_value_id",id));});}

    @Override @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult createInventoryValuationPolicy(P2pFinanceSetupCommands.InventoryValuationPolicy command,String actor){return execute(command.getEnvelope(),"CREATE_VALUATION_POLICY",actor,command,c->{String id=valueOrUuid(command.getValuationPolicyId());requireRef(command.getPolicyCode(),"policyCode");requireRef(command.getLedgerId(),"ledgerId");requireRef(command.getPolicyVersion(),"policyVersion");require("FROZEN_RECEIPT_COST".equals(upper(command.getCostMethod())),"costMethod must be FROZEN_RECEIPT_COST");require(mapper.insertValuationPolicy(id,c.tenantId(),upper(command.getPolicyCode()),command.getLedgerId(),command.getPolicyVersion(),upper(command.getCostMethod()),c.now())==1,"failed to create valuation policy");return outcome("finance.inventory_valuation_policy.created","finance_inventory_valuation_policy",id,1L,"ACTIVE",payload("valuation_policy_id",id,"policy_version",command.getPolicyVersion()));});}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult ingestPurchaseOrderLine(P2pEvidenceCommands.PurchaseOrderLine command,
                                                      String actorPrincipalId) {
        return execute(command.getEnvelope(), "INGEST_PURCHASE_ORDER_LINE", actorPrincipalId, command, context -> {
            validateSourceEvidence(command.getSourceEventId(), command.getSourceVersion(),
                    command.getEvidenceSha256(), command.getSourceOccurredAt());
            requireRef(command.getPurchaseOrderId(), "purchaseOrderId");
            requireRef(command.getPurchaseOrderItemId(), "purchaseOrderItemId");
            requireRef(command.getLegalEntityId(), "legalEntityId");
            requireRef(command.getSupplierId(), "supplierId");
            requireCurrency(command.getCurrencyCode());
            requirePositive(command.getOrderedQuantity(), "orderedQuantity");
            requirePositive(command.getUnitNetPrice(), "unitNetPrice");
            requireUom(command.getUnitOfMeasure());
            requireAmounts(command.getNetAmountMinor(), command.getTaxAmountMinor(), command.getGrossAmountMinor());
            InboxResolution resolution = resolveInbox(context, command.getSourceEventId(),
                    "procurement.purchase_order.line_published", command.getPurchaseOrderItemId(),
                    command.getSourceVersion(), command.getEvidenceSha256(), command.getSourceOccurredAt());
            EventInbox inbox = resolution.inbox();
            if (resolution.duplicate()) {
                String existingId = nonNull(mapper.selectPoEvidenceIdByInbox(context.tenantId(), inbox.getInboxId()),
                        "purchase order evidence is missing for duplicate source event");
                return duplicateOutcome("finance_po_line_evidence", existingId, command.getSourceVersion(),
                        payload("purchase_order_id", command.getPurchaseOrderId(),
                                "purchase_order_item_id", command.getPurchaseOrderItemId()));
            }
            requireMonotonic(command.getSourceVersion(), mapper.selectMaxPoEvidenceVersion(
                    context.tenantId(), command.getPurchaseOrderItemId()), "purchase order line");
            String evidenceId = UUID.randomUUID().toString();
            PurchaseOrderLineEvidence row = new PurchaseOrderLineEvidence()
                    .setPoLineEvidenceId(evidenceId).setTenantId(context.tenantId()).setInboxId(inbox.getInboxId())
                    .setPurchaseOrderId(command.getPurchaseOrderId())
                    .setPurchaseOrderItemId(command.getPurchaseOrderItemId())
                    .setDeliveryScheduleId(command.getDeliveryScheduleId())
                    .setLegalEntityId(command.getLegalEntityId()).setSupplierId(command.getSupplierId())
                    .setCurrencyCode(upper(command.getCurrencyCode()))
                    .setOrderedQuantity(command.getOrderedQuantity()).setUnitOfMeasure(upper(command.getUnitOfMeasure()))
                    .setUnitNetPrice(command.getUnitNetPrice()).setNetAmountMinor(command.getNetAmountMinor())
                    .setTaxAmountMinor(command.getTaxAmountMinor()).setGrossAmountMinor(command.getGrossAmountMinor())
                    .setSourceVersion(command.getSourceVersion()).setCreatedAt(context.now());
            require(mapper.insertPurchaseOrderLineEvidence(row) == 1, "failed to persist purchase order evidence");
            return outcome("finance.p2p_evidence.purchase_order_line_recorded", "finance_po_line_evidence",
                    evidenceId, command.getSourceVersion(), "RECORDED",
                    payload("purchase_order_id", command.getPurchaseOrderId(),
                            "purchase_order_item_id", command.getPurchaseOrderItemId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult ingestReceiptLine(P2pEvidenceCommands.ReceiptLine command,
                                                String actorPrincipalId) {
        return execute(command.getEnvelope(), "INGEST_RECEIPT_LINE", actorPrincipalId, command, context -> {
            validateSourceEvidence(command.getSourceEventId(), command.getSourceVersion(),
                    command.getEvidenceSha256(), command.getSourceOccurredAt());
            requireRef(command.getReceiptId(), "receiptId"); requireRef(command.getReceiptLineId(), "receiptLineId");
            requireRef(command.getPurchaseOrderId(), "purchaseOrderId");
            requireRef(command.getPurchaseOrderItemId(), "purchaseOrderItemId");
            require(command.getPurchaseOrderLineVersion() != null && command.getPurchaseOrderLineVersion() > 0,
                    "purchaseOrderLineVersion must be positive");
            requirePositive(command.getReceivedQuantity(), "receivedQuantity"); requireUom(command.getUnitOfMeasure());
            InboxResolution resolution = resolveInbox(context, command.getSourceEventId(),
                    "warehouse.purchase_receipt_line.received", command.getReceiptLineId(),
                    command.getSourceVersion(), command.getEvidenceSha256(), command.getSourceOccurredAt());
            EventInbox inbox = resolution.inbox();
            if (resolution.duplicate()) {
                String existingId = nonNull(mapper.selectReceiptEvidenceIdByInbox(context.tenantId(), inbox.getInboxId()),
                        "receipt evidence is missing for duplicate source event");
                return duplicateOutcome("finance_receipt_line_evidence", existingId, command.getSourceVersion(),
                        payload("receipt_id", command.getReceiptId(), "receipt_line_id", command.getReceiptLineId(),
                                "purchase_order_item_id", command.getPurchaseOrderItemId()));
            }
            requireMonotonic(command.getSourceVersion(), mapper.selectMaxReceiptEvidenceVersion(
                    context.tenantId(), command.getReceiptLineId()), "receipt line");
            PurchaseOrderLineEvidence po = nonNull(mapper.selectPoEvidenceForUpdate(context.tenantId(),
                    command.getPurchaseOrderItemId(), command.getPurchaseOrderLineVersion()),
                    "referenced purchase order line evidence version not found");
            require(Objects.equals(po.getPurchaseOrderId(), command.getPurchaseOrderId()),
                    "receipt purchase order does not match referenced purchase order evidence");
            require(Objects.equals(po.getDeliveryScheduleId(), command.getDeliveryScheduleId()),
                    "receipt delivery schedule does not match referenced purchase order evidence");
            require(Objects.equals(po.getUnitOfMeasure(), upper(command.getUnitOfMeasure())),
                    "receipt unit of measure does not match referenced purchase order evidence");
            require(command.getReceivedQuantity().compareTo(po.getOrderedQuantity()) <= 0,
                    "receipt quantity exceeds referenced ordered quantity");
            String evidenceId = UUID.randomUUID().toString();
            ReceiptLineEvidence row = new ReceiptLineEvidence().setReceiptLineEvidenceId(evidenceId)
                    .setTenantId(context.tenantId()).setInboxId(inbox.getInboxId()).setReceiptId(command.getReceiptId())
                    .setReceiptLineId(command.getReceiptLineId()).setPurchaseOrderId(command.getPurchaseOrderId())
                    .setPurchaseOrderItemId(command.getPurchaseOrderItemId())
                    .setPurchaseOrderLineVersion(command.getPurchaseOrderLineVersion())
                    .setDeliveryScheduleId(command.getDeliveryScheduleId())
                    .setReceivedQuantity(command.getReceivedQuantity()).setUnitOfMeasure(upper(command.getUnitOfMeasure()))
                    .setSourceVersion(command.getSourceVersion()).setCreatedAt(context.now());
            require(mapper.insertReceiptLineEvidence(row) == 1, "failed to persist receipt evidence");
            return outcome("finance.p2p_evidence.receipt_line_recorded", "finance_receipt_line_evidence",
                    evidenceId, command.getSourceVersion(), "RECORDED",
                    payload("receipt_id", command.getReceiptId(), "receipt_line_id", command.getReceiptLineId(),
                            "purchase_order_item_id", command.getPurchaseOrderItemId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult ingestQualityDisposition(P2pEvidenceCommands.QualityDisposition command,
                                                       String actorPrincipalId) {
        return execute(command.getEnvelope(), "INGEST_QUALITY_DISPOSITION", actorPrincipalId, command, context -> {
            validateSourceEvidence(command.getSourceEventId(), command.getSourceVersion(),
                    command.getEvidenceSha256(), command.getSourceOccurredAt());
            requireRef(command.getQualityDispositionId(), "qualityDispositionId");
            requireRef(command.getReceiptLineId(), "receiptLineId");
            require(command.getReceiptLineVersion() != null && command.getReceiptLineVersion() > 0,
                    "receiptLineVersion must be positive");
            requireRef(command.getPurchaseOrderItemId(), "purchaseOrderItemId"); requireUom(command.getUnitOfMeasure());
            requireNonNegative(command.getInspectedQuantity(), "inspectedQuantity");
            requireNonNegative(command.getAcceptedQuantity(), "acceptedQuantity");
            requireNonNegative(command.getRejectedQuantity(), "rejectedQuantity");
            requireNonNegative(command.getHeldQuantity(), "heldQuantity");
            require(command.getInspectedQuantity().compareTo(command.getAcceptedQuantity()
                            .add(command.getRejectedQuantity()).add(command.getHeldQuantity())) == 0,
                    "quality disposition quantities must balance");
            InboxResolution resolution = resolveInbox(context, command.getSourceEventId(),
                    "quality.purchase_receipt_line.dispositioned", command.getQualityDispositionId(),
                    command.getSourceVersion(), command.getEvidenceSha256(), command.getSourceOccurredAt());
            EventInbox inbox = resolution.inbox();
            if (resolution.duplicate()) {
                String existingId = nonNull(mapper.selectQualityEvidenceIdByInbox(context.tenantId(), inbox.getInboxId()),
                        "quality evidence is missing for duplicate source event");
                return duplicateOutcome("finance_quality_disposition_evidence", existingId, command.getSourceVersion(),
                        payload("quality_disposition_id", command.getQualityDispositionId(),
                                "receipt_line_id", command.getReceiptLineId()));
            }
            requireMonotonic(command.getSourceVersion(), mapper.selectMaxQualityEvidenceVersion(
                    context.tenantId(), command.getQualityDispositionId()), "quality disposition");
            ReceiptLineEvidence receipt = nonNull(mapper.selectReceiptEvidenceForUpdate(context.tenantId(),
                    command.getReceiptLineId(), command.getReceiptLineVersion()),
                    "referenced receipt line evidence version not found");
            require(Objects.equals(receipt.getPurchaseOrderItemId(), command.getPurchaseOrderItemId()),
                    "quality purchase order item does not match referenced receipt evidence");
            require(Objects.equals(receipt.getUnitOfMeasure(), upper(command.getUnitOfMeasure())),
                    "quality unit of measure does not match referenced receipt evidence");
            require(command.getInspectedQuantity().compareTo(receipt.getReceivedQuantity()) <= 0,
                    "inspected quantity exceeds referenced receipt quantity");
            String evidenceId = UUID.randomUUID().toString();
            QualityDispositionEvidence row = new QualityDispositionEvidence().setQualityEvidenceId(evidenceId)
                    .setTenantId(context.tenantId()).setInboxId(inbox.getInboxId())
                    .setQualityDispositionId(command.getQualityDispositionId()).setReceiptLineId(command.getReceiptLineId())
                    .setReceiptLineVersion(command.getReceiptLineVersion())
                    .setPurchaseOrderItemId(command.getPurchaseOrderItemId())
                    .setInspectedQuantity(command.getInspectedQuantity()).setAcceptedQuantity(command.getAcceptedQuantity())
                    .setRejectedQuantity(command.getRejectedQuantity()).setHeldQuantity(command.getHeldQuantity())
                    .setUnitOfMeasure(upper(command.getUnitOfMeasure())).setSourceVersion(command.getSourceVersion())
                    .setCreatedAt(context.now());
            require(mapper.insertQualityDispositionEvidence(row) == 1, "failed to persist quality evidence");
            return outcome("finance.p2p_evidence.quality_disposition_recorded",
                    "finance_quality_disposition_evidence", evidenceId, command.getSourceVersion(), "RECORDED",
                    payload("quality_disposition_id", command.getQualityDispositionId(),
                            "receipt_line_id", command.getReceiptLineId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult ingestInventoryMovement(P2pEvidenceCommands.InventoryMovement command,
                                                      String actorPrincipalId) {
        return execute(command.getEnvelope(), "INGEST_INVENTORY_MOVEMENT", actorPrincipalId, command, context -> {
            validateSourceEvidence(command.getSourceEventId(), command.getSourceVersion(),
                    command.getEvidenceSha256(), command.getSourceOccurredAt());
            requireRef(command.getInventoryMovementId(), "inventoryMovementId");
            requireRef(command.getReceiptLineId(), "receiptLineId");
            requireRef(command.getQualityDispositionId(), "qualityDispositionId");
            require(command.getQualityDispositionVersion() != null && command.getQualityDispositionVersion() > 0,
                    "qualityDispositionVersion must be positive");
            String disposition = upper(command.getDisposition());
            require(Set.of("ACCEPTED", "REJECTED", "QUARANTINED").contains(disposition),
                    "disposition must be ACCEPTED, REJECTED, or QUARANTINED");
            requireRef(command.getPurchaseOrderItemId(), "purchaseOrderItemId");
            requirePositive(command.getMovementQuantity(), "movementQuantity"); requireUom(command.getUnitOfMeasure());
            requireNonNegative(command.getUnitCostAmountMinor(), "unitCostAmountMinor");
            requireNonNegative(command.getMovementCostAmountMinor(), "movementCostAmountMinor");
            long calculatedCost = BigDecimal.valueOf(command.getUnitCostAmountMinor())
                    .multiply(command.getMovementQuantity()).setScale(0, RoundingMode.HALF_UP).longValueExact();
            require(calculatedCost == command.getMovementCostAmountMinor(),
                    "movement cost must equal unit cost multiplied by movement quantity using HALF_UP minor-unit rounding");
            requireCurrency(command.getCurrencyCode()); requireRef(command.getValuationPolicyId(), "valuationPolicyId");
            requireRef(command.getValuationPolicyVersion(), "valuationPolicyVersion");
            InboxResolution resolution = resolveInbox(context, command.getSourceEventId(), "inventory.stock.changed",
                    command.getInventoryMovementId(), command.getSourceVersion(), command.getEvidenceSha256(),
                    command.getSourceOccurredAt());
            EventInbox inbox = resolution.inbox();
            if (resolution.duplicate()) {
                String existingId = nonNull(mapper.selectInventoryEvidenceIdByInbox(context.tenantId(), inbox.getInboxId()),
                        "inventory evidence is missing for duplicate source event");
                return duplicateOutcome("finance_inventory_movement_evidence", existingId, command.getSourceVersion(),
                        payload("inventory_movement_id", command.getInventoryMovementId(),
                                "receipt_line_id", command.getReceiptLineId()));
            }
            requireMonotonic(command.getSourceVersion(), mapper.selectMaxInventoryEvidenceVersion(
                    context.tenantId(), command.getInventoryMovementId()), "inventory movement");
            QualityDispositionEvidence quality = nonNull(mapper.selectQualityEvidenceForUpdate(context.tenantId(),
                    command.getQualityDispositionId(), command.getQualityDispositionVersion()),
                    "referenced quality disposition evidence version not found");
            require(Objects.equals(quality.getReceiptLineId(), command.getReceiptLineId()),
                    "inventory receipt line does not match referenced quality evidence");
            require(Objects.equals(quality.getPurchaseOrderItemId(), command.getPurchaseOrderItemId()),
                    "inventory purchase order item does not match referenced quality evidence");
            require(Objects.equals(quality.getUnitOfMeasure(), upper(command.getUnitOfMeasure())),
                    "inventory unit of measure does not match referenced quality evidence");
            BigDecimal dispositionQuantity = switch (disposition) {
                case "ACCEPTED" -> quality.getAcceptedQuantity();
                case "REJECTED" -> quality.getRejectedQuantity();
                case "QUARANTINED" -> quality.getHeldQuantity();
                default -> throw new IllegalStateException("unsupported inventory disposition");
            };
            require(command.getMovementQuantity().compareTo(dispositionQuantity) <= 0,
                    "inventory movement quantity exceeds matching quality disposition quantity");
            String evidenceId = UUID.randomUUID().toString();
            InventoryMovementEvidence row = new InventoryMovementEvidence().setInventoryEvidenceId(evidenceId)
                    .setTenantId(context.tenantId()).setInboxId(inbox.getInboxId())
                    .setInventoryMovementId(command.getInventoryMovementId()).setReceiptLineId(command.getReceiptLineId())
                    .setQualityDispositionId(command.getQualityDispositionId())
                    .setQualityDispositionVersion(command.getQualityDispositionVersion())
                    .setDisposition(disposition)
                    .setPurchaseOrderItemId(command.getPurchaseOrderItemId())
                    .setMovementQuantity(command.getMovementQuantity()).setUnitOfMeasure(upper(command.getUnitOfMeasure()))
                    .setUnitCostAmountMinor(command.getUnitCostAmountMinor())
                    .setMovementCostAmountMinor(command.getMovementCostAmountMinor())
                    .setCurrencyCode(upper(command.getCurrencyCode())).setValuationPolicyId(command.getValuationPolicyId())
                    .setValuationPolicyVersion(command.getValuationPolicyVersion()).setSourceVersion(command.getSourceVersion())
                    .setCreatedAt(context.now());
            require(mapper.insertInventoryMovementEvidence(row) == 1, "failed to persist inventory evidence");
            return outcome("finance.p2p_evidence.inventory_movement_recorded",
                    "finance_inventory_movement_evidence", evidenceId, command.getSourceVersion(), "RECORDED",
                    payload("inventory_movement_id", command.getInventoryMovementId(),
                            "receipt_line_id", command.getReceiptLineId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult postQualifiedReceipt(P2pEvidenceCommands.PostQualifiedReceipt command,
                                                   String actorPrincipalId) {
        return execute(command.getEnvelope(), "POST_QUALIFIED_RECEIPT", actorPrincipalId, command, context -> {
            requireRef(command.getInventoryMovementId(), "inventoryMovementId");
            require(command.getInventoryMovementVersion() != null && command.getInventoryMovementVersion() > 0,
                    "inventoryMovementVersion must be positive"); requireRef(command.getLedgerId(), "ledgerId");
            requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            require(command.getAccountingDate() != null, "accountingDate is required");
            InventoryMovementEvidence evidence = nonNull(mapper.selectInventoryEvidenceForUpdate(context.tenantId(),
                    command.getInventoryMovementId(), command.getInventoryMovementVersion()),
                    "frozen inventory movement cost evidence not found");
            require("ACCEPTED".equals(evidence.getDisposition()),
                    "only ACCEPTED inventory movement evidence may be posted as a qualified receipt");
            require(evidence.getMovementQuantity().signum() > 0 && evidence.getMovementCostAmountMinor() >= 0,
                    "qualified receipt requires positive frozen quantity and nonnegative cost");
            require(mapper.countActiveValuationPolicy(context.tenantId(), evidence.getValuationPolicyId(),
                    evidence.getValuationPolicyVersion(), command.getLedgerId()) == 1,
                    "inventory valuation policy is not active for the posting ledger");
            long calculatedCost = BigDecimal.valueOf(evidence.getUnitCostAmountMinor())
                    .multiply(evidence.getMovementQuantity()).setScale(0, RoundingMode.HALF_UP).longValueExact();
            require(calculatedCost == evidence.getMovementCostAmountMinor(),
                    "frozen inventory valuation cost does not balance quantity and unit cost");
            PurchaseOrderLineEvidence purchaseOrder = nonNull(mapper.selectPoEvidenceForInventory(context.tenantId(),
                    evidence.getInventoryMovementId(), evidence.getSourceVersion()),
                    "exact purchase order lineage for qualified receipt not found");
            require(purchaseOrder.getCurrencyCode().equals(evidence.getCurrencyCode()),
                    "qualified receipt valuation currency must equal purchase order currency");
            EventInbox sourceInbox = nonNull(mapper.selectInbox(context.tenantId(), evidence.getInboxId()),
                    "inventory movement evidence envelope not found");
            AccountingPeriod period = requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(),
                    command.getAccountingDate());
            require(period.getCurrencyCode().equals(evidence.getCurrencyCode()),
                    "valuation currency must equal accounting period currency");
            Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), command.getPostingRuleId(),
                    command.getPostingRuleVersion(), command.getLedgerId(), "QUALIFIED_RECEIPT",
                    Set.of("INVENTORY", "GRIR"));
            String layerId = UUID.randomUUID().toString();
            String journalId = evidence.getMovementCostAmountMinor() > 0 ? UUID.randomUUID().toString() : null;
            InventoryValuationLayer layer = new InventoryValuationLayer().setValuationLayerId(layerId)
                    .setTenantId(context.tenantId()).setLedgerId(command.getLedgerId())
                    .setInventoryMovementId(evidence.getInventoryMovementId())
                    .setInventoryMovementVersion(evidence.getSourceVersion()).setReceiptLineId(evidence.getReceiptLineId())
                    .setQualityDispositionId(evidence.getQualityDispositionId())
                    .setPurchaseOrderItemId(evidence.getPurchaseOrderItemId())
                    .setValuationPolicyId(evidence.getValuationPolicyId())
                    .setValuationPolicyVersion(evidence.getValuationPolicyVersion())
                    .setQuantity(evidence.getMovementQuantity()).setUnitOfMeasure(evidence.getUnitOfMeasure())
                    .setUnitCostAmountMinor(evidence.getUnitCostAmountMinor())
                    .setTotalCostAmountMinor(evidence.getMovementCostAmountMinor())
                    .setCurrencyCode(evidence.getCurrencyCode()).setRemainingQuantity(evidence.getMovementQuantity())
                    .setRemainingCostAmountMinor(evidence.getMovementCostAmountMinor()).setStatus("OPEN").setVersion(1L)
                    .setCreatedAt(context.now()).setUpdatedAt(context.now());
            require(mapper.insertValuationLayer(layer) == 1, "failed to create inventory valuation layer");
            if (journalId != null) {
                List<JournalLine> lines = List.of(
                        journalLine(context, journalId, 1, accounts.get("INVENTORY"),
                                evidence.getMovementCostAmountMinor(), 0L, evidence.getCurrencyCode(), null, null, null),
                        journalLine(context, journalId, 2, accounts.get("GRIR"), 0L,
                                evidence.getMovementCostAmountMinor(), evidence.getCurrencyCode(), null, null, null));
                insertPostedJournal(context, journalId, "QR-" + evidence.getInventoryMovementId(),
                        purchaseOrder.getLegalEntityId(),
                        command.getLedgerId(), command.getAccountingPeriodId(), command.getAccountingDate(),
                        "QUALIFIED_RECEIPT", evidence.getInventoryMovementId(), evidence.getCurrencyCode(),
                        evidence.getMovementCostAmountMinor(), sourceInbox.getEvidenceSha256(),
                        lines, "QUALIFIED_RECEIPT", command.getJournalDimensions());
            }
            require(mapper.insertQualifiedReceiptValuationEffect(UUID.randomUUID().toString(), context.tenantId(),
                    layerId, evidence.getInventoryMovementId(), evidence.getSourceVersion(), evidence.getMovementQuantity(),
                    evidence.getMovementCostAmountMinor(), evidence.getCurrencyCode(), journalId, context.now()) == 1,
                    "failed to create qualified receipt valuation effect");
            return outcome("finance.qualified_receipt.posted", "finance_inventory_valuation_layer", layerId, 1L,
                    "POSTED", payload("journal_entry_id", journalId,
                            "inventory_movement_id", evidence.getInventoryMovementId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult create(SupplierInvoiceCommands.Create command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "CREATE_SUPPLIER_INVOICE", actorPrincipalId, command, context -> {
            requireRef(command.getInvoiceCode(), "invoiceCode"); requireRef(command.getLegalEntityId(), "legalEntityId");
            requireRef(command.getLedgerId(), "ledgerId"); requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            requireRef(command.getSupplierId(), "supplierId");
            requireRef(command.getSupplierInvoiceNumber(), "supplierInvoiceNumber");
            require(Set.of("INVOICE", "CREDIT_NOTE").contains(upper(command.getInvoiceType())), "invoiceType is invalid");
            requireCurrency(command.getCurrencyCode()); require(command.getIssueDate() != null, "issueDate is required");
            require(command.getAccountingDate() != null, "accountingDate is required");
            require(command.getDueDate() != null && !command.getDueDate().isBefore(command.getIssueDate()),
                    "dueDate is invalid"); requireSha256(command.getEvidenceSha256(), "evidenceSha256");
            requireAmounts(command.getNetAmountMinor(), command.getTaxAmountMinor(), command.getGrossAmountMinor());
            requireRef(command.getPaymentTermId(), "paymentTermId");
            require(command.getPaymentTermVersion() != null && command.getPaymentTermVersion() > 0,
                    "paymentTermVersion must be positive");
            require(mapper.countActivePaymentTerm(context.tenantId(), command.getPaymentTermId(),
                    command.getPaymentTermVersion(), command.getLegalEntityId()) == 1,
                    "active payment term version not found for legal entity");
            List<PaymentTermRule> termRules = mapper.selectPaymentTermRules(context.tenantId(),
                    command.getPaymentTermId(), command.getPaymentTermVersion());
            require(!termRules.isEmpty(), "payment term has no installment rules");
            int basisPoints = termRules.stream().mapToInt(PaymentTermRule::getAllocationBasisPoints).sum();
            require(basisPoints == 10000, "payment term installment rules do not total 10000 basis points");
            java.time.LocalDate calculatedDue = command.getIssueDate().plusDays(termRules.stream()
                    .mapToInt(PaymentTermRule::getDueDaysAfterIssue).max().orElseThrow());
            require(calculatedDue.equals(command.getDueDate()), "invoice dueDate must follow payment term version");
            postingAccounts(context.tenantId(), command.getPostingRuleId(), command.getPostingRuleVersion(),
                    command.getLedgerId(), "SUPPLIER_INVOICE",
                    Set.of("GRIR", "INPUT_TAX", "PURCHASE_PRICE_VARIANCE", "AP"));
            require(command.getLines() != null && !command.getLines().isEmpty(), "invoice lines are required");
            long net = 0, tax = 0, gross = 0; Set<Integer> lineNumbers = new HashSet<>();
            for (SupplierInvoiceCommands.Line line : command.getLines()) {
                validateInvoiceLine(line); require(lineNumbers.add(line.getLineNumber()), "invoice lineNumber must be unique");
                net = Math.addExact(net, line.getNetAmountMinor()); tax = Math.addExact(tax, line.getTaxAmountMinor());
                gross = Math.addExact(gross, line.getGrossAmountMinor());
            }
            require(net == command.getNetAmountMinor() && tax == command.getTaxAmountMinor()
                    && gross == command.getGrossAmountMinor(), "invoice header totals must equal line totals");
            requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getAccountingDate());
            String invoiceId = valueOrUuid(command.getSupplierInvoiceId());
            SupplierInvoice invoice = new SupplierInvoice().setSupplierInvoiceId(invoiceId)
                    .setTenantId(context.tenantId()).setInvoiceCode(command.getInvoiceCode())
                    .setLegalEntityId(command.getLegalEntityId()).setLedgerId(command.getLedgerId())
                    .setAccountingPeriodId(command.getAccountingPeriodId()).setSupplierId(command.getSupplierId())
                    .setSupplierInvoiceNumber(command.getSupplierInvoiceNumber()).setInvoiceType(upper(command.getInvoiceType()))
                    .setCurrencyCode(upper(command.getCurrencyCode())).setIssueDate(command.getIssueDate())
                    .setAccountingDate(command.getAccountingDate()).setDueDate(command.getDueDate())
                    .setNetAmountMinor(net).setTaxAmountMinor(tax).setGrossAmountMinor(gross)
                    .setLifecycleStatus("DRAFT").setMatchStatus("NOT_STARTED").setSettlementStatus("UNPAID")
                    .setEvidenceSha256(command.getEvidenceSha256()).setPaymentTermId(command.getPaymentTermId())
                    .setPaymentTermVersion(command.getPaymentTermVersion()).setPostingRuleId(command.getPostingRuleId())
                    .setPostingRuleVersion(command.getPostingRuleVersion()).setCreatedByPrincipalId(context.actor())
                    .setVersion(1L).setCreatedAt(context.now()).setUpdatedAt(context.now());
            require(mapper.insertSupplierInvoice(invoice) == 1, "failed to persist supplier invoice");
            for (SupplierInvoiceCommands.Line input : command.getLines()) {
                SupplierInvoiceLine line = new SupplierInvoiceLine().setInvoiceLineId(valueOrUuid(input.getInvoiceLineId()))
                        .setTenantId(context.tenantId()).setSupplierInvoiceId(invoiceId).setLineNumber(input.getLineNumber())
                        .setPurchaseOrderId(input.getPurchaseOrderId()).setPurchaseOrderItemId(input.getPurchaseOrderItemId())
                        .setSkuId(input.getSkuId()).setQuantity(input.getQuantity())
                        .setUnitOfMeasure(upper(input.getUnitOfMeasure())).setUnitNetPrice(input.getUnitNetPrice())
                        .setNetAmountMinor(input.getNetAmountMinor()).setTaxCode(upper(input.getTaxCode()))
                        .setTaxRate(input.getTaxRate()).setTaxAmountMinor(input.getTaxAmountMinor())
                        .setGrossAmountMinor(input.getGrossAmountMinor()).setVersion(1L)
                        .setCreatedAt(context.now()).setUpdatedAt(context.now());
                require(mapper.insertSupplierInvoiceLine(line) == 1, "failed to persist supplier invoice line");
            }
            mapper.insertInvoiceHistory(context.tenantId(), invoiceId, null, "DRAFT", "NOT_STARTED",
                    context.actor(), null, 1L, context.now());
            return outcome("finance.supplier_invoice.created", "finance_supplier_invoice", invoiceId, 1L, "DRAFT",
                    payload("supplier_invoice_id", invoiceId, "gross_amount_minor", gross,
                            "currency_code", invoice.getCurrencyCode()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult submit(SupplierInvoiceCommands.Transition command, String actorPrincipalId) {
        return transitionInvoice(command, actorPrincipalId, "SUBMIT_SUPPLIER_INVOICE", "DRAFT", "SUBMITTED",
                "finance.supplier_invoice.submitted", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult approve(SupplierInvoiceCommands.Transition command, String actorPrincipalId) {
        return transitionInvoice(command, actorPrincipalId, "APPROVE_SUPPLIER_INVOICE", "SUBMITTED", "APPROVED",
                "finance.supplier_invoice.approved", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult runThreeWayMatch(SupplierInvoiceCommands.Match command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "RUN_THREE_WAY_MATCH", actorPrincipalId, command, context -> {
            requireRef(command.getSupplierInvoiceId(), "supplierInvoiceId");
            requireRef(command.getMatchPolicyId(), "matchPolicyId");
            require(command.getMatchPolicyVersion() != null && command.getMatchPolicyVersion() > 0,
                    "matchPolicyVersion must be positive");
            SupplierInvoice invoice = nonNull(mapper.selectInvoiceForUpdate(context.tenantId(),
                    command.getSupplierInvoiceId()), "supplier invoice not found");
            MatchPolicy policy = nonNull(mapper.selectMatchPolicy(context.tenantId(), command.getMatchPolicyId(),
                    command.getMatchPolicyVersion()), "active match policy version not found");
            require("ACTIVE".equals(policy.getStatus()) && invoice.getLegalEntityId().equals(policy.getLegalEntityId()),
                    "match policy does not govern invoice legal entity");
            requireExpectedVersion(command.getExpectedVersion(), invoice.getVersion());
            require("SUBMITTED".equals(invoice.getLifecycleStatus()), "only submitted invoice can be matched");
            require(Set.of("NOT_STARTED", "EXCEPTION").contains(invoice.getMatchStatus()),
                    "invoice is not eligible for a match run");
            require(mapper.transitionInvoiceMatch(context.tenantId(), invoice.getSupplierInvoiceId(),
                    invoice.getVersion(), invoice.getMatchStatus(), "RUNNING", context.now()) == 1,
                    "invoice match start conflict");
            if ("EXCEPTION".equals(invoice.getMatchStatus())) {
                require(mapper.supersedeExceptionMatchRuns(context.tenantId(), invoice.getSupplierInvoiceId()) == 1,
                        "previous exception match run is inconsistent");
                mapper.supersedePendingMatchAllocations(context.tenantId(), invoice.getSupplierInvoiceId());
                mapper.supersedeOpenMatchExceptions(context.tenantId(), invoice.getSupplierInvoiceId(), context.now());
            }
            List<SupplierInvoiceLine> invoiceLines = mapper.selectInvoiceLines(context.tenantId(), invoice.getSupplierInvoiceId());
            require(!invoiceLines.isEmpty(), "supplier invoice has no lines");
            String runId = UUID.randomUUID().toString();
            MatchRun run = new MatchRun().setMatchRunId(runId).setTenantId(context.tenantId())
                    .setSupplierInvoiceId(invoice.getSupplierInvoiceId()).setInvoiceVersion(invoice.getVersion() + 1)
                    .setMatchPolicyId(command.getMatchPolicyId()).setMatchPolicyVersion(command.getMatchPolicyVersion())
                    .setPriceToleranceAmountMinor(policy.getPriceToleranceAmountMinor())
                    .setTaxToleranceAmountMinor(policy.getTaxToleranceAmountMinor())
                    .setQuantityTolerance(policy.getQuantityTolerance()).setStatus("RUNNING")
                    .setRequestedByPrincipalId(context.actor()).setVersion(1L).setCreatedAt(context.now());
            require(mapper.insertMatchRun(run) == 1, "failed to persist match run");
            List<LinePlan> plans = new ArrayList<>(); boolean hasException = false;
            for (SupplierInvoiceLine line : invoiceLines) {
                LinePlan plan = planMatchLine(context, invoice, line, policy, runId);
                plans.add(plan); hasException |= !plan.exceptions().isEmpty();
            }
            String firstExceptionId = null;
            for (LinePlan plan : plans) {
                MatchLine matchLine = plan.line();
                matchLine.setResultStatus(plan.exceptions().isEmpty() ? "MATCHED" : "EXCEPTION");
                require(mapper.insertMatchLine(matchLine) == 1, "failed to persist match line");
                String allocationStatus = hasException ? "PENDING_OVERRIDE" : "ACTIVE";
                if (!plan.hasQuantityShortage()) {
                    for (MatchAllocation allocation : plan.allocations()) {
                        allocation.setStatus(allocationStatus);
                        require(mapper.insertMatchAllocation(allocation) == 1, "failed to persist match allocation");
                    }
                }
                for (ExceptionPlan exceptionPlan : plan.exceptions()) {
                    String exceptionId = UUID.randomUUID().toString();
                    if (firstExceptionId == null) firstExceptionId = exceptionId;
                    MatchException exception = new MatchException().setExceptionId(exceptionId)
                            .setTenantId(context.tenantId()).setMatchRunId(runId)
                            .setMatchLineId(matchLine.getMatchLineId()).setExceptionCode(exceptionPlan.code())
                            .setExceptionType(exceptionPlan.type())
                            .setBlocking(true).setExpectedQuantity(exceptionPlan.expectedQuantity())
                            .setActualQuantity(exceptionPlan.actualQuantity())
                            .setExpectedAmountMinor(exceptionPlan.expectedAmountMinor())
                            .setActualAmountMinor(exceptionPlan.actualAmountMinor())
                            .setExpectedCode(exceptionPlan.expectedCode()).setActualCode(exceptionPlan.actualCode())
                            .setStatus("OPEN")
                            .setOpenedByPrincipalId(context.actor()).setVersion(1L).setOpenedAt(context.now());
                    require(mapper.insertMatchException(exception) == 1, "failed to persist match exception");
                }
            }
            String resultStatus = hasException ? "EXCEPTION" : "MATCHED";
            require(mapper.completeMatchRun(context.tenantId(), runId, resultStatus, context.now()) == 1,
                    "match run completion conflict");
            require(mapper.transitionInvoiceMatch(context.tenantId(), invoice.getSupplierInvoiceId(),
                    invoice.getVersion() + 1, "RUNNING", resultStatus, context.now()) == 1,
                    "invoice match completion conflict");
            mapper.insertInvoiceHistory(context.tenantId(), invoice.getSupplierInvoiceId(),
                    invoice.getLifecycleStatus(), invoice.getLifecycleStatus(), resultStatus, context.actor(),
                    null, invoice.getVersion() + 2, context.now());
            return outcome(hasException ? "finance.supplier_invoice.match_exception_opened"
                            : "finance.supplier_invoice.matched",
                    "finance_invoice_match_run", runId, 2L, resultStatus,
                    payload("supplier_invoice_id", invoice.getSupplierInvoiceId(), "match_run_id", runId,
                            "exception_id", firstExceptionId, "current_status", resultStatus));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult approveMatchOverride(SupplierInvoiceCommands.ApproveOverride command,
                                                   String actorPrincipalId) {
        return execute(command.getEnvelope(), "APPROVE_MATCH_OVERRIDE", actorPrincipalId, command, context -> {
            requireRef(command.getExceptionId(), "exceptionId"); requireSha256(command.getResolutionEvidenceSha256(),
                    "resolutionEvidenceSha256"); requireRef(command.getReasonCode(), "reasonCode");
            MatchException exception = nonNull(mapper.selectMatchExceptionForUpdate(context.tenantId(),
                    command.getExceptionId()), "match exception not found");
            require(mapper.countCurrentlyOverridableException(context.tenantId(), exception.getExceptionId()) == 1,
                    "match exception is not part of the current exception run");
            requireExpectedVersion(command.getExpectedVersion(), exception.getVersion());
            require("OPEN".equals(exception.getStatus()), "only open match exception can be overridden");
            require(Set.of("PRICE", "TAX").contains(exception.getExceptionType()),
                    "quantity, currency, UOM and missing evidence exceptions cannot be overridden");
            require(!context.actor().equals(exception.getOpenedByPrincipalId()),
                    "match override approver must be independent from exception opener");
            require(mapper.approveMatchException(context.tenantId(), exception.getExceptionId(), exception.getVersion(),
                    context.actor(), command.getResolutionEvidenceSha256(), upper(command.getReasonCode()),
                    context.now()) == 1, "match exception override conflict");
            require(mapper.insertOverrideResolution(UUID.randomUUID().toString(), context.tenantId(),
                    exception.getExceptionId(), context.actor(), command.getResolutionEvidenceSha256(),
                    upper(command.getReasonCode()), context.now()) == 1, "failed to persist override evidence");
            if (mapper.countOpenMatchExceptions(context.tenantId(), exception.getMatchRunId()) == 0) {
                mapper.activateMatchAllocations(context.tenantId(), exception.getMatchRunId());
                require(mapper.markMatchRunOverridden(context.tenantId(), exception.getMatchRunId()) == 1,
                        "match run override completion conflict");
                String invoiceId = mapper.selectInvoiceIdByMatchRun(context.tenantId(), exception.getMatchRunId());
                SupplierInvoice invoice = nonNull(mapper.selectInvoiceForUpdate(context.tenantId(), invoiceId),
                        "match invoice not found");
                require(mapper.transitionInvoiceMatch(context.tenantId(), invoiceId, invoice.getVersion(),
                        "EXCEPTION", "MATCHED", context.now()) == 1, "invoice override completion conflict");
            }
            return outcome("finance.invoice_match_exception.override_approved", "finance_match_exception",
                    exception.getExceptionId(), exception.getVersion() + 1, "OVERRIDE_APPROVED",
                    payload("supplier_invoice_id", mapper.selectInvoiceIdByMatchRun(context.tenantId(),
                                    exception.getMatchRunId()),
                            "match_run_id", exception.getMatchRunId(), "exception_id", exception.getExceptionId()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult post(SupplierInvoiceCommands.Transition command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "POST_SUPPLIER_INVOICE", actorPrincipalId, command, context -> {
            requireRef(command.getSupplierInvoiceId(), "supplierInvoiceId");
            SupplierInvoice invoice = nonNull(mapper.selectInvoiceForUpdate(context.tenantId(),
                    command.getSupplierInvoiceId()), "supplier invoice not found");
            requireExpectedVersion(command.getExpectedVersion(), invoice.getVersion());
            require("APPROVED".equals(invoice.getLifecycleStatus()), "only approved invoice can be posted");
            require("MATCHED".equals(invoice.getMatchStatus()), "three-way match must be complete before posting");
            require(!context.actor().equals(invoice.getApprovedByPrincipalId()),
                    "invoice poster must be independent from invoice approver");
            requireOpenPeriod(context.tenantId(), invoice.getAccountingPeriodId(), invoice.getAccountingDate());
            Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), invoice.getPostingRuleId(),
                    invoice.getPostingRuleVersion(), invoice.getLedgerId(), "SUPPLIER_INVOICE",
                    Set.of("GRIR", "INPUT_TAX", "PURCHASE_PRICE_VARIANCE", "AP"));
            String apId = UUID.randomUUID().toString(); String journalId = UUID.randomUUID().toString();
            ApOpenItem ap = new ApOpenItem().setApOpenItemId(apId).setTenantId(context.tenantId())
                    .setSupplierInvoiceId(invoice.getSupplierInvoiceId()).setLegalEntityId(invoice.getLegalEntityId())
                    .setLedgerId(invoice.getLedgerId()).setSupplierId(invoice.getSupplierId())
                    .setCurrencyCode(invoice.getCurrencyCode()).setOriginalAmountMinor(invoice.getGrossAmountMinor())
                    .setSettledAmountMinor(0L).setOpenAmountMinor(invoice.getGrossAmountMinor()).setDueDate(invoice.getDueDate())
                    .setStatus("OPEN").setVersion(1L).setCreatedAt(context.now()).setUpdatedAt(context.now());
            require(mapper.insertApOpenItem(ap) == 1, "failed to create AP open item");
            List<PaymentTermRule> termRules = mapper.selectPaymentTermRules(context.tenantId(),
                    invoice.getPaymentTermId(), invoice.getPaymentTermVersion());
            require(!termRules.isEmpty(), "invoice payment term rules are missing");
            long scheduled = 0;
            for (int index = 0; index < termRules.size(); index++) {
                PaymentTermRule rule = termRules.get(index);
                long amount = index == termRules.size() - 1 ? invoice.getGrossAmountMinor() - scheduled
                        : BigDecimal.valueOf(invoice.getGrossAmountMinor())
                                .multiply(BigDecimal.valueOf(rule.getAllocationBasisPoints()))
                                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP).longValueExact();
                scheduled = Math.addExact(scheduled, amount);
                require(mapper.insertApInstallment(UUID.randomUUID().toString(), context.tenantId(), apId,
                        rule.getInstallmentNumber(), invoice.getIssueDate().plusDays(rule.getDueDaysAfterIssue()),
                        amount, context.now()) == 1, "failed to create AP installment");
            }
            require(scheduled == invoice.getGrossAmountMinor(), "AP installment amounts do not balance invoice");
            mapper.insertApHistory(context.tenantId(), apId, null, "OPEN", invoice.getGrossAmountMinor(),
                    "SUPPLIER_INVOICE", invoice.getSupplierInvoiceId(), 1L, context.now());
            Long receiptValuation = mapper.sumMatchedReceiptValuation(context.tenantId(), invoice.getSupplierInvoiceId());
            require(receiptValuation != null && receiptValuation > 0,
                    "qualified receipt valuation/GRIR evidence is missing");
            List<JournalLine> lines = invoicePostingLines(context, invoice, apId, journalId, receiptValuation, accounts);
            insertPostedJournal(context, journalId, "PI-" + invoice.getInvoiceCode(), invoice.getLegalEntityId(),
                    invoice.getLedgerId(), invoice.getAccountingPeriodId(), invoice.getAccountingDate(),
                    "SUPPLIER_INVOICE", invoice.getSupplierInvoiceId(), invoice.getCurrencyCode(),
                    invoice.getGrossAmountMinor(), invoice.getEvidenceSha256(), lines, "INVOICE_POSTING",
                    command.getJournalDimensions());
            require(mapper.markInvoicePosted(context.tenantId(), invoice.getSupplierInvoiceId(), invoice.getVersion(),
                    journalId, context.now()) == 1, "invoice posting conflict");
            mapper.insertInvoiceHistory(context.tenantId(), invoice.getSupplierInvoiceId(), "APPROVED", "POSTED",
                    "MATCHED", context.actor(), upper(command.getReasonCode()), invoice.getVersion() + 1, context.now());
            return outcome("finance.supplier_invoice.posted", "finance_supplier_invoice",
                    invoice.getSupplierInvoiceId(), invoice.getVersion() + 1, "POSTED",
                    payload("supplier_invoice_id", invoice.getSupplierInvoiceId(), "ap_open_item_id", apId,
                            "journal_entry_id", journalId, "gross_amount_minor", invoice.getGrossAmountMinor()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult create(SupplierPaymentCommands.Create command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "CREATE_SUPPLIER_PAYMENT", actorPrincipalId, command, context -> {
            requireRef(command.getPaymentCode(), "paymentCode"); requireRef(command.getLegalEntityId(), "legalEntityId");
            requireRef(command.getLedgerId(), "ledgerId"); requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            requireRef(command.getSupplierId(), "supplierId"); requireRef(command.getPayeeInstrumentId(), "payeeInstrumentId");
            requireCurrency(command.getCurrencyCode()); require(command.getRequestedExecutionDate() != null,
                    "requestedExecutionDate is required"); requirePositive(command.getTotalAmountMinor(), "totalAmountMinor");
            require(command.getAllocations() != null && !command.getAllocations().isEmpty(), "payment allocations are required");
            requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getRequestedExecutionDate());
            postingAccounts(context.tenantId(), command.getPostingRuleId(), command.getPostingRuleVersion(),
                    command.getLedgerId(), "SUPPLIER_PAYMENT", Set.of("AP", "BANK_CLEARING"));
            PayeeInstrument payee = nonNull(mapper.selectPayeeInstrumentForUpdate(context.tenantId(),
                    command.getPayeeInstrumentId()), "verified supplier payee instrument not found");
            require("ACTIVE".equals(payee.getStatus()) && "VERIFIED".equals(payee.getVerificationStatus())
                            && command.getSupplierId().equals(payee.getSupplierId())
                            && command.getLegalEntityId().equals(payee.getLegalEntityId())
                            && upper(command.getCurrencyCode()).equals(payee.getCurrencyCode())
                            && !command.getRequestedExecutionDate().isBefore(payee.getValidFrom())
                            && (payee.getValidUntil() == null
                                || !command.getRequestedExecutionDate().isAfter(payee.getValidUntil())),
                    "payee instrument is not valid for supplier, legal entity, currency and execution date");
            long total = 0; Set<String> apIds = new HashSet<>();
            for (SupplierPaymentCommands.Allocation allocation : command.getAllocations()) {
                requireRef(allocation.getApOpenItemId(), "apOpenItemId");
                requirePositive(allocation.getAmountMinor(), "allocation.amountMinor");
                require(apIds.add(allocation.getApOpenItemId()), "AP open item may occur only once per payment");
                ApOpenItem ap = nonNull(mapper.selectApForUpdate(context.tenantId(), allocation.getApOpenItemId()),
                        "AP open item not found");
                require(!"SETTLED".equals(ap.getStatus()), "AP open item is already settled");
                require(command.getSupplierId().equals(ap.getSupplierId())
                                && upper(command.getCurrencyCode()).equals(ap.getCurrencyCode())
                                && command.getLegalEntityId().equals(ap.getLegalEntityId()),
                        "payment allocation must use the AP supplier, legal entity and currency");
                long reserved = Optional.ofNullable(mapper.sumReservedPaymentAmount(context.tenantId(),
                        ap.getApOpenItemId())).orElse(0L);
                require(allocation.getAmountMinor() <= ap.getOpenAmountMinor() - reserved,
                        "payment allocation exceeds available AP open amount");
                total = Math.addExact(total, allocation.getAmountMinor());
            }
            require(total == command.getTotalAmountMinor(), "payment allocation total must equal instruction total");
            String paymentId = valueOrUuid(command.getPaymentInstructionId());
            PaymentInstruction payment = new PaymentInstruction().setPaymentInstructionId(paymentId)
                    .setTenantId(context.tenantId()).setPaymentCode(command.getPaymentCode())
                    .setLegalEntityId(command.getLegalEntityId()).setLedgerId(command.getLedgerId())
                    .setAccountingPeriodId(command.getAccountingPeriodId()).setSupplierId(command.getSupplierId())
                    .setPayeeInstrumentId(command.getPayeeInstrumentId()).setCurrencyCode(upper(command.getCurrencyCode()))
                    .setRequestedExecutionDate(command.getRequestedExecutionDate()).setTotalAmountMinor(total)
                    .setPostingRuleId(command.getPostingRuleId()).setPostingRuleVersion(command.getPostingRuleVersion())
                    .setStatus("DRAFT").setCreatedByPrincipalId(context.actor()).setVersion(1L)
                    .setCreatedAt(context.now()).setUpdatedAt(context.now());
            require(mapper.insertPaymentInstruction(payment) == 1, "failed to persist payment instruction");
            for (SupplierPaymentCommands.Allocation input : command.getAllocations()) {
                PaymentAllocation allocation = new PaymentAllocation()
                        .setPaymentAllocationId(valueOrUuid(input.getPaymentAllocationId()))
                        .setTenantId(context.tenantId()).setPaymentInstructionId(paymentId)
                        .setApOpenItemId(input.getApOpenItemId()).setAmountMinor(input.getAmountMinor())
                        .setCreatedAt(context.now());
                require(mapper.insertPaymentAllocation(allocation) == 1, "failed to persist payment allocation");
            }
            mapper.insertPaymentHistory(context.tenantId(), paymentId, null, "DRAFT", context.actor(),
                    null, 1L, context.now());
            return outcome("finance.supplier_payment.instruction_created", "finance_supplier_payment",
                    paymentId, 1L, "DRAFT", payload("payment_instruction_id", paymentId,
                            "total_amount_minor", total, "currency_code", payment.getCurrencyCode()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult submitForApproval(SupplierPaymentCommands.Transition command, String actorPrincipalId) {
        return transitionPayment(command, actorPrincipalId, "SUBMIT_SUPPLIER_PAYMENT", "DRAFT",
                "SUBMITTED_FOR_APPROVAL", "finance.supplier_payment.submitted_for_approval", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult approve(SupplierPaymentCommands.Transition command, String actorPrincipalId) {
        return transitionPayment(command, actorPrincipalId, "APPROVE_SUPPLIER_PAYMENT", "SUBMITTED_FOR_APPROVAL", "APPROVED",
                "finance.supplier_payment.approved", true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult release(SupplierPaymentCommands.Transition command, String actorPrincipalId) {
        return transitionPayment(command, actorPrincipalId, "RELEASE_SUPPLIER_PAYMENT", "APPROVED", "RELEASED",
                "finance.supplier_payment.released", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult submitForExecution(SupplierPaymentCommands.Transition command, String actorPrincipalId) {
        return transitionPayment(command, actorPrincipalId, "SUBMIT_SUPPLIER_PAYMENT_EXECUTION", "RELEASED",
                "PROCESSING", "finance.supplier_payment.execution_submitted", false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult recordExecution(SupplierPaymentCommands.Execution command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "RECORD_SUPPLIER_PAYMENT_EXECUTION", actorPrincipalId, command, context -> {
            requireRef(command.getPaymentInstructionId(), "paymentInstructionId");
            requireRef(command.getExecutionId(), "executionId"); requireRef(command.getProviderCode(), "providerCode");
            requireRef(command.getProviderReference(), "providerReference");
            requireSha256(command.getExecutionEvidenceSha256(), "executionEvidenceSha256");
            PaymentInstruction payment = requirePayment(context, command.getPaymentInstructionId(),
                    command.getExpectedVersion(), "PROCESSING");
            require(mapper.insertPaymentExecution(command.getExecutionId(), context.tenantId(),
                    payment.getPaymentInstructionId(), upper(command.getProviderCode()), command.getProviderReference(),
                    command.getExecutionEvidenceSha256(), context.now()) == 1, "failed to persist payment execution");
            require(mapper.transitionPayment(context.tenantId(), payment.getPaymentInstructionId(), payment.getVersion(),
                    "PROCESSING", "EXECUTED", context.actor(), context.now()) == 1, "payment execution conflict");
            mapper.insertPaymentHistory(context.tenantId(), payment.getPaymentInstructionId(), "PROCESSING",
                    "EXECUTED", context.actor(), null, payment.getVersion() + 1, context.now());
            return outcome("finance.supplier_payment.executed", "finance_supplier_payment",
                    payment.getPaymentInstructionId(), payment.getVersion() + 1, "EXECUTED",
                    payload("payment_instruction_id", payment.getPaymentInstructionId(),
                            "provider_reference", command.getProviderReference()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult settle(SupplierPaymentCommands.Settlement command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "SETTLE_SUPPLIER_PAYMENT", actorPrincipalId, command, context -> {
            requireRef(command.getPaymentInstructionId(), "paymentInstructionId");
            requireRef(command.getSettlementId(), "settlementId"); require(command.getSettlementDate() != null,
                    "settlementDate is required"); requirePositive(command.getSettledAmountMinor(), "settledAmountMinor");
            requireCurrency(command.getCurrencyCode()); requireRef(command.getBankReference(), "bankReference");
            requireSha256(command.getSettlementEvidenceSha256(), "settlementEvidenceSha256");
            PaymentInstruction payment = requirePayment(context, command.getPaymentInstructionId(),
                    command.getExpectedVersion(), "EXECUTED");
            require(payment.getTotalAmountMinor().equals(command.getSettledAmountMinor()),
                    "bank settlement amount must exactly equal payment instruction total");
            require(payment.getCurrencyCode().equals(upper(command.getCurrencyCode())),
                    "bank settlement currency must equal payment instruction currency");
            requireOpenPeriod(context.tenantId(), payment.getAccountingPeriodId(), command.getSettlementDate());
            Map<String, PostingAccount> accounts = postingAccounts(context.tenantId(), payment.getPostingRuleId(),
                    payment.getPostingRuleVersion(), payment.getLedgerId(), "SUPPLIER_PAYMENT",
                    Set.of("AP", "BANK_CLEARING"));
            List<PaymentAllocation> allocations = mapper.selectPaymentAllocations(context.tenantId(),
                    payment.getPaymentInstructionId());
            require(!allocations.isEmpty(), "payment has no AP allocations");
            long allocated = allocations.stream().mapToLong(PaymentAllocation::getAmountMinor).sum();
            require(allocated == command.getSettledAmountMinor(), "settlement allocations do not balance");
            require(mapper.insertPaymentSettlement(command.getSettlementId(), context.tenantId(),
                    payment.getPaymentInstructionId(), command.getSettlementDate(), command.getSettledAmountMinor(),
                    payment.getCurrencyCode(), command.getBankReference(), command.getSettlementEvidenceSha256(),
                    context.now()) == 1, "failed to persist bank settlement evidence");
            String journalId = UUID.randomUUID().toString(); List<JournalLine> lines = new ArrayList<>(); int lineNo = 1;
            for (PaymentAllocation allocation : allocations) {
                ApOpenItem ap = nonNull(mapper.selectApForUpdate(context.tenantId(), allocation.getApOpenItemId()),
                        "payment AP open item not found");
                require(allocation.getAmountMinor() <= ap.getOpenAmountMinor(), "settlement exceeds AP open amount");
                lines.add(journalLine(context, journalId, lineNo++, accounts.get("AP"),
                        allocation.getAmountMinor(), 0L, payment.getCurrencyCode(), payment.getSupplierId(),
                        null, ap.getApOpenItemId()));
            }
            lines.add(journalLine(context, journalId, lineNo, accounts.get("BANK_CLEARING"), 0L,
                    command.getSettledAmountMinor(), payment.getCurrencyCode(), payment.getSupplierId(), null, null));
            insertPostedJournal(context, journalId, "PAY-" + payment.getPaymentCode(), payment.getLegalEntityId(),
                    payment.getLedgerId(), payment.getAccountingPeriodId(), command.getSettlementDate(),
                    "SUPPLIER_PAYMENT", payment.getPaymentInstructionId(), payment.getCurrencyCode(),
                    command.getSettledAmountMinor(), command.getSettlementEvidenceSha256(), lines,
                    "PAYMENT_SETTLEMENT", command.getJournalDimensions());
            for (PaymentAllocation allocation : allocations) {
                ApOpenItem ap = nonNull(mapper.selectApForUpdate(context.tenantId(), allocation.getApOpenItemId()),
                        "payment AP open item not found");
                require(mapper.insertApSettlementApplication(UUID.randomUUID().toString(), context.tenantId(),
                        ap.getApOpenItemId(), command.getSettlementId(), allocation.getAmountMinor(), journalId,
                        context.now()) == 1, "failed to persist AP application");
                require(mapper.applyApSettlement(context.tenantId(), ap.getApOpenItemId(), ap.getVersion(),
                        allocation.getAmountMinor(), context.now()) == 1, "AP settlement conflict");
                applyInstallments(context, ap.getApOpenItemId(), allocation.getAmountMinor());
                mapper.updateInvoiceSettlementFromAp(context.tenantId(), ap.getApOpenItemId(), context.now());
                mapper.insertApHistory(context.tenantId(), ap.getApOpenItemId(), ap.getStatus(),
                        allocation.getAmountMinor().equals(ap.getOpenAmountMinor()) ? "SETTLED" : "PARTIALLY_SETTLED",
                        allocation.getAmountMinor(), "PAYMENT_SETTLEMENT", command.getSettlementId(),
                        ap.getVersion() + 1, context.now());
            }
            require(mapper.transitionPayment(context.tenantId(), payment.getPaymentInstructionId(), payment.getVersion(),
                    "EXECUTED", "SETTLED", context.actor(), context.now()) == 1, "payment settlement conflict");
            mapper.insertPaymentHistory(context.tenantId(), payment.getPaymentInstructionId(), "EXECUTED",
                    "SETTLED", context.actor(), null, payment.getVersion() + 1, context.now());
            return outcome("finance.supplier_payment.settled", "finance_supplier_payment",
                    payment.getPaymentInstructionId(), payment.getVersion() + 1, "SETTLED",
                    payload("payment_instruction_id", payment.getPaymentInstructionId(),
                            "journal_entry_id", journalId, "settled_amount_minor", command.getSettledAmountMinor()));
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcureToPayResult reverse(JournalCommands.Reverse command, String actorPrincipalId) {
        return execute(command.getEnvelope(), "REVERSE_POSTED_JOURNAL", actorPrincipalId, command, context -> {
            requireRef(command.getOriginalJournalEntryId(), "originalJournalEntryId");
            requireRef(command.getReversalJournalCode(), "reversalJournalCode");
            requireRef(command.getAccountingPeriodId(), "accountingPeriodId");
            require(command.getAccountingDate() != null, "accountingDate is required");
            requireSha256(command.getReversalEvidenceSha256(), "reversalEvidenceSha256");
            JournalEntry original = nonNull(mapper.selectJournalForUpdate(context.tenantId(),
                    command.getOriginalJournalEntryId()), "journal entry not found");
            requireExpectedVersion(command.getExpectedVersion(), original.getVersion());
            require("POSTED".equals(original.getStatus()), "only posted journal entry can be reversed");
            require(!context.actor().equals(original.getPostedByPrincipalId()),
                    "journal reverser must be independent from original poster");
            require(mapper.countJournalReversal(context.tenantId(), original.getJournalEntryId()) == 0,
                    "posted journal entry already has a reversal");
            requireOpenPeriod(context.tenantId(), command.getAccountingPeriodId(), command.getAccountingDate());
            List<JournalLine> originalLines = mapper.selectJournalLines(context.tenantId(), original.getJournalEntryId());
            require(!originalLines.isEmpty(), "journal entry has no immutable lines");
            String reversalId = valueOrUuid(command.getReversalJournalEntryId()); List<JournalLine> reversalLines = new ArrayList<>();
            for (JournalLine line : originalLines) {
                reversalLines.add(new JournalLine().setJournalLineId(UUID.randomUUID().toString())
                        .setTenantId(context.tenantId()).setJournalEntryId(reversalId).setLineNumber(line.getLineNumber())
                        .setLedgerId(line.getLedgerId()).setAccountId(line.getAccountId()).setAccountCode(line.getAccountCode())
                        .setDebitAmountMinor(line.getCreditAmountMinor())
                        .setCreditAmountMinor(line.getDebitAmountMinor())
                        .setTransactionCurrencyCode(line.getTransactionCurrencyCode())
                        .setTransactionAmountMinor(line.getTransactionAmountMinor()).setSupplierId(line.getSupplierId())
                        .setSupplierInvoiceId(line.getSupplierInvoiceId()).setApOpenItemId(line.getApOpenItemId())
                        .setPurchaseOrderId(line.getPurchaseOrderId()).setPurchaseOrderItemId(line.getPurchaseOrderItemId())
                        .setReceiptLineId(line.getReceiptLineId()).setInventoryMovementId(line.getInventoryMovementId())
                        .setCreatedAt(context.now()));
            }
            insertPostedJournal(context, reversalId, command.getReversalJournalCode(), original.getLegalEntityId(),
                    original.getLedgerId(), command.getAccountingPeriodId(), command.getAccountingDate(),
                    "JOURNAL_REVERSAL", original.getJournalEntryId(), original.getCurrencyCode(),
                    original.getDebitTotalMinor(), command.getReversalEvidenceSha256(), reversalLines, "REVERSAL",
                    List.of());
            for (int index = 0; index < originalLines.size(); index++) {
                JournalLine originalLine = originalLines.get(index);
                JournalLine reversalLine = reversalLines.get(index);
                for (JournalDimensionAssignment dimension : mapper.selectJournalLineDimensions(context.tenantId(),
                        originalLine.getJournalLineId())) {
                    require(mapper.insertJournalLineDimension(UUID.randomUUID().toString(), context.tenantId(),
                            reversalLine.getJournalLineId(), dimension.getDimensionTypeId(),
                            dimension.getDimensionValueId(), context.now()) == 1,
                            "failed to copy immutable journal dimension to reversal");
                }
            }
            require(mapper.insertJournalReversalLink(UUID.randomUUID().toString(), context.tenantId(),
                    original.getJournalEntryId(), reversalId, command.getReversalEvidenceSha256(), context.actor(),
                    context.now()) == 1, "failed to persist journal reversal link");
            return outcome("finance.journal_reversal.posted", "finance_journal_entry", reversalId, 1L, "POSTED",
                    payload("journal_entry_id", reversalId, "original_journal_entry_id", original.getJournalEntryId()));
        });
    }

    private void applyInstallments(Context context, String apOpenItemId, long settlementAmount) {
        List<ApInstallment> installments = mapper.selectOpenInstallmentsForUpdate(context.tenantId(), apOpenItemId);
        require(!installments.isEmpty(), "AP installment schedule is missing");
        long remaining = settlementAmount;
        for (ApInstallment installment : installments) {
            if (remaining == 0) break;
            long open = Math.subtractExact(installment.getAmountMinor(), installment.getSettledAmountMinor());
            require(open > 0, "AP installment open amount is invalid");
            long applied = Math.min(open, remaining);
            require(mapper.applyInstallmentSettlement(context.tenantId(), installment.getApInstallmentId(),
                    installment.getVersion(), applied, context.now()) == 1,
                    "AP installment settlement conflict");
            remaining = Math.subtractExact(remaining, applied);
        }
        require(remaining == 0, "AP installment schedule cannot absorb settlement amount");
    }

    private ProcureToPayResult transitionInvoice(SupplierInvoiceCommands.Transition command, String actorPrincipalId,
                                                 String commandType, String from, String to, String eventType,
                                                 boolean makerChecker) {
        return execute(command.getEnvelope(), commandType, actorPrincipalId, command, context -> {
            requireRef(command.getSupplierInvoiceId(), "supplierInvoiceId");
            SupplierInvoice invoice = nonNull(mapper.selectInvoiceForUpdate(context.tenantId(),
                    command.getSupplierInvoiceId()), "supplier invoice not found");
            requireExpectedVersion(command.getExpectedVersion(), invoice.getVersion());
            require(from.equals(invoice.getLifecycleStatus()), "invoice lifecycle transition is invalid");
            if (makerChecker) {
                require(!context.actor().equals(invoice.getCreatedByPrincipalId()),
                        "invoice approver must be independent from invoice creator");
                require("MATCHED".equals(invoice.getMatchStatus()), "invoice approval requires a completed match");
            }
            require(mapper.transitionInvoice(context.tenantId(), invoice.getSupplierInvoiceId(), invoice.getVersion(),
                    from, to, context.actor(), context.now()) == 1, "invoice transition conflict");
            mapper.insertInvoiceHistory(context.tenantId(), invoice.getSupplierInvoiceId(), from, to,
                    invoice.getMatchStatus(), context.actor(), upper(command.getReasonCode()), invoice.getVersion() + 1,
                    context.now());
            return outcome(eventType, "finance_supplier_invoice", invoice.getSupplierInvoiceId(),
                    invoice.getVersion() + 1, to, payload("supplier_invoice_id", invoice.getSupplierInvoiceId()));
        });
    }

    private ProcureToPayResult transitionPayment(SupplierPaymentCommands.Transition command, String actorPrincipalId,
                                                 String commandType, String from, String to, String eventType,
                                                 boolean makerChecker) {
        return execute(command.getEnvelope(), commandType, actorPrincipalId, command, context -> {
            requireRef(command.getPaymentInstructionId(), "paymentInstructionId");
            PaymentInstruction payment = requirePayment(context, command.getPaymentInstructionId(),
                    command.getExpectedVersion(), from);
            if (makerChecker) require(!context.actor().equals(payment.getCreatedByPrincipalId()),
                    "payment approver must be independent from payment creator");
            require(mapper.transitionPayment(context.tenantId(), payment.getPaymentInstructionId(), payment.getVersion(),
                    from, to, context.actor(), context.now()) == 1, "payment transition conflict");
            mapper.insertPaymentHistory(context.tenantId(), payment.getPaymentInstructionId(), from, to,
                    context.actor(), upper(command.getReasonCode()), payment.getVersion() + 1, context.now());
            return outcome(eventType, "finance_supplier_payment", payment.getPaymentInstructionId(),
                    payment.getVersion() + 1, to, payload("payment_instruction_id", payment.getPaymentInstructionId()));
        });
    }

    private PaymentInstruction requirePayment(Context context, String paymentId, Long expectedVersion, String status) {
        PaymentInstruction payment = nonNull(mapper.selectPaymentForUpdate(context.tenantId(), paymentId),
                "supplier payment instruction not found");
        requireExpectedVersion(expectedVersion, payment.getVersion());
        require(status.equals(payment.getStatus()), "supplier payment status transition is invalid");
        return payment;
    }

    private LinePlan planMatchLine(Context context, SupplierInvoice invoice, SupplierInvoiceLine line,
                                   MatchPolicy policy, String runId) {
        PurchaseOrderLineEvidence po = nonNull(mapper.selectLatestPoLineForUpdate(context.tenantId(),
                line.getPurchaseOrderItemId()), "purchase order line evidence not found");
        List<ExceptionPlan> exceptions = new ArrayList<>();
        if (!invoice.getSupplierId().equals(po.getSupplierId()) || !invoice.getLegalEntityId().equals(po.getLegalEntityId()))
            exceptions.add(codeException("PARTY_AUTHORITY_MISMATCH", po.getSupplierId(), invoice.getSupplierId()));
        if (!invoice.getCurrencyCode().equals(po.getCurrencyCode()))
            exceptions.add(codeException("CURRENCY_MISMATCH", po.getCurrencyCode(), invoice.getCurrencyCode()));
        if (!line.getUnitOfMeasure().equals(po.getUnitOfMeasure()))
            exceptions.add(codeException("UOM_MISMATCH", po.getUnitOfMeasure(), line.getUnitOfMeasure()));
        List<MatchCandidate> candidates = mapper.selectMatchCandidates(context.tenantId(), line.getPurchaseOrderItemId());
        BigDecimal eligible = candidates.stream()
                .map(candidate -> candidate.getAcceptedQuantity().subtract(candidate.getPreviouslyAllocatedQuantity()))
                .filter(value -> value.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean quantityShortage = line.getQuantity().subtract(eligible).compareTo(policy.getQuantityTolerance()) > 0;
        if (quantityShortage) exceptions.add(quantityException(line.getQuantity(), eligible));
        long expectedNet = proportional(po.getNetAmountMinor(), line.getQuantity(), po.getOrderedQuantity());
        long expectedTax = proportional(po.getTaxAmountMinor(), line.getQuantity(), po.getOrderedQuantity());
        long priceDifference = Math.subtractExact(line.getNetAmountMinor(), expectedNet);
        long taxDifference = Math.subtractExact(line.getTaxAmountMinor(), expectedTax);
        if (Math.abs(priceDifference) > policy.getPriceToleranceAmountMinor())
            exceptions.add(amountException("PRICE", expectedNet, line.getNetAmountMinor()));
        if (Math.abs(taxDifference) > policy.getTaxToleranceAmountMinor())
            exceptions.add(amountException("TAX", expectedTax, line.getTaxAmountMinor()));
        String matchLineId = UUID.randomUUID().toString();
        MatchLine matchLine = new MatchLine().setMatchLineId(matchLineId).setTenantId(context.tenantId())
                .setMatchRunId(runId).setInvoiceLineId(line.getInvoiceLineId())
                .setPurchaseOrderItemId(line.getPurchaseOrderItemId())
                .setPurchaseOrderLineVersion(po.getSourceVersion()).setInvoiceQuantity(line.getQuantity())
                .setEligibleQuantity(eligible).setInvoiceNetAmountMinor(line.getNetAmountMinor())
                .setExpectedPoNetAmountMinor(expectedNet).setPriceDifferenceAmountMinor(priceDifference)
                .setTaxDifferenceAmountMinor(taxDifference).setCreatedAt(context.now());
        List<MatchAllocation> allocations = new ArrayList<>(); BigDecimal remaining = line.getQuantity();
        if (!quantityShortage) {
            for (MatchCandidate candidate : candidates) {
                BigDecimal available = candidate.getAcceptedQuantity().subtract(candidate.getPreviouslyAllocatedQuantity());
                if (available.signum() <= 0 || remaining.signum() <= 0) continue;
                BigDecimal used = available.min(remaining);
                allocations.add(new MatchAllocation().setMatchAllocationId(UUID.randomUUID().toString())
                        .setTenantId(context.tenantId()).setMatchRunId(runId).setMatchLineId(matchLineId)
                        .setInvoiceLineId(line.getInvoiceLineId()).setReceiptLineId(candidate.getReceiptLineId())
                        .setReceiptLineVersion(candidate.getReceiptLineVersion())
                        .setQualityDispositionId(candidate.getQualityDispositionId())
                        .setQualityDispositionVersion(candidate.getQualityDispositionVersion())
                        .setInventoryMovementId(candidate.getInventoryMovementId())
                        .setInventoryMovementVersion(candidate.getInventoryMovementVersion()).setAllocatedQuantity(used)
                        .setUnitOfMeasure(candidate.getUnitOfMeasure()).setCreatedAt(context.now()));
                remaining = remaining.subtract(used);
            }
        }
        return new LinePlan(matchLine, allocations, exceptions, quantityShortage);
    }

    private List<JournalLine> invoicePostingLines(Context context, SupplierInvoice invoice, String apId,
                                                  String journalId, long receiptValuation,
                                                  Map<String, PostingAccount> accounts) {
        List<JournalLine> lines = new ArrayList<>(); int line = 1;
        lines.add(journalLine(context, journalId, line++, accounts.get("GRIR"), receiptValuation, 0L,
                invoice.getCurrencyCode(), invoice.getSupplierId(), invoice.getSupplierInvoiceId(), apId));
        if (invoice.getTaxAmountMinor() > 0) lines.add(journalLine(context, journalId, line++,
                accounts.get("INPUT_TAX"), invoice.getTaxAmountMinor(), 0L, invoice.getCurrencyCode(),
                invoice.getSupplierId(), invoice.getSupplierInvoiceId(), apId));
        long variance = Math.subtractExact(invoice.getNetAmountMinor(), receiptValuation);
        if (variance > 0) lines.add(journalLine(context, journalId, line++, accounts.get("PURCHASE_PRICE_VARIANCE"), variance, 0L,
                invoice.getCurrencyCode(), invoice.getSupplierId(), invoice.getSupplierInvoiceId(), apId));
        if (variance < 0) lines.add(journalLine(context, journalId, line++, accounts.get("PURCHASE_PRICE_VARIANCE"), 0L,
                Math.negateExact(variance), invoice.getCurrencyCode(), invoice.getSupplierId(),
                invoice.getSupplierInvoiceId(), apId));
        lines.add(journalLine(context, journalId, line, accounts.get("AP"), 0L, invoice.getGrossAmountMinor(),
                invoice.getCurrencyCode(), invoice.getSupplierId(), invoice.getSupplierInvoiceId(), apId));
        long debit = lines.stream().mapToLong(JournalLine::getDebitAmountMinor).sum();
        long credit = lines.stream().mapToLong(JournalLine::getCreditAmountMinor).sum();
        require(debit == credit && debit == invoice.getGrossAmountMinor(), "supplier invoice journal is not balanced");
        return lines;
    }

    private JournalLine journalLine(Context context, String journalId, int lineNumber, PostingAccount account,
                                    long debit, long credit, String currency, String supplierId,
                                    String invoiceId, String apId) {
        require((debit > 0 && credit == 0) || (credit > 0 && debit == 0), "journal line side is invalid");
        require(account != null, "posting rule account is missing");
        return new JournalLine().setJournalLineId(UUID.randomUUID().toString()).setTenantId(context.tenantId())
                .setJournalEntryId(journalId).setLineNumber(lineNumber).setAccountId(account.getAccountId())
                .setLedgerId(account.getLedgerId())
                .setAccountCode(account.getAccountCode())
                .setDebitAmountMinor(debit).setCreditAmountMinor(credit).setTransactionCurrencyCode(currency)
                .setTransactionAmountMinor(Math.max(debit, credit)).setSupplierId(supplierId)
                .setSupplierInvoiceId(invoiceId).setApOpenItemId(apId).setCreatedAt(context.now());
    }

    private Map<String, PostingAccount> postingAccounts(Long tenantId, String ruleId, Long ruleVersion,
                                                        String ledgerId, String sourceType,
                                                        Set<String> requiredRoles) {
        requireRef(ruleId, "postingRuleId");
        require(ruleVersion != null && ruleVersion > 0, "postingRuleVersion must be positive");
        Map<String, PostingAccount> accounts = new HashMap<>();
        for (PostingAccount account : mapper.selectPostingAccounts(tenantId, ruleId, ruleVersion,
                ledgerId, sourceType)) {
            require(accounts.put(account.getAccountRole(), account) == null,
                    "posting rule contains duplicate account role");
        }
        require(accounts.keySet().containsAll(requiredRoles),
                "posting rule is missing required account roles: " + requiredRoles);
        return accounts;
    }

    private void insertPostedJournal(Context context, String journalId, String journalCode, String legalEntityId,
                                     String ledgerId, String periodId, java.time.LocalDate accountingDate,
                                     String sourceType, String sourceId, String currency, long total,
                                     String evidence, List<JournalLine> lines, String effectType,
                                     List<JournalDimensionAssignment> dimensions) {
        long debit = lines.stream().mapToLong(JournalLine::getDebitAmountMinor).sum();
        long credit = lines.stream().mapToLong(JournalLine::getCreditAmountMinor).sum();
        require(total > 0 && debit == total && credit == total, "posted journal lines must balance to source total");
        JournalEntry journal = new JournalEntry().setJournalEntryId(journalId).setTenantId(context.tenantId())
                .setJournalCode(journalCode).setLegalEntityId(legalEntityId).setLedgerId(ledgerId).setPeriodId(periodId)
                .setAccountingDate(accountingDate).setSourceType(sourceType).setSourceId(sourceId)
                .setCurrencyCode(currency).setDocumentCurrencyCode(currency).setDebitTotalMinor(total)
                .setCreditTotalMinor(total).setEvidenceSha256(evidence).setStatus("POSTED")
                .setPreparedByPrincipalId(context.actor()).setPostedByPrincipalId(context.actor()).setVersion(1L)
                .setPreparedAt(context.now()).setPostedAt(context.now()).setCreatedAt(context.now()).setUpdatedAt(context.now());
        require(mapper.insertJournalEntry(journal) == 1, "failed to persist posted journal header");
        List<JournalDimensionAssignment> safeDimensions = dimensions == null ? List.of() : dimensions;
        Set<String> dimensionTypes = new HashSet<>();
        for (JournalDimensionAssignment dimension : safeDimensions) {
            require(dimension != null && dimensionTypes.add(dimension.getDimensionTypeId()),
                    "journal dimension type must be unique");
            require(mapper.countActiveDimensionValue(context.tenantId(), dimension.getDimensionTypeId(),
                    dimension.getDimensionValueId()) == 1, "journal dimension value is not active for type");
        }
        for (JournalLine line : lines) {
            require(mapper.insertJournalLine(line) == 1, "failed to persist immutable journal line");
            for (JournalDimensionAssignment dimension : safeDimensions) {
                require(mapper.insertJournalLineDimension(UUID.randomUUID().toString(), context.tenantId(),
                        line.getJournalLineId(), dimension.getDimensionTypeId(), dimension.getDimensionValueId(),
                        context.now()) == 1, "failed to persist journal dimension");
            }
        }
        require(mapper.insertJournalSourceEffect(UUID.randomUUID().toString(), context.tenantId(), sourceType,
                sourceId, effectType, journalId, context.now()) == 1, "failed to persist journal source effect");
        mapper.insertJournalHistory(context.tenantId(), journalId, null, "POSTED", context.actor(),
                null, 1L, context.now());
    }

    private InboxResolution resolveInbox(Context context, String eventId, String eventType, String aggregateId,
                                         Long aggregateVersion, String evidence, java.time.Instant occurredAt) {
        String attemptToken = UUID.randomUUID().toString();
        LocalDateTime sourceTime = sourceTime(occurredAt);
        EventInbox inbox = new EventInbox().setTenantId(context.tenantId()).setSourceEventId(eventId)
                .setSourceEventType(eventType).setSourceSchemaVersion(1).setSourceAggregateId(aggregateId)
                .setSourceAggregateVersion(aggregateVersion).setEvidenceSha256(evidence)
                .setAttemptToken(attemptToken).setSourceOccurredAt(sourceTime).setReceivedAt(context.now());
        require(mapper.insertInbox(inbox) >= 0 && inbox.getInboxId() != null,
                "failed to resolve finance event inbox");
        EventInbox resolved = nonNull(mapper.selectInboxForUpdate(context.tenantId(), inbox.getInboxId()),
                "finance event inbox disappeared");
        boolean duplicate = !attemptToken.equals(resolved.getAttemptToken());
        require(Objects.equals(eventId, resolved.getSourceEventId())
                        && Objects.equals(eventType, resolved.getSourceEventType())
                        && Objects.equals(1, resolved.getSourceSchemaVersion())
                        && Objects.equals(aggregateId, resolved.getSourceAggregateId())
                        && Objects.equals(aggregateVersion, resolved.getSourceAggregateVersion())
                        && Objects.equals(evidence, resolved.getEvidenceSha256())
                        && Objects.equals(sourceTime, resolved.getSourceOccurredAt()),
                "sourceEventId conflicts with different immutable evidence payload");
        return new InboxResolution(resolved, duplicate);
    }

    private static LocalDateTime sourceTime(java.time.Instant value) {
        LocalDateTime result = LocalDateTime.ofInstant(value, ZoneOffset.UTC);
        return result.withNano(result.getNano() / 1_000 * 1_000);
    }

    private static void requireMonotonic(Long version, Long currentMax, String aggregateName) {
        require(currentMax == null || version > currentMax,
                aggregateName + " sourceVersion must be strictly monotonic");
    }

    private AccountingPeriod requireOpenPeriod(Long tenantId, String periodId, java.time.LocalDate accountingDate) {
        AccountingPeriod period = nonNull(mapper.selectPeriodForUpdate(tenantId, periodId),
                "accounting period not found");
        require("OPEN".equals(period.getStatus()), "accounting period is not open");
        require(!accountingDate.isBefore(period.getPeriodStart()) && !accountingDate.isAfter(period.getPeriodEnd()),
                "accounting date is outside accounting period");
        return period;
    }

    private ProcureToPayResult execute(FinanceCommandEnvelope envelope, String commandType,
                                       String actorPrincipalId, Object command, Function<Context, Outcome> action) {
        validateEnvelope(envelope); requireRef(actorPrincipalId, "actorPrincipalId");
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(envelope.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, envelope.getIdempotencyKey(), commandType,
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve finance operation");
        Operation operation = nonNull(mapper.selectOperationForUpdate(operationId, tenantId),
                "finance operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different finance payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing finance operation is incomplete");
            ProcureToPayResult replay = JsonUtils.parseObject(operation.getResultJson(), ProcureToPayResult.class);
            replay.setDuplicate(true); return replay;
        }
        Outcome outcome = action.apply(new Context(tenantId, actorPrincipalId, now));
        if (!outcome.duplicate()) appendEvent(tenantId, envelope, outcome);
        ProcureToPayResult result = ProcureToPayResult.builder().operationId(operationId).duplicate(outcome.duplicate())
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).status(outcome.status())
                .supplierInvoiceId(text(outcome.payload().get("supplier_invoice_id")))
                .matchRunId(text(outcome.payload().get("match_run_id")))
                .exceptionId(text(outcome.payload().get("exception_id")))
                .apOpenItemId(text(outcome.payload().get("ap_open_item_id")))
                .paymentInstructionId(text(outcome.payload().get("payment_instruction_id")))
                .journalEntryId(text(outcome.payload().get("journal_entry_id"))).build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "finance operation completion conflict");
        return result;
    }

    private void appendEvent(Long tenantId, FinanceCommandEnvelope envelope, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType()).schemaVersion(1).sourceSystem(SOURCE_SYSTEM).tenantId(tenantId)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).eventSequence((short) 1).occurredAt(envelope.getOccurredAt())
                .traceId(envelope.getRunId()).correlationId(envelope.getCorrelationId())
                .causationId(envelope.getCausationId()).idempotencyKey(envelope.getIdempotencyKey() + ":" + outcome.version())
                .payload(outcome.payload()).headers(payload("run_id", envelope.getRunId(), "status", outcome.status()))
                .destination("lakehouse").build());
    }

    private static void validateEnvelope(FinanceCommandEnvelope envelope) {
        require(envelope != null, "finance command envelope is required");
        requireUuid(envelope.getCorrelationId(), "correlationId");
        if (envelope.getCausationId() != null) requireUuid(envelope.getCausationId(), "causationId");
        requireRef(envelope.getIdempotencyKey(), "idempotencyKey");
        require(envelope.getOccurredAt() != null, "occurredAt is required");
        if (envelope.getRunId() != null) requireRef(envelope.getRunId(), "runId");
    }

    private static void validateSourceEvidence(String eventId, Long version, String hash, java.time.Instant occurredAt) {
        requireRef(eventId, "sourceEventId"); require(version != null && version > 0, "sourceVersion must be positive");
        requireSha256(hash, "evidenceSha256"); require(occurredAt != null, "sourceOccurredAt is required");
    }

    private static void validateInvoiceLine(SupplierInvoiceCommands.Line line) {
        require(line != null, "invoice line is required"); require(line.getLineNumber() != null && line.getLineNumber() > 0,
                "lineNumber must be positive"); requireRef(line.getPurchaseOrderId(), "purchaseOrderId");
        requireRef(line.getPurchaseOrderItemId(), "purchaseOrderItemId"); requireRef(line.getSkuId(), "skuId");
        requirePositive(line.getQuantity(), "quantity"); requireUom(line.getUnitOfMeasure());
        requirePositive(line.getUnitNetPrice(), "unitNetPrice"); requireRef(line.getTaxCode(), "taxCode");
        requireNonNegative(line.getTaxRate(), "taxRate");
        requireAmounts(line.getNetAmountMinor(), line.getTaxAmountMinor(), line.getGrossAmountMinor());
    }

    private static long proportional(long total, BigDecimal part, BigDecimal whole) {
        return BigDecimal.valueOf(total).multiply(part).divide(whole, 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static void requireAmounts(Long net, Long tax, Long gross) {
        require(net != null && net >= 0 && tax != null && tax >= 0 && gross != null && gross > 0,
                "money amounts are invalid"); require(Math.addExact(net, tax) == gross, "gross must equal net plus tax");
    }

    private static void requireCurrency(String value) { require(value != null && value.matches("[A-Za-z]{3}"),
            "currencyCode must be ISO alpha-3"); }
    private static void requireUom(String value) { require(value != null && value.matches("[A-Za-z][A-Za-z0-9_]{0,31}"),
            "unitOfMeasure is invalid"); }
    private static void requireRef(String value, String field) { require(value != null && value.length() <= 256
            && SAFE_REF.matcher(value).matches(), field + " is invalid"); }
    private static void requireSha256(String value, String field) { require(value != null && SHA256.matcher(value).matches(),
            field + " must be lowercase SHA-256"); }
    private static void requirePositive(Long value, String field) { require(value != null && value > 0,
            field + " must be positive"); }
    private static void requireNonNegative(Long value, String field) { require(value != null && value >= 0,
            field + " must not be negative"); }
    private static void requirePositive(BigDecimal value, String field) { require(value != null && value.signum() > 0,
            field + " must be positive"); }
    private static void requireNonNegative(BigDecimal value, String field) { require(value != null && value.signum() >= 0,
            field + " must not be negative"); }
    private static void requireExpectedVersion(Long expected, Long actual) { require(expected != null
            && Objects.equals(expected, actual), "expectedVersion does not match current version"); }
    private static void requireUuid(String value, String field) { try { UUID.fromString(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(field + " must be a UUID", exception); } }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static String valueOrUuid(String value) { return value == null || value.isBlank()
            ? UUID.randomUUID().toString() : value; }
    private static String text(Object value) { return value == null ? null : value.toString(); }
    private static <T> T nonNull(T value, String message) { if (value == null) throw new IllegalStateException(message); return value; }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static Map<String, Object> payload(Object... values) { Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) if (values[i + 1] != null) result.put(values[i].toString(), values[i + 1]);
        return result; }
    private static Outcome outcome(String eventType, String aggregateType, String aggregateId, Long version,
                                   String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, status, payload, false); }
    private static Outcome duplicateOutcome(String aggregateType, String aggregateId, Long version,
                                            Map<String, Object> payload) {
        return new Outcome(null, aggregateType, aggregateId, version, "RECORDED", payload, true); }

    private record Context(Long tenantId, String actor, LocalDateTime now) { }
    private record Outcome(String eventType, String aggregateType, String aggregateId, Long version,
                           String status, Map<String, Object> payload, boolean duplicate) { }
    private record InboxResolution(EventInbox inbox, boolean duplicate) { }
    private static ExceptionPlan codeException(String code, String expected, String actual) {
        return new ExceptionPlan(code, "MISSING_EVIDENCE", null, null, null, null, expected, actual);
    }
    private static ExceptionPlan quantityException(BigDecimal expected, BigDecimal actual) {
        return new ExceptionPlan("QUANTITY_SHORTAGE", "QUANTITY", expected, actual, null, null, null, null);
    }
    private static ExceptionPlan amountException(String type, Long expected, Long actual) {
        return new ExceptionPlan(type + "_VARIANCE", type, null, null, expected, actual, null, null);
    }
    private record ExceptionPlan(String code, String type, BigDecimal expectedQuantity, BigDecimal actualQuantity,
                                 Long expectedAmountMinor, Long actualAmountMinor,
                                 String expectedCode, String actualCode) { }
    private record LinePlan(MatchLine line, List<MatchAllocation> allocations,
                            List<ExceptionPlan> exceptions, boolean hasQuantityShortage) { }
}
