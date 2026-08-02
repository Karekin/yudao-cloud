package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionCommandApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.PurchaseRequisitionResult;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisition;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionLine;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseRequisitionStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.reference.ProcurementReferenceValidationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PurchaseRequisitionService implements PurchaseRequisitionCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String AGGREGATE_TYPE = "purchase_requisition";
    private static final String COMMAND_TYPE = "CREATE_APPROVED_PURCHASE_REQUISITION";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");

    private final ProcurementMapper mapper;
    private final OutboxAppender outboxAppender;
    private final ProcurementActorPrincipalPort actorPrincipalPort;
    private final ProcurementReferenceValidationPort referenceValidationPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseRequisitionResult createApproved(PurchaseRequisitionCommand command, String actorPrincipalId) {
        validate(command, actorPrincipalId);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), COMMAND_TYPE,
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve purchase requisition operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "purchase requisition operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different purchase requisition payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing purchase requisition operation is incomplete");
            PurchaseRequisitionResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), PurchaseRequisitionResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        List<PurchaseRequisitionLine> lines = new ArrayList<>();
        List<PurchaseRequisitionDeliverySchedule> schedules = new ArrayList<>();
        for (PurchaseRequisitionCommand.LineDefinition inputLine : command.getLines()) {
            referenceValidationPort.requireActiveSku(inputLine.getCanonicalSkuId());
            lines.add(new PurchaseRequisitionLine()
                    .setLineId(inputLine.getLineId()).setTenantId(tenantId)
                    .setRequisitionId(command.getRequisitionId()).setLineNumber(inputLine.getLineNumber())
                    .setCanonicalSkuId(inputLine.getCanonicalSkuId())
                    .setRequestedQuantity(inputLine.getRequestedQuantity()).setUomCode(inputLine.getUomCode())
                    .setCreatedAt(now).setUpdatedAt(now));
            for (PurchaseRequisitionCommand.DeliveryScheduleDefinition inputSchedule : inputLine.getSchedules()) {
                referenceValidationPort.requireActiveWarehouse(inputSchedule.getCanonicalWarehouseId());
                schedules.add(new PurchaseRequisitionDeliverySchedule()
                        .setScheduleId(inputSchedule.getScheduleId()).setTenantId(tenantId)
                        .setRequisitionId(command.getRequisitionId()).setLineId(inputLine.getLineId())
                        .setScheduleNumber(inputSchedule.getScheduleNumber())
                        .setCanonicalWarehouseId(inputSchedule.getCanonicalWarehouseId())
                        .setRequiredDeliveryDate(inputSchedule.getRequiredDeliveryDate())
                        .setScheduledQuantity(inputSchedule.getScheduledQuantity())
                        .setCreatedAt(now).setUpdatedAt(now));
            }
        }
        PurchaseRequisition header = new PurchaseRequisition()
                .setRequisitionId(command.getRequisitionId()).setTenantId(tenantId)
                .setRequisitionCode(command.getRequisitionCode())
                .setSourceBusinessType(command.getSourceBusinessType())
                .setSourceBusinessRef(command.getSourceBusinessRef()).setStatus("APPROVED")
                .setRequestedByPrincipalId(actorPrincipalId).setApprovedByPrincipalId(actorPrincipalId)
                .setReasonCode(command.getReasonCode()).setRemark(command.getRemark()).setVersion(1L)
                .setRequestedAt(now).setApprovedAt(now).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertPurchaseRequisition(header) == 1, "failed to persist purchase requisition");
        require(mapper.insertPurchaseRequisitionLines(lines) == lines.size(),
                "failed to persist purchase requisition lines");
        require(mapper.insertPurchaseRequisitionSchedules(schedules) == schedules.size(),
                "failed to persist purchase requisition schedules");
        require(mapper.insertPurchaseRequisitionStatusHistory(new PurchaseRequisitionStatusHistory()
                .setTenantId(tenantId).setRequisitionId(header.getRequisitionId()).setOperationId(operationId)
                .setAggregateVersion(1L).setStatus("APPROVED").setActorPrincipalId(actorPrincipalId)
                .setReasonCode(header.getReasonCode()).setOccurredAt(now).setCreatedAt(now)) == 1,
                "failed to persist purchase requisition status history");

        appendEvent(tenantId, command, header, lines, schedules);
        PurchaseRequisitionResult result = PurchaseRequisitionResult.builder()
                .operationId(operationId).aggregateType(AGGREGATE_TYPE)
                .requisitionId(header.getRequisitionId()).requisitionCode(header.getRequisitionCode())
                .aggregateVersion(1L).status("APPROVED").build();
        require(mapper.markOperationSucceeded(operationId, tenantId, AGGREGATE_TYPE,
                        header.getRequisitionId(), JsonUtils.toJsonString(result), now) == 1,
                "purchase requisition operation completion conflict");
        return result;
    }

    private void appendEvent(Long tenantId, PurchaseRequisitionCommand command, PurchaseRequisition header,
                             List<PurchaseRequisitionLine> lines,
                             List<PurchaseRequisitionDeliverySchedule> schedules) {
        Map<String, List<PurchaseRequisitionDeliverySchedule>> schedulesByLine = new LinkedHashMap<>();
        schedules.forEach(schedule -> schedulesByLine.computeIfAbsent(schedule.getLineId(), ignored -> new ArrayList<>())
                .add(schedule));
        List<Map<String, Object>> linePayloads = new ArrayList<>();
        for (PurchaseRequisitionLine line : lines) {
            List<Map<String, Object>> schedulePayloads = schedulesByLine.getOrDefault(line.getLineId(), List.of())
                    .stream().map(schedule -> Map.<String, Object>of(
                            "schedule_id", schedule.getScheduleId(),
                            "schedule_number", schedule.getScheduleNumber(),
                            "canonical_warehouse_id", schedule.getCanonicalWarehouseId(),
                            "required_delivery_date", schedule.getRequiredDeliveryDate().toString(),
                            "scheduled_quantity", schedule.getScheduledQuantity()))
                    .toList();
            linePayloads.add(Map.of(
                    "line_id", line.getLineId(), "line_number", line.getLineNumber(),
                    "canonical_sku_id", line.getCanonicalSkuId(),
                    "requested_quantity", line.getRequestedQuantity(), "uom_code", line.getUomCode(),
                    "delivery_schedules", schedulePayloads));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requisition_id", header.getRequisitionId());
        payload.put("requisition_code", header.getRequisitionCode());
        payload.put("source_business_type", header.getSourceBusinessType());
        payload.put("source_business_ref", header.getSourceBusinessRef());
        payload.put("status", header.getStatus());
        payload.put("reason_code", header.getReasonCode());
        payload.put("lines", linePayloads);
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("procurement.purchase_requisition.approved").schemaVersion(1)
                .sourceSystem("cloudmold-procurement").tenantId(tenantId)
                .aggregateType(AGGREGATE_TYPE).aggregateId(header.getRequisitionId())
                .aggregateVersion(header.getVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).traceId(command.getRunId())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":1").payload(payload)
                .headers(Map.of("run_id", command.getRunId(), "status", header.getStatus()))
                .destination("lakehouse").build());
    }

    private static void validate(PurchaseRequisitionCommand command, String actorPrincipalId) {
        require(command != null, "purchase requisition command is required");
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireRef(command.getRequisitionId(), "requisitionId", 128);
        requireRef(command.getRequisitionCode(), "requisitionCode", 64);
        requireRef(command.getSourceBusinessType(), "sourceBusinessType", 64);
        requireRef(command.getSourceBusinessRef(), "sourceBusinessRef", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        require(SAFE_CODE.matcher(command.getReasonCode()).matches(), "reasonCode must be an uppercase code");
        require(command.getLines() != null && !command.getLines().isEmpty() && command.getLines().size() <= 200,
                "purchase requisition must contain between 1 and 200 lines");
        Set<String> lineIds = new LinkedHashSet<>();
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        for (PurchaseRequisitionCommand.LineDefinition line : command.getLines()) {
            requireRef(line.getLineId(), "lineId", 128);
            require(lineIds.add(line.getLineId()), "duplicate purchase requisition lineId");
            require(line.getLineNumber() != null && line.getLineNumber() > 0 && lineNumbers.add(line.getLineNumber()),
                    "lineNumber must be positive and unique");
            requireRef(line.getCanonicalSkuId(), "canonicalSkuId", 128);
            requireQuantity(line.getRequestedQuantity(), "requestedQuantity");
            require(line.getUomCode() != null && SAFE_CODE.matcher(line.getUomCode()).matches(),
                    "uomCode must be an uppercase code");
            require(line.getSchedules() != null && !line.getSchedules().isEmpty()
                            && line.getSchedules().size() <= 50,
                    "each purchase requisition line must contain between 1 and 50 delivery schedules");
            Set<String> scheduleIds = new LinkedHashSet<>();
            Set<Integer> scheduleNumbers = new LinkedHashSet<>();
            BigDecimal scheduledTotal = BigDecimal.ZERO;
            for (PurchaseRequisitionCommand.DeliveryScheduleDefinition schedule : line.getSchedules()) {
                requireRef(schedule.getScheduleId(), "scheduleId", 128);
                require(scheduleIds.add(schedule.getScheduleId()), "duplicate scheduleId within requisition line");
                require(schedule.getScheduleNumber() != null && schedule.getScheduleNumber() > 0
                                && scheduleNumbers.add(schedule.getScheduleNumber()),
                        "scheduleNumber must be positive and unique within requisition line");
                requireRef(schedule.getCanonicalWarehouseId(), "canonicalWarehouseId", 128);
                require(schedule.getRequiredDeliveryDate() != null, "requiredDeliveryDate is required");
                requireQuantity(schedule.getScheduledQuantity(), "scheduledQuantity");
                scheduledTotal = scheduledTotal.add(schedule.getScheduledQuantity());
            }
            require(scheduledTotal.compareTo(line.getRequestedQuantity()) == 0,
                    "delivery schedule quantities must equal requestedQuantity");
        }
    }

    private static void requireQuantity(BigDecimal value, String field) {
        require(value != null && value.signum() > 0 && value.scale() <= 6 && value.precision() <= 24,
                field + " must be a positive DECIMAL(24,6)");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
