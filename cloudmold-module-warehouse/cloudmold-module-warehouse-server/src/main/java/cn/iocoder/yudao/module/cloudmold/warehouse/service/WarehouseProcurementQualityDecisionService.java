package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDisposition;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ProcurementQualityEffectDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ScheduleFulfillmentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundOperationMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundStatusHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ProcurementQualityEffectMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ScheduleFulfillmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseProcurementQualityDecisionService implements WarehouseProcurementQualityDecisionApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InboundOperationMapper operationMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final ScheduleFulfillmentMapper scheduleFulfillmentMapper;
    private final ProcurementQualityEffectMapper qualityEffectMapper;
    private final InboundStatusHistoryMapper historyMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseProcurementQualityDecisionResult execute(WarehouseProcurementQualityDecisionCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                "PROCUREMENT_QUALITY_DECISION", requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve warehouse quality operation");
        InboundOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "warehouse quality operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different quality payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing warehouse quality operation is not complete");
            WarehouseProcurementQualityDecisionResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), WarehouseProcurementQualityDecisionResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        ReceiptDO receipt = receiptMapper.selectForUpdate(tenantId, command.getReceiptId());
        require(receipt != null, "procurement receipt not found");
        ReceiptLineDO line = receiptLineMapper.selectForUpdate(tenantId, command.getReceiptLineId());
        require(line != null && line.getReceiptId().equals(receipt.getReceiptId()),
                "procurement receipt line not found in receipt");
        require(line.getProcurementOrderId().equals(command.getProcurementOrderId()), "procurementOrderId mismatch");
        require(line.getProcurementOrderItemId().equals(command.getProcurementOrderItemId()),
                "procurementOrderItemId mismatch");
        require(line.getDeliveryScheduleId().equals(command.getDeliveryScheduleId()), "deliveryScheduleId mismatch");
        require(line.getWarehouseId().equals(command.getWarehouseId()), "warehouseId mismatch");
        require(line.getReceiptLocationId().equals(command.getLocationId()), "locationId mismatch");
        require(Objects.equals(line.getLotId(), command.getLotId()), "lotId mismatch");
        require(line.getBaseUomCode().equals(command.getBaseUomCode()), "baseUomCode mismatch");
        require(line.getFinanceReceiptEvidenceOperationId() != null
                        && line.getFinanceReceiptEvidenceId() != null
                        && line.getFinanceReceiptEvidenceVersion() != null,
                "finance receipt-line evidence reference is missing");

        ScheduleFulfillmentDO schedule = scheduleFulfillmentMapper.selectForUpdate(tenantId,
                line.getProcurementOrderItemId(), line.getDeliveryScheduleId());
        require(schedule != null && schedule.getProcurementOrderId().equals(line.getProcurementOrderId()),
                "procurement schedule fulfillment not found");
        BigDecimal quantity = scaledPositive(command.getQuantity(), "quantity");
        BigDecimal pendingAfter = scaled(line.getPendingQualityQuantity()).subtract(quantity);
        require(pendingAfter.signum() >= 0, "quality disposition exceeds pending quantity");
        BigDecimal acceptedAfter = scaled(line.getAcceptedQuantity());
        BigDecimal rejectedAfter = scaled(line.getRejectedQuantity());
        BigDecimal quarantinedAfter = scaled(line.getQuarantinedQuantity());
        switch (command.getDisposition()) {
            case ACCEPTED -> acceptedAfter = acceptedAfter.add(quantity);
            case REJECTED -> rejectedAfter = rejectedAfter.add(quantity);
            case QUARANTINED -> quarantinedAfter = quarantinedAfter.add(quantity);
        }
        String lineStatus = deriveLineStatus(line.getReceivedQuantity(), pendingAfter, acceptedAfter,
                rejectedAfter, quarantinedAfter, line.getCumulativePutawayQuantity());
        require(receiptLineMapper.updateQualityCas(tenantId, line.getReceiptLineId(), line.getVersion(), pendingAfter,
                acceptedAfter, rejectedAfter, quarantinedAfter, lineStatus, command.getQualityDecisionId(), now) == 1,
                "procurement receipt line quality version conflict");

        BigDecimal schedulePendingAfter = scaled(schedule.getPendingQualityQuantity()).subtract(quantity);
        require(schedulePendingAfter.signum() >= 0, "schedule quality disposition exceeds pending quantity");
        BigDecimal scheduleAcceptedAfter = scaled(schedule.getAcceptedQuantity());
        BigDecimal scheduleRejectedAfter = scaled(schedule.getRejectedQuantity());
        BigDecimal scheduleQuarantinedAfter = scaled(schedule.getQuarantinedQuantity());
        switch (command.getDisposition()) {
            case ACCEPTED -> scheduleAcceptedAfter = scheduleAcceptedAfter.add(quantity);
            case REJECTED -> scheduleRejectedAfter = scheduleRejectedAfter.add(quantity);
            case QUARANTINED -> scheduleQuarantinedAfter = scheduleQuarantinedAfter.add(quantity);
        }
        require(scheduleFulfillmentMapper.updateQualityCas(tenantId, schedule.getScheduleFulfillmentId(),
                schedule.getVersion(), schedulePendingAfter, scheduleAcceptedAfter, scheduleRejectedAfter,
                scheduleQuarantinedAfter, now) == 1, "procurement schedule quality version conflict");

        List<ReceiptLineDO> aggregateLines = receiptLineMapper.selectByReceipt(tenantId, receipt.getReceiptId());
        ReceiptLineDO updatedLine = aggregateLines.stream()
                .filter(candidate -> candidate.getReceiptLineId().equals(line.getReceiptLineId()))
                .findFirst().orElse(null);
        if (updatedLine == null || Objects.equals(updatedLine.getVersion(), line.getVersion())) {
            updatedLine = new ReceiptLineDO().setReceiptLineId(line.getReceiptLineId())
                    .setReceivedQuantity(line.getReceivedQuantity()).setPendingQualityQuantity(pendingAfter)
                    .setAcceptedQuantity(acceptedAfter).setRejectedQuantity(rejectedAfter)
                    .setQuarantinedQuantity(quarantinedAfter)
                    .setCumulativePutawayQuantity(line.getCumulativePutawayQuantity()).setQualityStatus(lineStatus)
                    .setVersion(line.getVersion() + 1);
            ReceiptLineDO replacementLine = updatedLine;
            aggregateLines = aggregateLines.stream()
                    .map(candidate -> candidate.getReceiptLineId().equals(line.getReceiptLineId()) ? replacementLine : candidate)
                    .toList();
        }
        String receiptStatus = deriveReceiptStatus(aggregateLines);
        require(receiptMapper.updateStatusCas(tenantId, receipt.getReceiptId(), receipt.getVersion(), receiptStatus, now) == 1,
                "procurement receipt quality version conflict");

        ProcurementQualityEffectDO effect = new ProcurementQualityEffectDO()
                .setEffectId(UUID.randomUUID().toString()).setTenantId(tenantId).setOperationId(operationId)
                .setSourceEventId(command.getSourceEventId()).setQualityDecisionId(command.getQualityDecisionId())
                .setDecisionVersion(command.getDecisionVersion()).setInspectionSplitId(command.getInspectionSplitId())
                .setDisposition(command.getDisposition().name()).setReceiptId(receipt.getReceiptId())
                .setReceiptLineId(line.getReceiptLineId()).setProcurementOrderId(line.getProcurementOrderId())
                .setProcurementOrderItemId(line.getProcurementOrderItemId())
                .setDeliveryScheduleId(line.getDeliveryScheduleId()).setDispositionQuantity(quantity)
                .setPendingQuantityBefore(line.getPendingQualityQuantity()).setPendingQuantityAfter(pendingAfter)
                .setAcceptedQuantityBefore(line.getAcceptedQuantity()).setAcceptedQuantityAfter(acceptedAfter)
                .setRejectedQuantityBefore(line.getRejectedQuantity()).setRejectedQuantityAfter(rejectedAfter)
                .setQuarantinedQuantityBefore(line.getQuarantinedQuantity()).setQuarantinedQuantityAfter(quarantinedAfter)
                .setReceiptVersionBefore(receipt.getVersion()).setReceiptVersionAfter(receipt.getVersion() + 1)
                .setReceiptLineVersionBefore(line.getVersion()).setReceiptLineVersionAfter(line.getVersion() + 1)
                .setScheduleFulfillmentVersionBefore(schedule.getVersion())
                .setScheduleFulfillmentVersionAfter(schedule.getVersion() + 1)
                .setEvidenceRef(command.getEvidenceRef())
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        qualityEffectMapper.insert(effect);
        insertHistory(tenantId, operationId, "RECEIPT_LINE", line.getReceiptLineId(), lineStatus,
                line.getVersion() + 1, "QUALITY_" + command.getDisposition().name(),
                "质检处置已回写收货行", effect.getEffectId(), now);
        insertHistory(tenantId, operationId, "SCHEDULE_FULFILLMENT", schedule.getScheduleFulfillmentId(),
                command.getDisposition().name(), schedule.getVersion() + 1,
                "QUALITY_" + command.getDisposition().name(), "质检处置已回写采购交期", effect.getEffectId(), now);
        insertHistory(tenantId, operationId, "RECEIPT", receipt.getReceiptId(), receiptStatus,
                receipt.getVersion() + 1, "QUALITY_AGGREGATED", "收货质检状态已聚合", effect.getEffectId(), now);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("quality_effect_id", effect.getEffectId());
        eventPayload.put("quality_decision_id", command.getQualityDecisionId());
        eventPayload.put("decision_version", command.getDecisionVersion());
        eventPayload.put("inspection_split_id", command.getInspectionSplitId());
        eventPayload.put("disposition", command.getDisposition().name());
        eventPayload.put("receipt_id", receipt.getReceiptId());
        eventPayload.put("receipt_line_id", line.getReceiptLineId());
        eventPayload.put("procurement_order_id", line.getProcurementOrderId());
        eventPayload.put("procurement_order_item_id", line.getProcurementOrderItemId());
        eventPayload.put("delivery_schedule_id", line.getDeliveryScheduleId());
        eventPayload.put("quantity", quantity);
        eventPayload.put("pending_quality_quantity", pendingAfter);
        eventPayload.put("accepted_quantity", acceptedAfter);
        eventPayload.put("rejected_quantity", rejectedAfter);
        eventPayload.put("quarantined_quantity", quarantinedAfter);
        eventPayload.put("receipt_status", receiptStatus);
        eventPayload.put("receipt_line_status", lineStatus);
        eventPayload.put("evidence_ref", command.getEvidenceRef());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString()).eventType("warehouse.procurement_receipt.quality_applied")
                .schemaVersion(1).sourceSystem("cloudmold-warehouse").tenantId(tenantId)
                .aggregateType("procurement_receipt").aggregateId(receipt.getReceiptId())
                .aggregateVersion(receipt.getVersion() + 1).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey() + ":event")
                .payload(eventPayload).headers(Map.of("operation", "PROCUREMENT_QUALITY_DECISION"))
                .destination("lakehouse").build());

        WarehouseProcurementQualityDecisionResult result = WarehouseProcurementQualityDecisionResult.builder()
                .operationId(operationId).receiptId(receipt.getReceiptId()).receiptLineId(line.getReceiptLineId())
                .receiptVersion(receipt.getVersion() + 1).receiptLineVersion(line.getVersion() + 1)
                .scheduleFulfillmentVersion(schedule.getVersion() + 1)
                .financeReceiptEvidenceOperationId(line.getFinanceReceiptEvidenceOperationId())
                .financeReceiptEvidenceId(line.getFinanceReceiptEvidenceId())
                .financeReceiptEvidenceVersion(line.getFinanceReceiptEvidenceVersion())
                .receiptStatus(receiptStatus)
                .receiptLineStatus(lineStatus).pendingQualityQuantity(pendingAfter).acceptedQuantity(acceptedAfter)
                .rejectedQuantity(rejectedAfter).quarantinedQuantity(quarantinedAfter).duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, receipt.getReceiptId(),
                JsonUtils.toJsonString(result), now) == 1, "warehouse quality operation completion conflict");
        return result;
    }

    static String deriveLineStatus(BigDecimal received, BigDecimal pending, BigDecimal accepted,
                                   BigDecimal rejected, BigDecimal quarantined, BigDecimal putaway) {
        BigDecimal receivedValue = scaled(received);
        BigDecimal pendingValue = scaled(pending);
        BigDecimal acceptedValue = scaled(accepted);
        BigDecimal rejectedValue = scaled(rejected);
        BigDecimal quarantinedValue = scaled(quarantined);
        BigDecimal putawayValue = scaled(putaway);
        require(receivedValue.compareTo(pendingValue.add(acceptedValue).add(rejectedValue).add(quarantinedValue)) == 0,
                "receipt line quality quantities do not conserve received quantity");
        require(putawayValue.signum() >= 0 && putawayValue.compareTo(acceptedValue) <= 0,
                "putaway quantity exceeds accepted quantity");
        if (putawayValue.signum() > 0) {
            return pendingValue.signum() == 0 && putawayValue.compareTo(acceptedValue) == 0
                    ? "PUTAWAY_COMPLETED" : "PARTIALLY_PUTAWAY";
        }
        if (pendingValue.signum() > 0) {
            return pendingValue.compareTo(receivedValue) == 0 ? "PENDING_QUALITY" : "PARTIAL_QUALITY_DECIDED";
        }
        int nonZeroDispositions = (acceptedValue.signum() > 0 ? 1 : 0)
                + (rejectedValue.signum() > 0 ? 1 : 0) + (quarantinedValue.signum() > 0 ? 1 : 0);
        if (nonZeroDispositions > 1) return "QUALITY_MIXED";
        if (acceptedValue.signum() > 0) return "QUALITY_ACCEPTED";
        if (rejectedValue.signum() > 0) return "QUALITY_REJECTED";
        return "QUALITY_QUARANTINED";
    }

    static String deriveReceiptStatus(List<ReceiptLineDO> lines) {
        require(lines != null && !lines.isEmpty(), "receipt has no lines");
        BigDecimal pending = sum(lines, ReceiptLineDO::getPendingQualityQuantity);
        BigDecimal accepted = sum(lines, ReceiptLineDO::getAcceptedQuantity);
        BigDecimal rejected = sum(lines, ReceiptLineDO::getRejectedQuantity);
        BigDecimal quarantined = sum(lines, ReceiptLineDO::getQuarantinedQuantity);
        BigDecimal putaway = sum(lines, ReceiptLineDO::getCumulativePutawayQuantity);
        if (putaway.signum() > 0) {
            return pending.signum() == 0 && putaway.compareTo(accepted) == 0
                    ? "PUTAWAY_COMPLETED" : "PARTIALLY_PUTAWAY";
        }
        if (pending.signum() > 0) {
            BigDecimal received = sum(lines, ReceiptLineDO::getReceivedQuantity);
            return pending.compareTo(received) == 0 ? "PENDING_QUALITY" : "PARTIAL_QUALITY_DECIDED";
        }
        int nonZeroDispositions = (accepted.signum() > 0 ? 1 : 0)
                + (rejected.signum() > 0 ? 1 : 0) + (quarantined.signum() > 0 ? 1 : 0);
        if (nonZeroDispositions > 1) return "QUALITY_MIXED";
        if (accepted.signum() > 0) return "QUALITY_ACCEPTED";
        if (rejected.signum() > 0) return "QUALITY_REJECTED";
        return "QUALITY_QUARANTINED";
    }

    private static BigDecimal sum(List<ReceiptLineDO> lines,
                                  java.util.function.Function<ReceiptLineDO, BigDecimal> getter) {
        return lines.stream().map(getter).map(WarehouseProcurementQualityDecisionService::scaled)
                .reduce(ZERO, BigDecimal::add);
    }

    private void insertHistory(Long tenantId, Long operationId, String objectType, String objectId,
                               String status, Long version, String stageCode, String stageLabel,
                               String remark, LocalDateTime now) {
        historyMapper.insert(new InboundStatusHistoryDO().setHistoryId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setOperationId(operationId).setBusinessObjectType(objectType)
                .setBusinessObjectId(objectId).setStatus(status).setStatusVersion(version)
                .setStageCode(stageCode).setStageLabel(stageLabel).setRemark(remark)
                .setChangedAt(now).setCreatedAt(now));
    }

    private static void validate(WarehouseProcurementQualityDecisionCommand command) {
        require(command != null && command.getDisposition() != null, "quality disposition is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getSourceEventId(), "sourceEventId", 128);
        requireText(command.getQualityDecisionId(), "qualityDecisionId", 128);
        require(command.getDecisionVersion() != null && command.getDecisionVersion() > 0,
                "decisionVersion is required");
        requireText(command.getInspectionSplitId(), "inspectionSplitId", 128);
        requireText(command.getReceiptId(), "receiptId", 128);
        requireText(command.getReceiptLineId(), "receiptLineId", 128);
        requireText(command.getProcurementOrderId(), "procurementOrderId", 128);
        requireText(command.getProcurementOrderItemId(), "procurementOrderItemId", 128);
        requireText(command.getDeliveryScheduleId(), "deliveryScheduleId", 128);
        requireText(command.getWarehouseId(), "warehouseId", 128);
        requireText(command.getLocationId(), "locationId", 128);
        requireText(command.getBaseUomCode(), "baseUomCode", 64);
        scaledPositive(command.getQuantity(), "quantity");
        requireText(command.getEvidenceRef(), "evidenceRef", 255);
        requireText(command.getCorrelationId(), "correlationId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static BigDecimal scaledPositive(BigDecimal value, String field) {
        require(value != null && value.signum() > 0 && value.scale() <= 6 && value.precision() <= 24,
                field + " must be a positive DECIMAL(24,6)");
        return value.setScale(6);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6);
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
