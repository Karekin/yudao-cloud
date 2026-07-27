package cn.iocoder.yudao.module.cloudmold.fulfillment.service.exception;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.FulfillmentOrderDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception.FulfillmentExceptionDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.exception.FulfillmentExceptionOperationDO;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.FulfillmentOrderMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.exception.FulfillmentExceptionMapper;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.exception.FulfillmentExceptionOperationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical write authority for fulfillment exception cases.
 *
 * <p>The exception lifecycle is deliberately separate from shipment state. It
 * records the disposition plan, BPM approval evidence and execution evidence
 * without pretending that a carrier or warehouse action happened.</p>
 */
@Service
@RequiredArgsConstructor
public class FulfillmentExceptionCommandServiceImpl
        implements FulfillmentExceptionCommandApi, FulfillmentExceptionQueryApi {

    static final int OPERATION_SUCCEEDED = 10;

    private final FulfillmentExceptionOperationMapper operationMapper;
    private final FulfillmentExceptionMapper exceptionMapper;
    private final FulfillmentOrderMapper fulfillmentMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentExceptionView execute(FulfillmentExceptionCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (command.getOperation() == FulfillmentExceptionOperation.OPEN) {
            validateOpen(command);
        } else {
            requireText(command.getExceptionId(), "exceptionId", 36);
            require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                    "expectedVersion must be positive");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve fulfillment exception operation");
        FulfillmentExceptionOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "fulfillment exception operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different fulfillment exception payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing fulfillment exception operation is not complete");
            FulfillmentExceptionView replay = JsonUtils.parseObject(operation.getResultJson(),
                    FulfillmentExceptionView.class);
            replay.setDuplicate(true);
            return replay;
        }

        FulfillmentExceptionView result = command.getOperation() == FulfillmentExceptionOperation.OPEN
                ? open(tenantId, operationId, command, now)
                : transition(tenantId, operationId, command, now);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getExceptionId(),
                JsonUtils.toJsonString(result), now) == 1,
                "fulfillment exception operation completion conflict");
        return result;
    }

    @Override
    public FulfillmentExceptionView get(String exceptionId) {
        requireText(exceptionId, "exceptionId", 36);
        FulfillmentExceptionDO exception = exceptionMapper.selectTenant(
                TenantContextHolder.getRequiredTenantId(), exceptionId);
        require(exception != null, "canonical fulfillment exception does not exist");
        return view(null, exception, false);
    }

    @Override
    public FulfillmentExceptionView getLatestByOrder(String orderId) {
        requireText(orderId, "orderId", 36);
        FulfillmentExceptionDO exception = exceptionMapper.selectLatestByOrder(
                TenantContextHolder.getRequiredTenantId(), orderId);
        return exception == null ? null : view(null, exception, false);
    }

    private FulfillmentExceptionView open(Long tenantId, Long operationId,
                                          FulfillmentExceptionCommand command, LocalDateTime now) {
        FulfillmentOrderDO fulfillment = fulfillmentMapper.selectByIdForValidation(
                tenantId, command.getFulfillmentId());
        require(fulfillment != null, "canonical fulfillment does not exist");
        require(Objects.equals(fulfillment.getOrderId(), command.getOrderId()),
                "fulfillment does not belong to orderId");
        require(!"CANCELLED".equals(fulfillment.getStatus()),
                "cancelled fulfillment cannot open a new exception");
        require(exceptionMapper.countActive(tenantId, fulfillment.getFulfillmentId()) == 0,
                "fulfillment already has an active exception");

        String exceptionId = UUID.randomUUID().toString();
        String exceptionNo = "CMX" + exceptionId.replace("-", "").substring(0, 20)
                .toUpperCase(Locale.ROOT);
        FulfillmentExceptionDO exception = new FulfillmentExceptionDO()
                .setExceptionId(exceptionId)
                .setTenantId(tenantId)
                .setExceptionNo(exceptionNo)
                .setRunId(command.getRunId())
                .setFulfillmentId(fulfillment.getFulfillmentId())
                .setOrderId(fulfillment.getOrderId())
                .setExceptionType(command.getExceptionType().name())
                .setStatus(FulfillmentExceptionStatus.OPEN.name())
                .setReason(command.getReason())
                .setVersion(1L)
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC))
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(exceptionMapper.insert(exception) == 1, "failed to create canonical fulfillment exception");
        appendEvent(tenantId, exception, null, command);
        return view(operationId, exception, false);
    }

    private FulfillmentExceptionView transition(Long tenantId, Long operationId,
                                                FulfillmentExceptionCommand command, LocalDateTime now) {
        FulfillmentExceptionDO exception = exceptionMapper.selectForUpdate(tenantId, command.getExceptionId());
        require(exception != null, "canonical fulfillment exception does not exist");
        require(Objects.equals(exception.getVersion(), command.getExpectedVersion()),
                "canonical fulfillment exception version conflict");
        Transition transition = transition(command.getOperation(), exception.getStatus());
        validateTransition(command, exception);

        String actionCode = command.getAction() == null ? null : command.getAction().name();
        String actionDescription = command.getOperation() == FulfillmentExceptionOperation.PLAN
                ? command.getActionDescription() : null;
        String planEvidenceRef = command.getOperation() == FulfillmentExceptionOperation.PLAN
                ? command.getEvidenceRef() : null;
        String approvalRef = command.getOperation() == FulfillmentExceptionOperation.REQUEST_APPROVAL
                ? command.getApprovalRef() : null;
        String resolutionEvidenceRef = command.getOperation() == FulfillmentExceptionOperation.RESOLVE
                ? command.getEvidenceRef() : null;
        String resolutionSummary = command.getOperation() == FulfillmentExceptionOperation.RESOLVE
                ? command.getResolutionSummary() : null;
        LocalDateTime resolvedAt = command.getOperation() == FulfillmentExceptionOperation.RESOLVE
                ? LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC) : null;
        LocalDateTime closedAt = command.getOperation() == FulfillmentExceptionOperation.CLOSE
                ? LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC) : null;

        require(exceptionMapper.transition(tenantId, exception.getExceptionId(), exception.getVersion(),
                transition.expectedStatus(), transition.nextStatus(), actionCode, actionDescription,
                planEvidenceRef, approvalRef, resolutionEvidenceRef, resolutionSummary,
                resolvedAt, closedAt, now) == 1,
                "canonical fulfillment exception transition conflict");

        String previousStatus = exception.getStatus();
        exception.setStatus(transition.nextStatus())
                .setVersion(exception.getVersion() + 1)
                .setUpdatedAt(now);
        if (actionCode != null) exception.setActionCode(actionCode);
        if (actionDescription != null) exception.setActionDescription(actionDescription);
        if (planEvidenceRef != null) exception.setPlanEvidenceRef(planEvidenceRef);
        if (approvalRef != null) exception.setApprovalRef(approvalRef);
        if (resolutionEvidenceRef != null) exception.setResolutionEvidenceRef(resolutionEvidenceRef);
        if (resolutionSummary != null) exception.setResolutionSummary(resolutionSummary);
        if (resolvedAt != null) exception.setResolvedAt(resolvedAt);
        if (closedAt != null) exception.setClosedAt(closedAt);
        appendEvent(tenantId, exception, previousStatus, command);
        return view(operationId, exception, false);
    }

    private static void validateTransition(FulfillmentExceptionCommand command,
                                           FulfillmentExceptionDO exception) {
        switch (command.getOperation()) {
            case PLAN -> {
                require(command.getAction() != null, "PLAN requires action");
                requireText(command.getActionDescription(), "actionDescription", 1000);
                requireText(command.getEvidenceRef(), "evidenceRef", 255);
            }
            case REQUEST_APPROVAL -> requireText(command.getApprovalRef(), "approvalRef", 255);
            case START_EXECUTION -> {
                requireText(command.getApprovalRef(), "approvalRef", 255);
                require(Objects.equals(exception.getApprovalRef(), command.getApprovalRef()),
                        "START_EXECUTION requires the approved BPM reference");
            }
            case RESOLVE -> {
                requireText(command.getEvidenceRef(), "evidenceRef", 255);
                requireText(command.getResolutionSummary(), "resolutionSummary", 2000);
            }
            case CLOSE -> {
                // Closing only acknowledges an already evidenced resolution.
            }
            case OPEN -> throw new IllegalArgumentException("OPEN is not a transition");
        }
    }

    private void appendEvent(Long tenantId, FulfillmentExceptionDO exception, String previousStatus,
                             FulfillmentExceptionCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", exception.getRunId());
        payload.put("exception_id", exception.getExceptionId());
        payload.put("exception_no", exception.getExceptionNo());
        payload.put("fulfillment_id", exception.getFulfillmentId());
        payload.put("order_id", exception.getOrderId());
        payload.put("exception_type", exception.getExceptionType());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", exception.getStatus());
        payload.put("action_code", exception.getActionCode());
        payload.put("action_description", exception.getActionDescription());
        payload.put("plan_evidence_ref", exception.getPlanEvidenceRef());
        payload.put("approval_ref", exception.getApprovalRef());
        payload.put("resolution_evidence_ref", exception.getResolutionEvidenceRef());
        payload.put("reason", exception.getReason());
        payload.put("resolution_summary", exception.getResolutionSummary());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("fulfillment.exception.status.changed")
                .schemaVersion(1)
                .sourceSystem("cloudmold-fulfillment")
                .tenantId(tenantId)
                .aggregateType("fulfillment_exception")
                .aggregateId(exception.getExceptionId())
                .aggregateVersion(exception.getVersion())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey())
                .payload(payload)
                .headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse")
                .build());
    }

    private static FulfillmentExceptionView view(Long operationId, FulfillmentExceptionDO exception,
                                                 boolean duplicate) {
        return FulfillmentExceptionView.builder()
                .operationId(operationId)
                .exceptionId(exception.getExceptionId())
                .exceptionNo(exception.getExceptionNo())
                .runId(exception.getRunId())
                .fulfillmentId(exception.getFulfillmentId())
                .orderId(exception.getOrderId())
                .exceptionType(FulfillmentExceptionType.valueOf(exception.getExceptionType()))
                .status(FulfillmentExceptionStatus.valueOf(exception.getStatus()))
                .action(exception.getActionCode() == null
                        ? null : FulfillmentExceptionAction.valueOf(exception.getActionCode()))
                .actionDescription(exception.getActionDescription())
                .planEvidenceRef(exception.getPlanEvidenceRef())
                .approvalRef(exception.getApprovalRef())
                .resolutionEvidenceRef(exception.getResolutionEvidenceRef())
                .reason(exception.getReason())
                .resolutionSummary(exception.getResolutionSummary())
                .aggregateVersion(exception.getVersion())
                .duplicate(duplicate)
                .build();
    }

    private static Transition transition(FulfillmentExceptionOperation operation, String status) {
        return switch (operation) {
            case PLAN -> requireTransition(status, FulfillmentExceptionStatus.OPEN,
                    FulfillmentExceptionStatus.PLANNED);
            case REQUEST_APPROVAL -> requireTransition(status, FulfillmentExceptionStatus.PLANNED,
                    FulfillmentExceptionStatus.WAITING_APPROVAL);
            case START_EXECUTION -> requireTransition(status, FulfillmentExceptionStatus.WAITING_APPROVAL,
                    FulfillmentExceptionStatus.EXECUTING);
            case RESOLVE -> requireTransition(status, FulfillmentExceptionStatus.EXECUTING,
                    FulfillmentExceptionStatus.RESOLVED);
            case CLOSE -> requireTransition(status, FulfillmentExceptionStatus.RESOLVED,
                    FulfillmentExceptionStatus.CLOSED);
            case OPEN -> throw new IllegalArgumentException("OPEN is not a transition");
        };
    }

    private static Transition requireTransition(String actual, FulfillmentExceptionStatus expected,
                                                FulfillmentExceptionStatus next) {
        require(expected.name().equals(actual), next.name() + " requires " + expected.name());
        return new Transition(expected.name(), next.name());
    }

    private static void validateCommon(FulfillmentExceptionCommand command) {
        require(command != null && command.getOperation() != null,
                "fulfillment exception operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static void validateOpen(FulfillmentExceptionCommand command) {
        require(command.getExceptionId() == null, "OPEN does not accept exceptionId");
        require(command.getExpectedVersion() == null, "OPEN does not accept expectedVersion");
        requireText(command.getFulfillmentId(), "fulfillmentId", 36);
        requireText(command.getOrderId(), "orderId", 36);
        require(command.getExceptionType() != null, "exceptionType is required");
        requireText(command.getReason(), "reason", 2000);
    }

    private static String fingerprint(Long tenantId, FulfillmentExceptionCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId);
        value.put("operation", command.getOperation());
        value.put("run_id", command.getRunId());
        value.put("exception_id", command.getExceptionId());
        value.put("fulfillment_id", command.getFulfillmentId());
        value.put("order_id", command.getOrderId());
        value.put("exception_type", command.getExceptionType());
        value.put("expected_version", command.getExpectedVersion());
        value.put("action", command.getAction());
        value.put("action_description", command.getActionDescription());
        value.put("evidence_ref", command.getEvidenceRef());
        value.put("approval_ref", command.getApprovalRef());
        value.put("reason", command.getReason());
        value.put("resolution_summary", command.getResolutionSummary());
        value.put("occurred_at", command.getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Transition(String expectedStatus, String nextStatus) {
    }
}
