package cn.iocoder.yudao.module.cloudmold.crossborder.service.bonded;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.bonded.*;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.EligibilityAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.bonded.BondedCustomsRecords.StatusHistoryRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.bonded.BondedCustomsCaseMapper;
import cn.iocoder.yudao.module.cloudmold.crossborder.service.actor.CrossBorderActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BondedCustomsServiceImpl implements BondedCustomsCommandApi {
    static final int OPERATION_SUCCEEDED = 10;
    static final String AGGREGATE_TYPE = "bonded_customs_case";
    static final String SOURCE_SYSTEM = "cloudmold-crossborder-bonded-customs";
    static final String MODE = "BONDED_RETAIL_IMPORT";
    private static final String TRIPLE_MATCHED = "TRIPLE_MATCHED";
    private static final String CUSTOMS_ACCEPTED = "CUSTOMS_ACCEPTED";
    private static final String BONDED_RELEASED = "BONDED_RELEASED";
    private static final String DELIVERED = "DELIVERED";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9_:-]{0,63}");
    private static final Pattern SAFE_TEXT =
            Pattern.compile("[\\p{L}\\p{N} _.,:/()#%=+\\-，。；：、（）]{1,255}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{32,64}");
    private static final Pattern HS_CODE = Pattern.compile("[0-9]{6,12}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Set<String> POSITIVE_LIST_CODES = Set.of("POSITIVE_LIST_A", "POSITIVE_LIST_B",
            "POSITIVE_LIST_TEST");

    private final BondedCustomsCaseMapper mapper;
    private final OutboxAppender outboxAppender;
    private final CrossBorderActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BondedCustomsResult execute(BondedCustomsCommand command, String actorPrincipalId) {
        validateEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve bonded customs operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "bonded customs operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different bonded customs payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing bonded customs operation is incomplete");
            BondedCustomsResult replay = JsonUtils.parseObject(operation.getResultJson(), BondedCustomsResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_CASE -> createCase(tenantId, command, actorPrincipalId, now);
            case ASSESS_ELIGIBILITY -> assessEligibility(tenantId, command, actorPrincipalId, now);
            case CLASSIFY_GOODS -> classifyGoods(tenantId, command, actorPrincipalId, now);
            case MATCH_TRIPLE_ORDERS -> matchTripleOrders(tenantId, command, actorPrincipalId, now);
            case CALCULATE_TAX -> calculateTax(tenantId, command, actorPrincipalId, now);
            case APPROVE_DECLARATION -> approveDeclaration(tenantId, command, actorPrincipalId, now);
            case SUBMIT_DECLARATION -> submitDeclaration(tenantId, command, actorPrincipalId, now);
            case ACCEPT_CUSTOMS -> acceptCustoms(tenantId, command, actorPrincipalId, now);
            case RELEASE_BONDED_STOCK -> releaseBondedStock(tenantId, command, actorPrincipalId, now);
            case CONFIRM_DELIVERY -> confirmDelivery(tenantId, command, actorPrincipalId, now);
            case CLOSE_CASE -> closeCase(tenantId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        BondedCustomsResult result = BondedCustomsResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(outcome.caseRecord().getCaseId())
                .aggregateVersion(outcome.caseRecord().getVersion())
                .status(outcome.caseRecord().getStatus())
                .tripleMatchStatus(outcome.caseRecord().getTripleMatchStatus())
                .customsStatus(outcome.caseRecord().getCustomsStatus())
                .bondedReleaseStatus(outcome.caseRecord().getBondedReleaseStatus())
                .deliveryStatus(outcome.caseRecord().getDeliveryStatus())
                .caseId(outcome.caseRecord().getCaseId())
                .caseNo(outcome.caseRecord().getCaseNo())
                .approvalRef(outcome.caseRecord().getApprovalRef())
                .declarationRef(outcome.caseRecord().getDeclarationRef())
                .customsAcceptanceRef(outcome.caseRecord().getCustomsAcceptanceRef())
                .bondedReleaseRef(outcome.caseRecord().getBondedReleaseRef())
                .deliveryConfirmationRef(outcome.caseRecord().getDeliveryConfirmationRef())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, AGGREGATE_TYPE, outcome.caseRecord().getCaseId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "bonded customs operation completion conflict");
        return result;
    }

    private Outcome createCase(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                               LocalDateTime now) {
        BondedCustomsCommand.TripleOrder tripleOrder = command.getTripleOrder();
        String caseId = valueOrUuid(command.getCaseId());
        CaseRecord row = new CaseRecord()
                .setCaseId(caseId)
                .setTenantId(tenantId)
                .setCaseNo(caseNo(caseId))
                .setMode(MODE)
                .setCanonicalOrderId(command.getCanonicalOrderId())
                .setStatus("DRAFT")
                .setTripleMatchStatus("PENDING")
                .setCustomsStatus("PENDING")
                .setBondedReleaseStatus("PENDING")
                .setDeliveryStatus("PENDING")
                .setOrderRef(tripleOrder.getOrderRef())
                .setPaymentRef(tripleOrder.getPaymentRef())
                .setLogisticsRef(tripleOrder.getLogisticsRef())
                .setCreatedByPrincipalId(actorPrincipalId)
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertCase(row) == 1, "failed to persist bonded customs case");
        insertHistory(tenantId, row, command.getOperation().name(), null, "DRAFT",
                actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("bonded_customs.case.created", row, "PENDING", Map.of("canonical_order_id", row.getCanonicalOrderId()));
    }

    private Outcome assessEligibility(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                      LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DRAFT");
        BondedCustomsCommand.EligibilityAssessment input = command.getEligibilityAssessment();
        String assessmentId = UUID.randomUUID().toString();
        EligibilityAssessmentRecord record = new EligibilityAssessmentRecord()
                .setAssessmentId(assessmentId)
                .setTenantId(tenantId)
                .setCaseId(row.getCaseId())
                .setFactsJson(jsonList(input.getFacts()))
                .setOptionsJson(jsonList(input.getOptions()))
                .setRecommendation(input.getRecommendation())
                .setRisksJson(jsonList(input.getRisks()))
                .setConfidence(scaleConfidence(input.getConfidence()))
                .setMissingFactsJson(jsonList(input.getMissingFacts()))
                .setEvidenceRef(input.getEvidenceRef())
                .setAssessedByPrincipalId(actorPrincipalId)
                .setAssessedAt(now)
                .setCreatedAt(now);
        require(mapper.insertAssessment(record) == 1, "failed to persist bonded customs eligibility assessment");
        String previous = row.getStatus();
        row.setAssessmentId(assessmentId).setStatus("ELIGIBILITY_ASSESSED").setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.eligibility.assessed", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome classifyGoods(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                  LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "ELIGIBILITY_ASSESSED");
        BondedCustomsCommand.GoodsClassification input = command.getGoodsClassification();
        String previous = row.getStatus();
        row.setHsCode(input.getHsCode())
                .setPositiveListCode(input.getPositiveListCode())
                .setGoodsName(input.getGoodsName())
                .setGoodsEvidenceRef(input.getEvidenceRef())
                .setStatus("GOODS_CLASSIFIED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.goods.classified", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome matchTripleOrders(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                      LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "GOODS_CLASSIFIED");
        BondedCustomsCommand.TripleOrder input = command.getTripleOrder();
        require(Objects.equals(input.getOrderAmountMinor(), input.getPaymentAmountMinor())
                        && Objects.equals(input.getOrderAmountMinor(), input.getLogisticsAmountMinor()),
                "triple order amounts must match exactly");
        require(Objects.equals(input.getBuyerIdentityHash(), input.getReceiverIdentityHash())
                        && Objects.equals(input.getBuyerIdentityHash(), input.getDeclarantIdentityHash()),
                "triple order identity hashes must match exactly");
        String previous = row.getStatus();
        row.setOrderRef(input.getOrderRef())
                .setPaymentRef(input.getPaymentRef())
                .setLogisticsRef(input.getLogisticsRef())
                .setOrderAmountMinor(input.getOrderAmountMinor())
                .setPaymentAmountMinor(input.getPaymentAmountMinor())
                .setLogisticsAmountMinor(input.getLogisticsAmountMinor())
                .setCurrency(input.getCurrency())
                .setBuyerIdentityHash(input.getBuyerIdentityHash())
                .setReceiverIdentityHash(input.getReceiverIdentityHash())
                .setDeclarantIdentityHash(input.getDeclarantIdentityHash())
                .setOrderSnapshotRef(input.getOrderSnapshotRef())
                .setPaymentSnapshotRef(input.getPaymentSnapshotRef())
                .setLogisticsSnapshotRef(input.getLogisticsSnapshotRef())
                .setTripleMatchStatus(TRIPLE_MATCHED)
                .setStatus("TRIPLE_MATCHED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.triple_orders.matched", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome calculateTax(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                 LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "TRIPLE_MATCHED");
        BondedCustomsCommand.TaxCalculation input = command.getTaxCalculation();
        require(input.getTotalTaxMinor().equals(input.getConsumptionTaxMinor() + input.getValueAddedTaxMinor()),
                "totalTaxMinor must equal consumptionTaxMinor plus valueAddedTaxMinor");
        String previous = row.getStatus();
        row.setDutiableAmountMinor(input.getDutiableAmountMinor())
                .setConsumptionTaxMinor(input.getConsumptionTaxMinor())
                .setValueAddedTaxMinor(input.getValueAddedTaxMinor())
                .setTotalTaxMinor(input.getTotalTaxMinor())
                .setTaxCurrency(input.getCurrency())
                .setTaxEvidenceRef(input.getEvidenceRef())
                .setStatus("TAX_CALCULATED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.tax.calculated", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome approveDeclaration(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                       LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "TAX_CALCULATED");
        String previous = row.getStatus();
        row.setApprovalRef(command.getApprovalRef())
                .setStatus("DECLARATION_APPROVED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.declaration.approved", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome submitDeclaration(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                      LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DECLARATION_APPROVED");
        require(Objects.equals(row.getApprovalRef(), command.getApprovalRef()),
                "approvalRef does not match the approved declaration");
        String previous = row.getStatus();
        row.setDeclarationRef(command.getDeclarationRef())
                .setCustomsStatus("DECLARATION_SUBMITTED")
                .setStatus("DECLARATION_SUBMITTED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.declaration.submitted", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome acceptCustoms(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                  LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DECLARATION_SUBMITTED");
        String previous = row.getStatus();
        row.setCustomsAcceptanceRef(command.getCustomsAcceptanceRef())
                .setCustomsStatus(CUSTOMS_ACCEPTED)
                .setStatus("CUSTOMS_ACCEPTED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.customs.accepted", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome releaseBondedStock(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                       LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "CUSTOMS_ACCEPTED");
        String previous = row.getStatus();
        row.setBondedReleaseRef(command.getBondedReleaseRef())
                .setBondedReleaseStatus(BONDED_RELEASED)
                .setStatus("BONDED_RELEASED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.bonded_stock.released", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome confirmDelivery(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                                    LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "BONDED_RELEASED");
        String previous = row.getStatus();
        row.setDeliveryConfirmationRef(command.getDeliveryConfirmationRef())
                .setDeliveryStatus(DELIVERED)
                .setStatus("DELIVERED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.delivery.confirmed", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private Outcome closeCase(Long tenantId, BondedCustomsCommand command, String actorPrincipalId,
                              LocalDateTime now) {
        CaseRecord row = requireCase(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DELIVERED");
        String previous = row.getStatus();
        row.setCloseReason(command.getCloseReason())
                .setStatus("CLOSED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now)
                .setClosedAt(now);
        persistTransition(row, tenantId, command, previous, actorPrincipalId, detail(command));
        return outcome("bonded_customs.case.closed", row, row.getTripleMatchStatus(), detailMap(command));
    }

    private void persistTransition(CaseRecord row, Long tenantId, BondedCustomsCommand command, String previousStatus,
                                   String actorPrincipalId, String detailJson) {
        require(mapper.updateCase(row, tenantId, row.getCaseId(), command.getExpectedVersion(), previousStatus) == 1,
                "bonded customs case transition conflict");
        insertHistory(tenantId, row, command.getOperation().name(), previousStatus, row.getStatus(),
                actorPrincipalId, command.getOccurredAt(), detailJson);
    }

    private void insertHistory(Long tenantId, CaseRecord row, String operation, String previousStatus,
                               String currentStatus, String actorPrincipalId, Instant occurredAt, String detailJson) {
        require(mapper.insertHistory(new StatusHistoryRecord()
                        .setTenantId(tenantId)
                        .setCaseId(row.getCaseId())
                        .setAggregateVersion(row.getVersion())
                        .setOperation(operation)
                        .setPreviousStatus(previousStatus)
                        .setCurrentStatus(currentStatus)
                        .setActorPrincipalId(actorPrincipalId)
                        .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .setDetailJson(detailJson)
                        .setCreatedAt(row.getUpdatedAt())) == 1,
                "failed to persist bonded customs status history");
    }

    private void appendEvent(Long tenantId, BondedCustomsCommand command, Outcome outcome) {
        CaseRecord row = outcome.caseRecord();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("case_id", row.getCaseId());
        payload.put("case_no", row.getCaseNo());
        payload.put("mode", row.getMode());
        payload.put("canonical_order_id", row.getCanonicalOrderId());
        payload.put("previous_status", outcome.previousStatus());
        payload.put("current_status", row.getStatus());
        payload.put("triple_match_status", row.getTripleMatchStatus());
        payload.put("customs_status", row.getCustomsStatus());
        payload.put("bonded_release_status", row.getBondedReleaseStatus());
        payload.put("delivery_status", row.getDeliveryStatus());
        payload.put("approval_ref", row.getApprovalRef());
        payload.put("declaration_ref", row.getDeclarationRef());
        payload.put("customs_acceptance_ref", row.getCustomsAcceptanceRef());
        payload.put("bonded_release_ref", row.getBondedReleaseRef());
        payload.put("delivery_confirmation_ref", row.getDeliveryConfirmationRef());
        payload.put("hs_code", row.getHsCode());
        payload.put("positive_list_code", row.getPositiveListCode());
        payload.put("goods_name", row.getGoodsName());
        payload.put("total_tax_minor", row.getTotalTaxMinor());
        payload.put("tax_currency", row.getTaxCurrency());
        payload.put("detail", outcome.detail());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(outcome.eventType())
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(row.getCaseId())
                .aggregateVersion(row.getVersion())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .idempotencyKey(command.getIdempotencyKey())
                .payload(payload)
                .headers(Map.of("mode", MODE))
                .destination("lakehouse")
                .maxAttempts(25)
                .build());
    }

    private CaseRecord requireCase(Long tenantId, String caseId, Long expectedVersion) {
        CaseRecord row = mapper.selectCaseForUpdate(tenantId, caseId);
        require(row != null, "bonded customs case not found");
        require(Objects.equals(row.getVersion(), expectedVersion), "bonded customs case version conflict");
        return row;
    }

    private void requireStatus(CaseRecord row, String expectedStatus) {
        require(expectedStatus.equals(row.getStatus()), "bonded customs case must be in " + expectedStatus + " status");
    }

    private void validateEnvelope(BondedCustomsCommand command) {
        require(command != null, "command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        require(!command.getOccurredAt().isAfter(Instant.now().plusSeconds(300)),
                "occurredAt cannot be materially in the future");
        if (command.getCaseId() != null && !command.getCaseId().isBlank()) {
            requireRef(command.getCaseId(), "caseId", 128);
        }
        if (command.getOperation() != BondedCustomsOperation.CREATE_CASE) {
            requireRef(command.getCaseId(), "caseId", 128);
            require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                    "expectedVersion must be positive");
        }
        switch (command.getOperation()) {
            case CREATE_CASE -> validateCreate(command);
            case ASSESS_ELIGIBILITY -> validateAssessment(command.getEligibilityAssessment());
            case CLASSIFY_GOODS -> validateClassification(command.getGoodsClassification());
            case MATCH_TRIPLE_ORDERS -> validateTripleOrder(command.getTripleOrder(), true);
            case CALCULATE_TAX -> validateTax(command.getTaxCalculation());
            case APPROVE_DECLARATION -> requireRef(command.getApprovalRef(), "approvalRef", 128);
            case SUBMIT_DECLARATION -> {
                requireRef(command.getApprovalRef(), "approvalRef", 128);
                requireRef(command.getDeclarationRef(), "declarationRef", 128);
            }
            case ACCEPT_CUSTOMS -> requireRef(command.getCustomsAcceptanceRef(), "customsAcceptanceRef", 128);
            case RELEASE_BONDED_STOCK -> requireRef(command.getBondedReleaseRef(), "bondedReleaseRef", 128);
            case CONFIRM_DELIVERY -> requireRef(command.getDeliveryConfirmationRef(), "deliveryConfirmationRef", 128);
            case CLOSE_CASE -> requireText(command.getCloseReason(), "closeReason", 255);
        }
    }

    private void validateCreate(BondedCustomsCommand command) {
        requireRef(command.getCanonicalOrderId(), "canonicalOrderId", 128);
        validateTripleOrder(command.getTripleOrder(), false);
    }

    private void validateTripleOrder(BondedCustomsCommand.TripleOrder tripleOrder, boolean requireAmounts) {
        require(tripleOrder != null, "tripleOrder is required");
        requireRef(tripleOrder.getOrderRef(), "tripleOrder.orderRef", 128);
        requireRef(tripleOrder.getPaymentRef(), "tripleOrder.paymentRef", 128);
        requireRef(tripleOrder.getLogisticsRef(), "tripleOrder.logisticsRef", 128);
        if (requireAmounts) {
            require(tripleOrder.getOrderAmountMinor() != null && tripleOrder.getOrderAmountMinor() > 0,
                    "tripleOrder.orderAmountMinor must be positive");
            require(tripleOrder.getPaymentAmountMinor() != null && tripleOrder.getPaymentAmountMinor() > 0,
                    "tripleOrder.paymentAmountMinor must be positive");
            require(tripleOrder.getLogisticsAmountMinor() != null && tripleOrder.getLogisticsAmountMinor() > 0,
                    "tripleOrder.logisticsAmountMinor must be positive");
            require(CURRENCY.matcher(Objects.toString(tripleOrder.getCurrency(), "")).matches(),
                    "tripleOrder.currency must be a 3-letter code");
            requireHash(tripleOrder.getBuyerIdentityHash(), "tripleOrder.buyerIdentityHash");
            requireHash(tripleOrder.getReceiverIdentityHash(), "tripleOrder.receiverIdentityHash");
            requireHash(tripleOrder.getDeclarantIdentityHash(), "tripleOrder.declarantIdentityHash");
            requireRef(tripleOrder.getOrderSnapshotRef(), "tripleOrder.orderSnapshotRef", 255);
            requireRef(tripleOrder.getPaymentSnapshotRef(), "tripleOrder.paymentSnapshotRef", 255);
            requireRef(tripleOrder.getLogisticsSnapshotRef(), "tripleOrder.logisticsSnapshotRef", 255);
        }
    }

    private void validateAssessment(BondedCustomsCommand.EligibilityAssessment assessment) {
        require(assessment != null, "eligibilityAssessment is required");
        validateLines(assessment.getFacts(), "eligibilityAssessment.facts", 1, 20);
        validateLines(assessment.getOptions(), "eligibilityAssessment.options", 1, 10);
        requireText(assessment.getRecommendation(), "eligibilityAssessment.recommendation", 255);
        require(assessment.getOptions().contains(assessment.getRecommendation()),
                "eligibilityAssessment.recommendation must be one of eligibilityAssessment.options");
        validateLines(assessment.getRisks(), "eligibilityAssessment.risks", 0, 10);
        require(assessment.getConfidence() != null
                        && assessment.getConfidence().compareTo(BigDecimal.ZERO) >= 0
                        && assessment.getConfidence().compareTo(BigDecimal.ONE) <= 0,
                "eligibilityAssessment.confidence must be between 0 and 1");
        validateLines(assessment.getMissingFacts(), "eligibilityAssessment.missingFacts", 0, 20);
        requireRef(assessment.getEvidenceRef(), "eligibilityAssessment.evidenceRef", 255);
    }

    private void validateClassification(BondedCustomsCommand.GoodsClassification goodsClassification) {
        require(goodsClassification != null, "goodsClassification is required");
        require(HS_CODE.matcher(Objects.toString(goodsClassification.getHsCode(), "")).matches(),
                "goodsClassification.hsCode must be 6 to 12 digits");
        require(POSITIVE_LIST_CODES.contains(goodsClassification.getPositiveListCode()),
                "goodsClassification.positiveListCode is unsupported");
        requireText(goodsClassification.getGoodsName(), "goodsClassification.goodsName", 255);
        requireRef(goodsClassification.getEvidenceRef(), "goodsClassification.evidenceRef", 255);
    }

    private void validateTax(BondedCustomsCommand.TaxCalculation taxCalculation) {
        require(taxCalculation != null, "taxCalculation is required");
        require(taxCalculation.getDutiableAmountMinor() != null && taxCalculation.getDutiableAmountMinor() > 0,
                "taxCalculation.dutiableAmountMinor must be positive");
        require(taxCalculation.getConsumptionTaxMinor() != null && taxCalculation.getConsumptionTaxMinor() >= 0,
                "taxCalculation.consumptionTaxMinor must not be negative");
        require(taxCalculation.getValueAddedTaxMinor() != null && taxCalculation.getValueAddedTaxMinor() >= 0,
                "taxCalculation.valueAddedTaxMinor must not be negative");
        require(taxCalculation.getTotalTaxMinor() != null && taxCalculation.getTotalTaxMinor() >= 0,
                "taxCalculation.totalTaxMinor must not be negative");
        require(CURRENCY.matcher(Objects.toString(taxCalculation.getCurrency(), "")).matches(),
                "taxCalculation.currency must be a 3-letter code");
        requireRef(taxCalculation.getEvidenceRef(), "taxCalculation.evidenceRef", 255);
    }

    private void validateLines(List<String> values, String field, int min, int max) {
        require(values != null && values.size() >= min && values.size() <= max,
                field + " must contain between " + min + " and " + max + " entries");
        for (String value : values) {
            requireText(value, field, 255);
        }
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static String caseNo(String caseId) {
        String cleaned = caseId.replace("-", "").toUpperCase(Locale.ROOT);
        if (cleaned.length() >= 20) {
            return "CMBC" + cleaned.substring(0, 20);
        }
        return "CMBC" + (cleaned + DigestUtil.md5Hex(caseId).toUpperCase(Locale.ROOT)).substring(0, 20);
    }

    private static BigDecimal scaleConfidence(BigDecimal confidence) {
        return confidence.setScale(4, RoundingMode.HALF_UP);
    }

    private static String jsonList(List<String> values) {
        return JsonUtils.toJsonString(values == null ? List.of() : values);
    }

    private static String detail(BondedCustomsCommand command) {
        return JsonUtils.toJsonString(detailMap(command));
    }

    private static Outcome outcome(String eventType, CaseRecord row, String previousStatus, Map<String, ?> detail) {
        return new Outcome(eventType, row, previousStatus, new LinkedHashMap<>(detail));
    }

    private static Map<String, Object> detailMap(BondedCustomsCommand command) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", command.getOperation().name());
        detail.put("canonicalOrderId", command.getCanonicalOrderId());
        detail.put("tripleOrder", command.getTripleOrder());
        detail.put("eligibilityAssessment", command.getEligibilityAssessment());
        detail.put("goodsClassification", command.getGoodsClassification());
        detail.put("taxCalculation", command.getTaxCalculation());
        detail.put("approvalRef", command.getApprovalRef());
        detail.put("declarationRef", command.getDeclarationRef());
        detail.put("customsAcceptanceRef", command.getCustomsAcceptanceRef());
        detail.put("bondedReleaseRef", command.getBondedReleaseRef());
        detail.put("deliveryConfirmationRef", command.getDeliveryConfirmationRef());
        detail.put("closeReason", command.getCloseReason());
        return detail;
    }

    private static void requireUuid(String value, String field) {
        try {
            require(value != null, field + " is required");
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static void requireHash(String value, String field) {
        require(HASH.matcher(Objects.toString(value, "")).matches(), field + " must be a lowercase hex hash");
    }

    private static void requireCode(String value, String field) {
        require(SAFE_CODE.matcher(Objects.toString(value, "")).matches(), field + " must be an uppercase code");
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength
                        && SAFE_TEXT.matcher(value).matches(),
                field + " is invalid");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " is invalid");
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    private record Outcome(String eventType, CaseRecord caseRecord, String previousStatus,
                           Map<String, Object> detail) {
    }
}
