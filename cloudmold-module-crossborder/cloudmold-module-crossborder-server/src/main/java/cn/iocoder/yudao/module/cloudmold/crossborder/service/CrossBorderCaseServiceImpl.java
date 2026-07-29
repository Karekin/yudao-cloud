package cn.iocoder.yudao.module.cloudmold.crossborder.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCaseView;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCommand;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderCommandApi;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderOperation;
import cn.iocoder.yudao.module.cloudmold.crossborder.api.CrossBorderResult;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.CaseRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.ComplianceAssessmentRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.dataobject.CrossBorderRecords.StatusHistoryRecord;
import cn.iocoder.yudao.module.cloudmold.crossborder.dal.mysql.CrossBorderCaseMapper;
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
public class CrossBorderCaseServiceImpl implements CrossBorderCommandApi {
    static final int OPERATION_SUCCEEDED = 10;

    private static final String SOURCE_SYSTEM = "cloudmold-crossborder";
    private static final String AGGREGATE_TYPE = "crossborder_direct_mail_case";
    private static final String TRADE_MODE_DIRECT_MAIL = "DIRECT_MAIL";
    private static final String COUNTRY_CN = "CN";
    private static final String COUNTRY_US = "US";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_:-]{0,63}");
    private static final Pattern SAFE_TEXT =
            Pattern.compile("[\\p{L}\\p{N} _.,:/()#%=+\\-，。；：、（）]{1,255}");
    private static final Pattern HS_CODE = Pattern.compile("[0-9]{6,12}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final Set<String> TERMINAL_STATUSES = Set.of("DELIVERED", "CLOSED");

    private final CrossBorderCaseMapper mapper;
    private final OutboxAppender outboxAppender;
    private final CrossBorderActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CrossBorderResult execute(CrossBorderCommand command, String actorPrincipalId) {
        validateEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Instant occurredAt = command.getOccurredAt();
        LocalDateTime now = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve cross-border operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "cross-border operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different cross-border payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing cross-border operation is incomplete");
            CrossBorderResult replay = JsonUtils.parseObject(operation.getResultJson(), CrossBorderResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_CASE -> createCase(tenantId, command, actorPrincipalId, now);
            case RECORD_COMPLIANCE_ASSESSMENT -> recordAssessment(tenantId, command, actorPrincipalId, now);
            case SELECT_ROUTE -> selectRoute(tenantId, command, actorPrincipalId, now);
            case APPROVE_COMPLIANCE -> approveCompliance(tenantId, command, actorPrincipalId, now);
            case ASSEMBLE_DECLARATION -> assembleDeclaration(tenantId, command, actorPrincipalId, now);
            case VALIDATE_THREE_DOCUMENTS -> validateDocuments(tenantId, command, actorPrincipalId, now);
            case BOOK_CARRIER -> bookCarrier(tenantId, command, actorPrincipalId, now);
            case RECORD_LABEL -> recordLabel(tenantId, command, actorPrincipalId, now);
            case HANDOVER -> handover(tenantId, command, actorPrincipalId, now);
            case SUBMIT_CUSTOMS -> submitCustoms(tenantId, command, actorPrincipalId, now);
            case RECORD_CUSTOMS_RELEASE -> recordCustomsRelease(tenantId, command, actorPrincipalId, now);
            case RECORD_DELIVERY -> recordDelivery(tenantId, command, actorPrincipalId, now);
            case CLOSE_CASE -> closeCase(tenantId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        CrossBorderResult result = CrossBorderResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(outcome.caseRecord().getCaseId())
                .aggregateVersion(outcome.caseRecord().getVersion())
                .status(outcome.caseRecord().getStatus())
                .caseId(outcome.caseRecord().getCaseId())
                .caseNo(outcome.caseRecord().getCaseNo())
                .approvalRef(outcome.caseRecord().getApprovalRef())
                .bookingRef(outcome.caseRecord().getBookingRef())
                .trackingNumber(outcome.caseRecord().getTrackingNumber())
                .customsDeclarationRef(outcome.caseRecord().getCustomsDeclarationRef())
                .customsReleaseRef(outcome.caseRecord().getCustomsReleaseRef())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, AGGREGATE_TYPE,
                        outcome.caseRecord().getCaseId(), JsonUtils.toJsonString(result), now) == 1,
                "cross-border operation completion conflict");
        return result;
    }

    private Outcome createCase(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                               LocalDateTime now) {
        String caseId = valueOrUuid(command.getCaseId());
        CaseRecord row = new CaseRecord()
                .setCaseId(caseId)
                .setTenantId(tenantId)
                .setCaseNo(caseNo(caseId))
                .setOrderId(command.getOrderId())
                .setFulfillmentId(command.getFulfillmentId())
                .setTradeMode(command.getTradeMode())
                .setOriginCountry(command.getOriginCountry())
                .setDestinationCountry(command.getDestinationCountry())
                .setStatus("DRAFT")
                .setCreatedByPrincipalId(actorPrincipalId)
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertCase(row) == 1, "failed to persist cross-border case");
        insertHistory(tenantId, row, 1L, command.getOperation().name(), null, "DRAFT",
                actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.case.created", row, command);
    }

    private Outcome recordAssessment(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                     LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DRAFT");
        CrossBorderCommand.ComplianceAssessment assessment = command.getAssessment();
        String assessmentId = UUID.randomUUID().toString();
        ComplianceAssessmentRecord record = new ComplianceAssessmentRecord()
                .setAssessmentId(assessmentId)
                .setTenantId(tenantId)
                .setCaseId(row.getCaseId())
                .setFactsJson(jsonList(assessment.getFacts()))
                .setOptionsJson(jsonList(assessment.getOptions()))
                .setRecommendation(assessment.getRecommendation())
                .setRisksJson(jsonList(assessment.getRisks()))
                .setConfidence(scaleConfidence(assessment.getConfidence()))
                .setMissingFactsJson(jsonList(assessment.getMissingFacts()))
                .setEvidenceRef(assessment.getEvidenceRef())
                .setAssessedByPrincipalId(actorPrincipalId)
                .setAssessedAt(now)
                .setCreatedAt(now);
        require(mapper.insertAssessment(record) == 1, "failed to persist compliance assessment");
        row.setAssessmentId(assessmentId).setStatus("COMPLIANCE_ASSESSED")
                .setVersion(row.getVersion() + 1).setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "DRAFT");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "DRAFT",
                "COMPLIANCE_ASSESSED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.compliance.assessment.recorded", row, command);
    }

    private Outcome selectRoute(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "COMPLIANCE_ASSESSED");
        CrossBorderCommand.RouteDefinition route = command.getRoute();
        row.setRouteCode(route.getRouteCode())
                .setRouteCarrierCode(route.getCarrierCode())
                .setRouteServiceLevel(route.getServiceLevel())
                .setRouteSlaDays(route.getSlaDays())
                .setStatus("ROUTE_SELECTED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "COMPLIANCE_ASSESSED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "COMPLIANCE_ASSESSED",
                "ROUTE_SELECTED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.route.selected", row, command);
    }

    private Outcome approveCompliance(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                      LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "ROUTE_SELECTED");
        require(row.getAssessmentId() != null, "cross-border case is missing a compliance assessment");
        row.setApprovalRef(command.getApprovalRef())
                .setStatus("COMPLIANCE_APPROVED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "ROUTE_SELECTED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "ROUTE_SELECTED",
                "COMPLIANCE_APPROVED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.compliance.approved", row, command);
    }

    private Outcome assembleDeclaration(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                        LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "COMPLIANCE_APPROVED");
        CrossBorderCommand.DeclarationDefinition declaration = command.getDeclaration();
        row.setDeclarationId(valueOrFallback(declaration.getDeclarationId(), UUID.randomUUID().toString()))
                .setHsCode(declaration.getHsCode())
                .setGoodsDescription(declaration.getGoodsDescription())
                .setQuantity(declaration.getQuantity())
                .setDeclaredAmountMinor(declaration.getDeclaredAmountMinor())
                .setCurrency(declaration.getCurrency())
                .setDeclarationEvidenceRef(declaration.getEvidenceRef())
                .setStatus("DECLARATION_ASSEMBLED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "COMPLIANCE_APPROVED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "COMPLIANCE_APPROVED",
                "DECLARATION_ASSEMBLED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.declaration.assembled", row, command);
    }

    private Outcome validateDocuments(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                      LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DECLARATION_ASSEMBLED");
        CrossBorderCommand.DocumentsDefinition documents = command.getDocuments();
        row.setDocumentOrderRef(documents.getOrderRef())
                .setDocumentPaymentRef(documents.getPaymentRef())
                .setDocumentLogisticsRef(documents.getLogisticsRef())
                .setDocumentValidationEvidenceRef(documents.getValidationEvidenceRef())
                .setStatus("DOCUMENTS_VALIDATED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "DECLARATION_ASSEMBLED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "DECLARATION_ASSEMBLED",
                "DOCUMENTS_VALIDATED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.documents.validated", row, command);
    }

    private Outcome bookCarrier(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DOCUMENTS_VALIDATED");
        CrossBorderCommand.BookingDefinition booking = command.getBooking();
        row.setBookingRef(booking.getBookingRef())
                .setBookingCarrierCode(booking.getCarrierCode())
                .setBookingServiceLevel(booking.getServiceLevel())
                .setStatus("CARRIER_BOOKED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "DOCUMENTS_VALIDATED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "DOCUMENTS_VALIDATED",
                "CARRIER_BOOKED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.carrier.booked", row, command);
    }

    private Outcome recordLabel(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "CARRIER_BOOKED");
        CrossBorderCommand.LabelDefinition label = command.getLabel();
        row.setLabelRef(label.getLabelRef())
                .setTrackingNumber(label.getTrackingNumber())
                .setStatus("LABEL_RECORDED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "CARRIER_BOOKED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "CARRIER_BOOKED",
                "LABEL_RECORDED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.label.recorded", row, command);
    }

    private Outcome handover(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                             LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "LABEL_RECORDED");
        row.setHandoverRef(command.getHandoverRef())
                .setStatus("HANDED_OVER")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "LABEL_RECORDED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "LABEL_RECORDED",
                "HANDED_OVER", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.handover.recorded", row, command);
    }

    private Outcome submitCustoms(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                  LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "HANDED_OVER");
        require(Objects.equals(row.getApprovalRef(), command.getApprovalRef()),
                "customs submission approval evidence does not match the approved plan");
        row.setCustomsDeclarationRef(command.getCustomsDeclarationRef())
                .setStatus("CUSTOMS_SUBMITTED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "HANDED_OVER");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "HANDED_OVER",
                "CUSTOMS_SUBMITTED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.customs.submitted", row, command);
    }

    private Outcome recordCustomsRelease(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                         LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "CUSTOMS_SUBMITTED");
        row.setCustomsReleaseRef(command.getCustomsReleaseRef())
                .setStatus("CUSTOMS_RELEASED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "CUSTOMS_SUBMITTED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "CUSTOMS_SUBMITTED",
                "CUSTOMS_RELEASED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.customs.released", row, command);
    }

    private Outcome recordDelivery(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                                   LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "CUSTOMS_RELEASED");
        row.setDeliveryEvidenceRef(command.getDeliveryEvidenceRef())
                .setStatus("DELIVERED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "CUSTOMS_RELEASED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "CUSTOMS_RELEASED",
                "DELIVERED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.delivery.recorded", row, command);
    }

    private Outcome closeCase(Long tenantId, CrossBorderCommand command, String actorPrincipalId,
                              LocalDateTime now) {
        CaseRecord row = requireCaseForUpdate(tenantId, command.getCaseId(), command.getExpectedVersion());
        requireStatus(row, "DELIVERED");
        row.setCloseReason(command.getCloseReason())
                .setStatus("CLOSED")
                .setVersion(row.getVersion() + 1)
                .setUpdatedAt(now)
                .setClosedAt(now);
        updateCase(row, tenantId, command.getCaseId(), command.getExpectedVersion(), "DELIVERED");
        insertHistory(tenantId, row, row.getVersion(), command.getOperation().name(), "DELIVERED",
                "CLOSED", actorPrincipalId, command.getOccurredAt(), detail(command));
        return outcome("crossborder.case.closed", row, command);
    }

    private CaseRecord requireCaseForUpdate(Long tenantId, String caseId, Long expectedVersion) {
        CaseRecord row = mapper.selectCaseForUpdate(tenantId, caseId);
        require(row != null, "cross-border case not found");
        require(Objects.equals(row.getVersion(), expectedVersion), "cross-border case version conflict");
        return row;
    }

    private void updateCase(CaseRecord row, Long tenantId, String caseId, Long expectedVersion, String expectedStatus) {
        require(mapper.updateCase(row, tenantId, caseId, expectedVersion, expectedStatus) == 1,
                "cross-border case transition conflict");
    }

    private void insertHistory(Long tenantId, CaseRecord row, Long version, String operation, String previousStatus,
                               String currentStatus, String actorPrincipalId, Instant occurredAt, String detailJson) {
        require(mapper.insertHistory(new StatusHistoryRecord()
                        .setTenantId(tenantId)
                        .setCaseId(row.getCaseId())
                        .setAggregateVersion(version)
                        .setOperation(operation)
                        .setPreviousStatus(previousStatus)
                        .setCurrentStatus(currentStatus)
                        .setActorPrincipalId(actorPrincipalId)
                        .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                        .setDetailJson(detailJson)
                        .setCreatedAt(row.getUpdatedAt())) == 1,
                "failed to persist cross-border status history");
    }

    private void appendEvent(Long tenantId, CrossBorderCommand command, Outcome outcome) {
        CaseRecord row = outcome.caseRecord();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("case_id", row.getCaseId());
        payload.put("case_no", row.getCaseNo());
        payload.put("order_id", row.getOrderId());
        payload.put("fulfillment_id", row.getFulfillmentId());
        payload.put("trade_mode", row.getTradeMode());
        payload.put("origin_country", row.getOriginCountry());
        payload.put("destination_country", row.getDestinationCountry());
        payload.put("previous_status", outcome.previousStatus());
        payload.put("current_status", row.getStatus());
        payload.put("approval_ref", row.getApprovalRef());
        payload.put("route_code", row.getRouteCode());
        payload.put("route_carrier_code", row.getRouteCarrierCode());
        payload.put("route_service_level", row.getRouteServiceLevel());
        payload.put("route_sla_days", row.getRouteSlaDays());
        payload.put("declaration_id", row.getDeclarationId());
        payload.put("hs_code", row.getHsCode());
        payload.put("goods_description", row.getGoodsDescription());
        payload.put("quantity", row.getQuantity() == null ? null : row.getQuantity().toPlainString());
        payload.put("declared_amount_minor", row.getDeclaredAmountMinor());
        payload.put("currency", row.getCurrency());
        payload.put("booking_ref", row.getBookingRef());
        payload.put("booking_carrier_code", row.getBookingCarrierCode());
        payload.put("booking_service_level", row.getBookingServiceLevel());
        payload.put("label_ref", row.getLabelRef());
        payload.put("tracking_number", row.getTrackingNumber());
        payload.put("handover_ref", row.getHandoverRef());
        payload.put("customs_declaration_ref", row.getCustomsDeclarationRef());
        payload.put("customs_release_ref", row.getCustomsReleaseRef());
        payload.put("delivery_evidence_ref", row.getDeliveryEvidenceRef());
        payload.put("close_reason", row.getCloseReason());
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
                .headers(Map.of("trade_mode", row.getTradeMode(), "route_scope", "CN_US_DIRECT_MAIL_V1"))
                .destination("lakehouse")
                .maxAttempts(25)
                .build());
    }

    private Outcome outcome(String eventType, CaseRecord row, CrossBorderCommand command) {
        return new Outcome(eventType, row, previousStatus(command.getOperation(), row.getStatus()), detailMap(command));
    }

    private String previousStatus(CrossBorderOperation operation, String currentStatus) {
        return switch (operation) {
            case CREATE_CASE -> null;
            case RECORD_COMPLIANCE_ASSESSMENT -> "DRAFT";
            case SELECT_ROUTE -> "COMPLIANCE_ASSESSED";
            case APPROVE_COMPLIANCE -> "ROUTE_SELECTED";
            case ASSEMBLE_DECLARATION -> "COMPLIANCE_APPROVED";
            case VALIDATE_THREE_DOCUMENTS -> "DECLARATION_ASSEMBLED";
            case BOOK_CARRIER -> "DOCUMENTS_VALIDATED";
            case RECORD_LABEL -> "CARRIER_BOOKED";
            case HANDOVER -> "LABEL_RECORDED";
            case SUBMIT_CUSTOMS -> "HANDED_OVER";
            case RECORD_CUSTOMS_RELEASE -> "CUSTOMS_SUBMITTED";
            case RECORD_DELIVERY -> "CUSTOMS_RELEASED";
            case CLOSE_CASE -> "DELIVERED";
        };
    }

    private String detail(CrossBorderCommand command) {
        return JsonUtils.toJsonString(detailMap(command));
    }

    private Map<String, Object> detailMap(CrossBorderCommand command) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", command.getOperation().name());
        detail.put("assessment", command.getAssessment());
        detail.put("route", command.getRoute());
        detail.put("approvalRef", command.getApprovalRef());
        detail.put("declaration", command.getDeclaration());
        detail.put("documents", command.getDocuments());
        detail.put("booking", command.getBooking());
        detail.put("label", command.getLabel());
        detail.put("handoverRef", command.getHandoverRef());
        detail.put("customsDeclarationRef", command.getCustomsDeclarationRef());
        detail.put("customsReleaseRef", command.getCustomsReleaseRef());
        detail.put("deliveryEvidenceRef", command.getDeliveryEvidenceRef());
        detail.put("closeReason", command.getCloseReason());
        return detail;
    }

    private static String jsonList(List<String> values) {
        return JsonUtils.toJsonString(values == null ? List.of() : values);
    }

    private static BigDecimal scaleConfidence(BigDecimal confidence) {
        return confidence.setScale(4, RoundingMode.HALF_UP);
    }

    private static String caseNo(String caseId) {
        String cleaned = caseId.replace("-", "").toUpperCase(Locale.ROOT);
        if (cleaned.length() >= 20) {
            return "CMCB" + cleaned.substring(0, 20);
        }
        String padded = cleaned + DigestUtil.md5Hex(caseId).toUpperCase(Locale.ROOT);
        return "CMCB" + padded.substring(0, 20);
    }

    private static String valueOrUuid(String value) {
        return valueOrFallback(value, UUID.randomUUID().toString());
    }

    private static String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void validateEnvelope(CrossBorderCommand command) {
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
        if (command.getOperation() != CrossBorderOperation.CREATE_CASE) {
            requireRef(command.getCaseId(), "caseId", 128);
            require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                    "expectedVersion must be positive");
        }
        switch (command.getOperation()) {
            case CREATE_CASE -> validateCreate(command);
            case RECORD_COMPLIANCE_ASSESSMENT -> validateAssessment(command.getAssessment());
            case SELECT_ROUTE -> validateRoute(command.getRoute());
            case APPROVE_COMPLIANCE -> requireRef(command.getApprovalRef(), "approvalRef", 128);
            case ASSEMBLE_DECLARATION -> validateDeclaration(command.getDeclaration());
            case VALIDATE_THREE_DOCUMENTS -> validateDocuments(command.getDocuments());
            case BOOK_CARRIER -> validateBooking(command.getBooking());
            case RECORD_LABEL -> validateLabel(command.getLabel());
            case HANDOVER -> requireRef(command.getHandoverRef(), "handoverRef", 128);
            case SUBMIT_CUSTOMS -> {
                requireRef(command.getApprovalRef(), "approvalRef", 128);
                requireRef(command.getCustomsDeclarationRef(), "customsDeclarationRef", 128);
            }
            case RECORD_CUSTOMS_RELEASE -> requireRef(command.getCustomsReleaseRef(), "customsReleaseRef", 128);
            case RECORD_DELIVERY -> requireRef(command.getDeliveryEvidenceRef(), "deliveryEvidenceRef", 255);
            case CLOSE_CASE -> requireText(command.getCloseReason(), "closeReason", 255);
        }
    }

    private void validateCreate(CrossBorderCommand command) {
        requireRef(command.getOrderId(), "orderId", 128);
        requireRef(command.getFulfillmentId(), "fulfillmentId", 128);
        require(TRADE_MODE_DIRECT_MAIL.equals(command.getTradeMode()),
                "cross-border direct-mail V1 only supports tradeMode DIRECT_MAIL");
        require(COUNTRY_CN.equals(command.getOriginCountry()),
                "cross-border direct-mail V1 only supports originCountry CN");
        require(COUNTRY_US.equals(command.getDestinationCountry()),
                "cross-border direct-mail V1 only supports destinationCountry US");
    }

    private void validateAssessment(CrossBorderCommand.ComplianceAssessment assessment) {
        require(assessment != null, "assessment is required");
        validateLines(assessment.getFacts(), "assessment.facts", 1, 20);
        validateLines(assessment.getOptions(), "assessment.options", 1, 10);
        requireText(assessment.getRecommendation(), "assessment.recommendation", 255);
        require(assessment.getOptions().contains(assessment.getRecommendation()),
                "assessment.recommendation must be one of assessment.options");
        validateLines(assessment.getRisks(), "assessment.risks", 0, 10);
        require(assessment.getConfidence() != null
                        && assessment.getConfidence().compareTo(BigDecimal.ZERO) >= 0
                        && assessment.getConfidence().compareTo(BigDecimal.ONE) <= 0,
                "assessment.confidence must be between 0 and 1");
        validateLines(assessment.getMissingFacts(), "assessment.missingFacts", 0, 20);
        requireRef(assessment.getEvidenceRef(), "assessment.evidenceRef", 255);
    }

    private void validateRoute(CrossBorderCommand.RouteDefinition route) {
        require(route != null, "route is required");
        requireCode(route.getRouteCode(), "route.routeCode");
        requireCode(route.getCarrierCode(), "route.carrierCode");
        requireText(route.getServiceLevel(), "route.serviceLevel", 64);
        require(route.getSlaDays() != null && route.getSlaDays() > 0 && route.getSlaDays() <= 90,
                "route.slaDays must be between 1 and 90");
    }

    private void validateDeclaration(CrossBorderCommand.DeclarationDefinition declaration) {
        require(declaration != null, "declaration is required");
        if (declaration.getDeclarationId() != null && !declaration.getDeclarationId().isBlank()) {
            requireRef(declaration.getDeclarationId(), "declaration.declarationId", 128);
        }
        require(HS_CODE.matcher(Objects.toString(declaration.getHsCode(), "")).matches(),
                "declaration.hsCode must be 6 to 12 digits");
        requireText(declaration.getGoodsDescription(), "declaration.goodsDescription", 255);
        require(declaration.getQuantity() != null && declaration.getQuantity().signum() > 0,
                "declaration.quantity must be positive");
        require(declaration.getDeclaredAmountMinor() != null && declaration.getDeclaredAmountMinor() > 0,
                "declaration.declaredAmountMinor must be positive");
        require(CURRENCY.matcher(Objects.toString(declaration.getCurrency(), "")).matches(),
                "declaration.currency must be a 3-letter code");
        requireRef(declaration.getEvidenceRef(), "declaration.evidenceRef", 255);
    }

    private void validateDocuments(CrossBorderCommand.DocumentsDefinition documents) {
        require(documents != null, "documents is required");
        requireRef(documents.getOrderRef(), "documents.orderRef", 128);
        requireRef(documents.getPaymentRef(), "documents.paymentRef", 128);
        requireRef(documents.getLogisticsRef(), "documents.logisticsRef", 128);
        requireRef(documents.getValidationEvidenceRef(), "documents.validationEvidenceRef", 255);
    }

    private void validateBooking(CrossBorderCommand.BookingDefinition booking) {
        require(booking != null, "booking is required");
        requireRef(booking.getBookingRef(), "booking.bookingRef", 128);
        requireCode(booking.getCarrierCode(), "booking.carrierCode");
        requireText(booking.getServiceLevel(), "booking.serviceLevel", 64);
    }

    private void validateLabel(CrossBorderCommand.LabelDefinition label) {
        require(label != null, "label is required");
        requireRef(label.getLabelRef(), "label.labelRef", 128);
        requireRef(label.getTrackingNumber(), "label.trackingNumber", 128);
    }

    private void validateLines(List<String> values, String field, int min, int max) {
        require(values != null && values.size() >= min && values.size() <= max,
                field + " must contain between " + min + " and " + max + " entries");
        for (String value : values) {
            requireText(value, field, 255);
        }
    }

    private void requireStatus(CaseRecord row, String expectedStatus) {
        require(expectedStatus.equals(row.getStatus()),
                "cross-border case must be in " + expectedStatus + " status");
    }

    private void requireUuid(String value, String field) {
        try {
            require(value != null, field + " is required");
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private void requireCode(String value, String field) {
        require(SAFE_CODE.matcher(Objects.toString(value, "")).matches(),
                field + " must be an uppercase code");
    }

    private void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength
                        && SAFE_TEXT.matcher(value).matches(),
                field + " is invalid");
    }

    private void requireRef(String value, String field, int maxLength) {
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
