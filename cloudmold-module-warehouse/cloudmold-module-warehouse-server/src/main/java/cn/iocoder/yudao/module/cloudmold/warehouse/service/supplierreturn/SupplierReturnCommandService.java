package cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.supplierreturn.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.SupplierReturnReferenceMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.SupplierReturnStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SupplierReturnCommandService implements SupplierReturnCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final BigDecimal ZERO = new BigDecimal("0.000000");
    static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    static final String CREATED_EVENT = "supplier_return.created";
    static final String SUBMITTED_EVENT = "supplier_return.submitted";
    static final String APPROVED_EVENT = "supplier_return.approved";
    static final String DISPATCHED_EVENT = "supplier_return.dispatched";
    static final String COMPLETED_EVENT = "supplier_return.completed";
    static final String CANCELLED_EVENT = "supplier_return.cancelled";

    private final SupplierReturnStoreMapper mapper;
    private final SupplierReturnReferenceMapper referenceMapper;
    private final ProcurementQueryApi procurementQueryApi;
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi;
    private final InventoryProcurementReceiptApi inventoryProcurementReceiptApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierReturnResult execute(SupplierReturnCommand command, String actorPrincipalId) {
        validateEnvelope(command, actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = nextAttemptToken();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), text(command.getSourceEventId()),
                command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve supplier return operation");
        SupplierReturnOperationDO operation = requireNonNull(
                mapper.selectOperationForUpdate(tenantId, operationId), "supplier return operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different supplier return payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && StringUtils.hasText(operation.getResultJson()),
                    "existing supplier return operation is incomplete");
            SupplierReturnResult replay = JsonUtils.parseObject(operation.getResultJson(), SupplierReturnResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        SupplierReturnResult result = switch (command.getOperation()) {
            case CREATE_DRAFT -> createDraft(tenantId, operationId, command, actorPrincipalId, now);
            case SUBMIT -> transitionStatus(tenantId, operationId, command, now, null, null, actorPrincipalId);
            case APPROVE -> transitionStatus(tenantId, operationId, command, now, null, null, actorPrincipalId);
            case COMPLETE -> transitionStatus(tenantId, operationId, command, now, null, null, actorPrincipalId);
            case CANCEL -> transitionStatus(tenantId, operationId, command, now,
                    requireNonNull(command.getCancel(), "cancel is required").getReasonCode(),
                    requireNonNull(command.getCancel(), "cancel is required").getRemark(),
                    actorPrincipalId);
            case DISPATCH -> dispatch(tenantId, operationId, command, actorPrincipalId, now);
        };
        result.setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, result.getReturnId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "supplier return operation completion conflict");
        return result;
    }

    private SupplierReturnResult createDraft(Long tenantId, Long operationId, SupplierReturnCommand command,
                                             String actorPrincipalId, LocalDateTime now) {
        SupplierReturnCommand.CreateDefinition input = requireNonNull(command.getCreate(), "create is required");
        requireRef(input.getPurchaseOrderId(), "purchaseOrderId", 128);
        requireRef(input.getReceiptId(), "receiptId", 128);
        List<SupplierReturnCommand.LineDefinition> definitions = requireNonEmpty(input.getLines(), "lines");
        ProcurementOrderView order = requireNonNull(procurementQueryApi.requireCurrent(input.getPurchaseOrderId()),
                "procurement order not found");
        require(text(order.getOrderId()).equals(input.getPurchaseOrderId()), "procurement order id mismatch");

        String returnId = valueOrUuid(input.getReturnId());
        String returnCode = valueOrCode(input.getReturnCode(), "SRTRN");
        require(mapper.selectDocument(tenantId, returnId) == null, "supplier return already exists");

        HeaderSnapshot snapshot = null;
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        Set<String> qualityKeys = new LinkedHashSet<>();
        List<SupplierReturnLineDO> lines = new ArrayList<>();
        int autoLineNumber = 10;
        for (SupplierReturnCommand.LineDefinition definition : definitions) {
            require(definition.getSourceDisposition() != null, "sourceDisposition is required");
            SupplierReturnReferenceMapper.QualityDecisionReference qualityRef =
                    requireNonNull(referenceMapper.selectQualityDecisionReference(tenantId,
                                    requireText(definition.getQualityDecisionId(), "qualityDecisionId"),
                                    requirePositive(definition.getDecisionVersion(), "decisionVersion")),
                            "quality decision not found");
            SupplierReturnReferenceMapper.ReceiptLineReference receiptRef =
                    requireNonNull(referenceMapper.selectReceiptLineReference(tenantId, qualityRef.getReceiptLineId()),
                            "receipt line not found");
            if (snapshot == null) {
                snapshot = new HeaderSnapshot(receiptRef.getReceiptId(), qualityRef.getPurchaseOrderId(),
                        qualityRef.getSupplierId(), qualityRef.getOwnerType(), qualityRef.getOwnerId(),
                        qualityRef.getWarehouseId());
            }
            validateHeaderConsistency(snapshot, input, order, receiptRef, qualityRef);
            BigDecimal quantity = normalizeQuantity(definition.getReturnQuantity());
            BigDecimal available = availableQuantity(qualityRef, definition.getSourceDisposition());
            BigDecimal allocated = scale(mapper.sumActiveReturnQuantityByQualityKeyExcludingReturn(tenantId,
                    qualityRef.getQualityDecisionId(), qualityRef.getDecisionVersion(), qualityRef.getInspectionSplitId(),
                    definition.getSourceDisposition().name(), null));
            require(allocated.add(quantity).compareTo(available) <= 0,
                    "supplier return quantity exceeds quality decision balance");
            Integer lineNumber = definition.getLineNumber() == null ? autoLineNumber : definition.getLineNumber();
            require(lineNumbers.add(lineNumber), "duplicate supplier return lineNumber");
            String qualityKey = qualityRef.getQualityDecisionId() + "|" + qualityRef.getDecisionVersion() + "|"
                    + qualityRef.getInspectionSplitId() + "|" + definition.getSourceDisposition().name();
            require(qualityKeys.add(qualityKey), "duplicate quality decision/disposition line");
            lines.add(new SupplierReturnLineDO()
                    .setReturnLineId(valueOrRef(definition.getReturnLineId(), returnId + ":line:" + lineNumber))
                    .setTenantId(tenantId)
                    .setReturnId(returnId)
                    .setLineNumber(lineNumber)
                    .setReceiptLineId(qualityRef.getReceiptLineId())
                    .setPurchaseOrderItemId(qualityRef.getPurchaseOrderItemId())
                    .setPurchaseOrderScheduleId(qualityRef.getPurchaseOrderScheduleId())
                    .setQualityDecisionId(qualityRef.getQualityDecisionId())
                    .setDecisionVersion(qualityRef.getDecisionVersion())
                    .setInspectionSplitId(qualityRef.getInspectionSplitId())
                    .setSourceDisposition(definition.getSourceDisposition().name())
                    .setCanonicalSkuId(qualityRef.getCanonicalSkuId())
                    .setWarehouseId(qualityRef.getWarehouseId())
                    .setLocationId(qualityRef.getLocationId())
                    .setLotId(qualityRef.getLotId())
                    .setReturnQuantity(quantity)
                    .setDispatchedQuantity(ZERO)
                    .setOutstandingQuantity(quantity)
                    .setUomCode(qualityRef.getUomCode())
                    .setValuationPolicyId(qualityRef.getValuationPolicyId())
                    .setValuationPolicyVersion(qualityRef.getValuationPolicyVersion())
                    .setValuationPolicyHash(qualityRef.getValuationPolicyHash())
                    .setUnitCostAmountMinor(qualityRef.getUnitCostAmountMinor())
                    .setCurrencyCode(qualityRef.getCurrencyCode())
                    .setQualityEvidenceRef(qualityRef.getEvidenceRef())
                    .setRemark(text(definition.getRemark()))
                    .setStatus("DRAFT")
                    .setVersion(1L)
                    .setCreatedAt(now)
                    .setUpdatedAt(now));
            autoLineNumber += 10;
        }
        require(snapshot != null, "supplier return lines are required");
        warehouseReferenceValidationApi.requireActiveWarehouse(snapshot.warehouseId());

        SupplierReturnDocumentDO document = new SupplierReturnDocumentDO()
                .setReturnId(returnId).setTenantId(tenantId).setReturnCode(returnCode)
                .setPurchaseOrderId(snapshot.purchaseOrderId()).setReceiptId(snapshot.receiptId())
                .setSupplierId(snapshot.supplierId()).setOwnerType(snapshot.ownerType()).setOwnerId(snapshot.ownerId())
                .setWarehouseId(snapshot.warehouseId()).setReasonCode(requireText(input.getReasonCode(), "reasonCode"))
                .setRemark(text(input.getRemark())).setStatus("DRAFT").setCreatedByPrincipalId(actorPrincipalId)
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertDocument(document) == 1, "failed to persist supplier return");
        for (SupplierReturnLineDO line : lines) {
            require(mapper.insertLine(line) == 1, "failed to persist supplier return line");
            insertHistory(tenantId, operationId, "LINE", line.getReturnLineId(), line.getStatus(), line.getVersion(),
                    stageForStatus(line.getStatus()).code(), stageForStatus(line.getStatus()).label(),
                    line.getRemark(), now);
        }
        insertHistory(tenantId, operationId, "RETURN", returnId, "DRAFT", 1L,
                stageForStatus("DRAFT").code(), stageForStatus("DRAFT").label(), document.getRemark(), now);
        appendEvent(tenantId, command, CREATED_EVENT, returnId, document.getVersion(),
                Map.of("return_id", returnId, "return_code", returnCode, "status", document.getStatus(),
                        "purchase_order_id", document.getPurchaseOrderId(), "receipt_id", document.getReceiptId(),
                        "line_count", lines.size()));
        return SupplierReturnResult.builder()
                .returnId(returnId).returnCode(returnCode).status("DRAFT").aggregateVersion(1L)
                .currentStageCode(stageForStatus("DRAFT").code()).currentStageLabel(stageForStatus("DRAFT").label())
                .processedLineCount(lines.size()).duplicate(false).build();
    }

    private SupplierReturnResult transitionStatus(Long tenantId, Long operationId, SupplierReturnCommand command,
                                                  LocalDateTime now, String overrideReasonCode,
                                                  String overrideRemark, String actorPrincipalId) {
        String returnId = requireText(command.getReturnId(), "returnId");
        SupplierReturnDocumentDO document = requireNonNull(mapper.selectDocumentForUpdate(tenantId, returnId),
                "supplier return not found");
        requirePositive(command.getExpectedVersion(), "expectedVersion");
        require(document.getVersion().equals(command.getExpectedVersion()), "supplier return version conflict");
        List<SupplierReturnLineDO> lines = mapper.selectLines(tenantId, returnId);
        Stage nextStage;
        String nextStatus;
        switch (command.getOperation()) {
            case SUBMIT -> {
                require("DRAFT".equals(document.getStatus()), "only draft supplier returns can be submitted");
                validateCurrentAvailability(tenantId, returnId, lines);
                for (SupplierReturnLineDO current : lines) {
                    SupplierReturnLineDO locked = requireNonNull(
                            mapper.selectLineForUpdate(tenantId, returnId, current.getReturnLineId()),
                            "supplier return line disappeared");
                    require(mapper.updateLineStatusCas(tenantId, locked.getReturnLineId(), locked.getVersion(),
                                    locked.getVersion() + 1, "SUBMITTED", now) == 1,
                            "supplier return line submit conflict");
                    insertHistory(tenantId, operationId, "LINE", locked.getReturnLineId(), "SUBMITTED",
                            locked.getVersion() + 1, stageForStatus("SUBMITTED").code(),
                            stageForStatus("SUBMITTED").label(), locked.getRemark(), now);
                }
                nextStatus = "SUBMITTED";
                nextStage = stageForStatus(nextStatus);
            }
            case APPROVE -> {
                require("SUBMITTED".equals(document.getStatus()), "only submitted supplier returns can be approved");
                validateCurrentAvailability(tenantId, returnId, lines);
                for (SupplierReturnLineDO current : lines) {
                    SupplierReturnLineDO locked = requireNonNull(
                            mapper.selectLineForUpdate(tenantId, returnId, current.getReturnLineId()),
                            "supplier return line disappeared");
                    require(mapper.updateLineStatusCas(tenantId, locked.getReturnLineId(), locked.getVersion(),
                                    locked.getVersion() + 1, "APPROVED", now) == 1,
                            "supplier return line approve conflict");
                    insertHistory(tenantId, operationId, "LINE", locked.getReturnLineId(), "APPROVED",
                            locked.getVersion() + 1, stageForStatus("APPROVED").code(),
                            stageForStatus("APPROVED").label(), locked.getRemark(), now);
                }
                nextStatus = "APPROVED";
                nextStage = stageForStatus(nextStatus);
            }
            case COMPLETE -> {
                require(Set.of("DISPATCHED", "PARTIALLY_DISPATCHED").contains(document.getStatus()),
                        "only dispatched supplier returns can be completed");
                require(lines.stream().allMatch(line -> scale(line.getOutstandingQuantity()).compareTo(ZERO) == 0),
                        "supplier return still has outstanding quantity");
                for (SupplierReturnLineDO current : lines) {
                    SupplierReturnLineDO locked = requireNonNull(
                            mapper.selectLineForUpdate(tenantId, returnId, current.getReturnLineId()),
                            "supplier return line disappeared");
                    require(mapper.updateLineStatusCas(tenantId, locked.getReturnLineId(), locked.getVersion(),
                                    locked.getVersion() + 1, "COMPLETED", now) == 1,
                            "supplier return line completion conflict");
                    insertHistory(tenantId, operationId, "LINE", locked.getReturnLineId(), "COMPLETED",
                            locked.getVersion() + 1, stageForStatus("COMPLETED").code(),
                            stageForStatus("COMPLETED").label(), locked.getRemark(), now);
                }
                nextStatus = "COMPLETED";
                nextStage = stageForStatus(nextStatus);
            }
            case CANCEL -> {
                require(Set.of("DRAFT", "SUBMITTED", "APPROVED").contains(document.getStatus()),
                        "dispatched supplier returns cannot be cancelled");
                for (SupplierReturnLineDO current : lines) {
                    SupplierReturnLineDO locked = requireNonNull(
                            mapper.selectLineForUpdate(tenantId, returnId, current.getReturnLineId()),
                            "supplier return line disappeared");
                    require(mapper.updateLineStatusCas(tenantId, locked.getReturnLineId(), locked.getVersion(),
                                    locked.getVersion() + 1, "CANCELLED", now) == 1,
                            "supplier return line cancel conflict");
                    insertHistory(tenantId, operationId, "LINE", locked.getReturnLineId(), "CANCELLED",
                            locked.getVersion() + 1, stageForStatus("CANCELLED").code(),
                            stageForStatus("CANCELLED").label(), text(overrideRemark), now);
                }
                nextStatus = "CANCELLED";
                nextStage = stageForStatus(nextStatus);
            }
            default -> throw new IllegalArgumentException("unsupported transition operation");
        }

        Long nextVersion = document.getVersion() + 1;
        require(mapper.updateDocumentStatusCas(tenantId, returnId, document.getVersion(), nextVersion,
                        nextStatus,
                        overrideReasonCode != null ? requireText(overrideReasonCode, "reasonCode") : document.getReasonCode(),
                        overrideRemark != null ? text(overrideRemark) : document.getRemark(),
                        command.getOperation() == SupplierReturnOperation.SUBMIT ? actorPrincipalId : document.getSubmittedByPrincipalId(),
                        command.getOperation() == SupplierReturnOperation.APPROVE ? actorPrincipalId : document.getApprovedByPrincipalId(),
                        command.getOperation() == SupplierReturnOperation.COMPLETE ? actorPrincipalId : document.getCompletedByPrincipalId(),
                        command.getOperation() == SupplierReturnOperation.CANCEL ? actorPrincipalId : document.getCancelledByPrincipalId(),
                        command.getOperation() == SupplierReturnOperation.SUBMIT ? now : document.getSubmittedAt(),
                        command.getOperation() == SupplierReturnOperation.APPROVE ? now : document.getApprovedAt(),
                        command.getOperation() == SupplierReturnOperation.COMPLETE ? now : document.getCompletedAt(),
                        command.getOperation() == SupplierReturnOperation.CANCEL ? now : document.getCancelledAt(),
                        now) == 1,
                "supplier return status transition conflict");
        insertHistory(tenantId, operationId, "RETURN", returnId, nextStatus, nextVersion,
                nextStage.code(), nextStage.label(), overrideRemark != null ? text(overrideRemark) : document.getRemark(), now);
        appendEvent(tenantId, command, switch (command.getOperation()) {
                    case SUBMIT -> SUBMITTED_EVENT;
                    case APPROVE -> APPROVED_EVENT;
                    case COMPLETE -> COMPLETED_EVENT;
                    case CANCEL -> CANCELLED_EVENT;
                    default -> throw new IllegalArgumentException("unsupported event");
                }, returnId, nextVersion,
                Map.of("return_id", returnId, "status", nextStatus, "line_count", lines.size()));
        return SupplierReturnResult.builder()
                .returnId(returnId).returnCode(document.getReturnCode()).status(nextStatus).aggregateVersion(nextVersion)
                .currentStageCode(nextStage.code()).currentStageLabel(nextStage.label())
                .processedLineCount(lines.size()).duplicate(false).build();
    }

    private SupplierReturnResult dispatch(Long tenantId, Long operationId, SupplierReturnCommand command,
                                          String actorPrincipalId, LocalDateTime now) {
        String returnId = requireText(command.getReturnId(), "returnId");
        SupplierReturnDocumentDO document = requireNonNull(mapper.selectDocumentForUpdate(tenantId, returnId),
                "supplier return not found");
        requirePositive(command.getExpectedVersion(), "expectedVersion");
        require(document.getVersion().equals(command.getExpectedVersion()), "supplier return version conflict");
        require(Set.of("APPROVED", "PARTIALLY_DISPATCHED").contains(document.getStatus()),
                "supplier return must be approved before dispatch");
        SupplierReturnCommand.DispatchBatchDefinition batchInput =
                requireNonNull(command.getDispatchBatch(), "dispatchBatch is required");
        List<SupplierReturnCommand.DispatchLineDefinition> definitions =
                requireNonEmpty(batchInput.getLines(), "dispatchBatch.lines");
        String batchId = valueOrUuid(batchInput.getBatchId());
        String batchNo = valueOrCode(batchInput.getBatchNo(), "SRDSP");
        require(mapper.insertDispatchBatch(new SupplierReturnDispatchBatchDO()
                        .setBatchId(batchId).setTenantId(tenantId).setReturnId(returnId).setBatchNo(batchNo)
                        .setStatus("COMPLETED").setDispatchedByPrincipalId(actorPrincipalId)
                        .setRemark(text(batchInput.getRemark())).setVersion(1L).setOccurredAt(now)
                        .setCreatedAt(now).setUpdatedAt(now)) == 1,
                "failed to persist supplier return dispatch batch");
        Set<String> visited = new LinkedHashSet<>();
        int processed = 0;
        for (SupplierReturnCommand.DispatchLineDefinition definition : definitions) {
            SupplierReturnLineDO line = definition.getReturnLineId() != null
                    ? requireNonNull(mapper.selectLineForUpdate(tenantId, returnId, definition.getReturnLineId()),
                    "supplier return line not found")
                    : requireNonNull(mapper.selectLineForUpdateByNumber(tenantId, returnId,
                    requirePositive(definition.getLineNumber(), "lineNumber")), "supplier return line not found");
            require(visited.add(line.getReturnLineId()), "duplicate dispatch line");
            require(Set.of("APPROVED", "PARTIALLY_DISPATCHED").contains(line.getStatus()),
                    "supplier return line is not dispatchable");
            BigDecimal dispatchQuantity = normalizeQuantity(definition.getDispatchQuantity());
            require(dispatchQuantity.compareTo(scale(line.getOutstandingQuantity())) <= 0,
                    "dispatch quantity exceeds supplier return outstanding quantity");
            long movementCost = multiplyMinor(dispatchQuantity, line.getUnitCostAmountMinor());
            InventoryProcurementReceiptResult inventory = requireNonNull(inventoryProcurementReceiptApi.execute(
                    InventoryProcurementReceiptCommand.builder()
                            .operation(InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER)
                            .disposition(dispositionOf(line.getSourceDisposition()))
                            .idempotencyKey(command.getIdempotencyKey() + ":dispatch:" + line.getReturnLineId())
                            .sourceEventId(eventId(command.getSourceEventId(), line.getReturnLineId()))
                            .receiptId(document.getReceiptId()).receiptLineId(line.getReceiptLineId())
                            .purchaseOrderId(document.getPurchaseOrderId())
                            .purchaseOrderItemId(line.getPurchaseOrderItemId())
                            .purchaseOrderScheduleId(line.getPurchaseOrderScheduleId())
                            .supplierId(document.getSupplierId()).ownerType(document.getOwnerType())
                            .ownerId(document.getOwnerId()).canonicalSkuId(line.getCanonicalSkuId())
                            .warehouseId(line.getWarehouseId()).locationId(line.getLocationId()).lotId(line.getLotId())
                            .baseUomCode(line.getUomCode()).quantity(dispatchQuantity)
                            .qualityDecisionId(line.getQualityDecisionId()).decisionVersion(line.getDecisionVersion())
                            .qualityEvidenceRef(line.getQualityEvidenceRef())
                            .valuationPolicy(line.getValuationPolicyId())
                            .valuationPolicyVersion(line.getValuationPolicyVersion())
                            .valuationPolicyHash(line.getValuationPolicyHash())
                            .unitCostAmountMinor(line.getUnitCostAmountMinor())
                            .movementCostAmountMinor(movementCost).currencyCode(line.getCurrencyCode())
                            .businessNo(document.getReturnCode()).correlationId(command.getCorrelationId())
                            .causationId(command.getCausationId()).occurredAt(command.getOccurredAt())
                            .build()), "inventory supplier return returned no result");
            verifyInventory(line, document, inventory, movementCost);
            BigDecimal nextDispatched = scale(line.getDispatchedQuantity()).add(dispatchQuantity);
            BigDecimal nextOutstanding = scale(line.getOutstandingQuantity()).subtract(dispatchQuantity);
            String lineStatus = nextOutstanding.compareTo(ZERO) == 0 ? "DISPATCHED" : "PARTIALLY_DISPATCHED";
            require(mapper.updateLineDispatchCas(tenantId, line.getReturnLineId(), line.getVersion(), line.getVersion() + 1,
                            nextDispatched, nextOutstanding, lineStatus, now) == 1,
                    "supplier return line dispatch conflict");
            require(mapper.insertDispatchLine(new SupplierReturnDispatchLineDO()
                            .setExecutionLineId(valueOrRef(definition.getExecutionLineId(),
                                    batchId + ":" + line.getReturnLineId()))
                            .setTenantId(tenantId).setBatchId(batchId).setReturnId(returnId)
                            .setReturnLineId(line.getReturnLineId()).setLineNumber(line.getLineNumber())
                            .setSourceDisposition(line.getSourceDisposition()).setDispatchedQuantity(dispatchQuantity)
                            .setCumulativeDispatchedQuantity(nextDispatched).setOutstandingQuantity(nextOutstanding)
                            .setInventoryOperationId(inventory.getOperationId())
                            .setLedgerTransactionId(inventory.getLedgerTransactionId())
                            .setSourceBalanceId(inventory.getSourceBalanceId())
                            .setInventoryAggregateVersion(inventory.getTargetAggregateVersion())
                            .setStatus(lineStatus).setRemark(text(definition.getRemark())).setVersion(1L)
                            .setOccurredAt(now).setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist supplier return dispatch line");
            insertHistory(tenantId, operationId, "LINE", line.getReturnLineId(), lineStatus, line.getVersion() + 1,
                    stageForStatus(lineStatus).code(), stageForStatus(lineStatus).label(), text(definition.getRemark()), now);
            processed++;
        }
        List<SupplierReturnLineDO> currentLines = mapper.selectLines(tenantId, returnId);
        boolean allDispatched = currentLines.stream()
                .allMatch(line -> scale(line.getOutstandingQuantity()).compareTo(ZERO) == 0);
        String nextStatus = allDispatched ? "DISPATCHED" : "PARTIALLY_DISPATCHED";
        Stage nextStage = stageForStatus(nextStatus);
        Long nextVersion = document.getVersion() + 1;
        require(mapper.updateDocumentStatusCas(tenantId, returnId, document.getVersion(), nextVersion, nextStatus,
                        document.getReasonCode(), document.getRemark(), document.getSubmittedByPrincipalId(),
                        document.getApprovedByPrincipalId(), document.getCompletedByPrincipalId(),
                        document.getCancelledByPrincipalId(), document.getSubmittedAt(), document.getApprovedAt(),
                        document.getCompletedAt(), document.getCancelledAt(), now) == 1,
                "supplier return dispatch status conflict");
        insertHistory(tenantId, operationId, "RETURN", returnId, nextStatus, nextVersion,
                nextStage.code(), nextStage.label(), batchInput.getRemark(), now);
        appendEvent(tenantId, command, DISPATCHED_EVENT, returnId, nextVersion,
                Map.of("return_id", returnId, "batch_id", batchId, "batch_no", batchNo, "status", nextStatus,
                        "processed_line_count", processed));
        return SupplierReturnResult.builder()
                .returnId(returnId).returnCode(document.getReturnCode()).status(nextStatus).aggregateVersion(nextVersion)
                .currentStageCode(nextStage.code()).currentStageLabel(nextStage.label())
                .batchId(batchId).batchNo(batchNo).processedLineCount(processed).duplicate(false).build();
    }

    private void validateCurrentAvailability(Long tenantId, String returnId, List<SupplierReturnLineDO> lines) {
        for (SupplierReturnLineDO line : lines) {
            SupplierReturnReferenceMapper.QualityDecisionReference reference = requireNonNull(
                    referenceMapper.selectQualityDecisionReference(tenantId, line.getQualityDecisionId(), line.getDecisionVersion()),
                    "quality decision not found");
            BigDecimal available = availableQuantity(reference, SupplierReturnSourceDisposition.valueOf(line.getSourceDisposition()));
            BigDecimal allocatedOther = scale(mapper.sumActiveReturnQuantityByQualityKeyExcludingReturn(tenantId,
                    line.getQualityDecisionId(), line.getDecisionVersion(), line.getInspectionSplitId(),
                    line.getSourceDisposition(), returnId));
            require(allocatedOther.add(scale(line.getReturnQuantity())).compareTo(available) <= 0,
                    "supplier return quantity exceeds current quality decision balance");
        }
    }

    private void validateHeaderConsistency(HeaderSnapshot snapshot, SupplierReturnCommand.CreateDefinition input,
                                           ProcurementOrderView order,
                                           SupplierReturnReferenceMapper.ReceiptLineReference receiptRef,
                                           SupplierReturnReferenceMapper.QualityDecisionReference qualityRef) {
        require(snapshot.receiptId().equals(input.getReceiptId()), "supplier return receiptId mismatch");
        require(snapshot.purchaseOrderId().equals(input.getPurchaseOrderId()), "supplier return purchaseOrderId mismatch");
        require(snapshot.receiptId().equals(receiptRef.getReceiptId()), "receipt line belongs to another receipt");
        require(snapshot.purchaseOrderId().equals(receiptRef.getProcurementOrderId()),
                "receipt line belongs to another procurement order");
        require(snapshot.purchaseOrderId().equals(qualityRef.getPurchaseOrderId()),
                "quality decision belongs to another procurement order");
        require(receiptRef.getReceiptId().equals(input.getReceiptId()), "receipt line receipt mismatch");
        require(receiptRef.getReceiptLineId().equals(qualityRef.getReceiptLineId()), "quality decision receipt line mismatch");
        require(receiptRef.getProcurementOrderItemId().equals(qualityRef.getPurchaseOrderItemId()),
                "quality decision procurement line mismatch");
        require(receiptRef.getDeliveryScheduleId().equals(qualityRef.getPurchaseOrderScheduleId()),
                "quality decision procurement schedule mismatch");
        require(receiptRef.getCanonicalSkuId().equals(qualityRef.getCanonicalSkuId()), "quality decision sku mismatch");
        require(receiptRef.getOwnerType().equals(qualityRef.getOwnerType())
                        && receiptRef.getOwnerId().equals(qualityRef.getOwnerId()),
                "quality decision owner mismatch");
        require(receiptRef.getValuationPolicyId().equals(qualityRef.getValuationPolicyId())
                        && receiptRef.getValuationPolicyVersion().equals(qualityRef.getValuationPolicyVersion())
                        && receiptRef.getValuationPolicyHash().equals(qualityRef.getValuationPolicyHash()),
                "quality decision valuation snapshot mismatch");
        require(Objects.equals(receiptRef.getUnitCostAmountMinor(), qualityRef.getUnitCostAmountMinor())
                        && Objects.equals(receiptRef.getCurrencyCode(), qualityRef.getCurrencyCode()),
                "quality decision cost snapshot mismatch");
        require(snapshot.supplierId().equals(order.getSupplierId()), "procurement order supplier mismatch");
        require(snapshot.supplierId().equals(receiptRef.getSupplierId())
                        && snapshot.supplierId().equals(qualityRef.getSupplierId()),
                "supplier return supplier mismatch");
        require(snapshot.ownerType().equals(receiptRef.getOwnerType())
                        && snapshot.ownerId().equals(receiptRef.getOwnerId()),
                "supplier return owner mismatch");
        require(snapshot.warehouseId().equals(receiptRef.getWarehouseId())
                        && snapshot.warehouseId().equals(qualityRef.getWarehouseId()),
                "supplier return warehouse mismatch");
    }

    private void verifyInventory(SupplierReturnLineDO line, SupplierReturnDocumentDO document,
                                 InventoryProcurementReceiptResult inventory, long movementCost) {
        require(inventory.getOperationId() != null && inventory.getOperationId() > 0,
                "inventory supplier return returned no operationId");
        require(inventory.getLedgerTransactionId() != null && inventory.getLedgerTransactionId() > 0,
                "inventory supplier return returned no ledgerTransactionId");
        require(Objects.equals(document.getReceiptId(), inventory.getReceiptId())
                        && Objects.equals(line.getReceiptLineId(), inventory.getReceiptLineId()),
                "inventory supplier return returned mismatched receipt identity");
        require(Objects.equals(line.getUnitCostAmountMinor(), inventory.getUnitCostAmountMinor())
                        && Objects.equals(movementCost, inventory.getMovementCostAmountMinor())
                        && Objects.equals(line.getCurrencyCode(), inventory.getCurrencyCode())
                        && Objects.equals(line.getValuationPolicyId(), inventory.getValuationPolicyId())
                        && Objects.equals(line.getValuationPolicyVersion(), inventory.getValuationPolicyVersion())
                        && Objects.equals(line.getValuationPolicyHash(), inventory.getValuationPolicyHash()),
                "inventory supplier return returned mismatched frozen valuation snapshot");
    }

    private void appendEvent(Long tenantId, SupplierReturnCommand command, String eventType,
                             String aggregateId, Long aggregateVersion, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType(eventType)
                .schemaVersion(1)
                .sourceSystem("cloudmold-warehouse")
                .aggregateType("supplier_return")
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .eventSequence((short) 1)
                .idempotencyKey(command.getIdempotencyKey() + ":" + eventType)
                .occurredAt(command.getOccurredAt())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .payload(payload)
                .headers(Map.of("operation", command.getOperation().name(),
                        "source_event_id", command.getSourceEventId()))
                .destination("lakehouse")
                .build());
    }

    private void insertHistory(Long tenantId, Long operationId, String objectType, String objectId, String status,
                               Long statusVersion, String stageCode, String stageLabel, String remark,
                               LocalDateTime now) {
        require(mapper.insertStatusHistory(new SupplierReturnStatusHistoryDO()
                        .setHistoryId(UUID.randomUUID().toString())
                        .setTenantId(tenantId).setOperationId(operationId)
                        .setBusinessObjectType(objectType).setBusinessObjectId(objectId).setStatus(status)
                        .setStatusVersion(statusVersion).setStageCode(stageCode).setStageLabel(stageLabel)
                        .setRemark(text(remark)).setChangedAt(now).setCreatedAt(now)) == 1,
                "failed to persist supplier return status history");
    }

    private static BigDecimal availableQuantity(SupplierReturnReferenceMapper.QualityDecisionReference reference,
                                                SupplierReturnSourceDisposition disposition) {
        return switch (disposition) {
            case ACCEPTED -> scale(reference.getAcceptedQuantity());
            case REJECTED -> scale(reference.getRejectedQuantity());
            case QUARANTINED -> scale(reference.getQuarantinedQuantity());
        };
    }

    private static InventoryProcurementReceiptDisposition dispositionOf(String sourceDisposition) {
        return switch (SupplierReturnSourceDisposition.valueOf(sourceDisposition)) {
            case ACCEPTED -> InventoryProcurementReceiptDisposition.ACCEPTED;
            case REJECTED -> InventoryProcurementReceiptDisposition.REJECTED;
            case QUARANTINED -> InventoryProcurementReceiptDisposition.QUARANTINED;
        };
    }

    private static Stage stageForStatus(String status) {
        return switch (status) {
            case "DRAFT" -> new Stage("DRAFT", "草稿待提交", false);
            case "SUBMITTED" -> new Stage("APPROVAL", "等待批准", false);
            case "APPROVED", "PARTIALLY_DISPATCHED" -> new Stage("RETURN_TO_SUPPLIER", "等待退供发运", false);
            case "DISPATCHED" -> new Stage("COMPLETE", "等待退供完成", false);
            case "COMPLETED" -> new Stage("NONE", "退供已完成", true);
            case "CANCELLED" -> new Stage("NONE", "退供已取消", true);
            default -> throw new IllegalArgumentException("unsupported supplier return status: " + status);
        };
    }

    private static String eventId(String sourceEventId, String returnLineId) {
        String source = StringUtils.hasText(sourceEventId) ? sourceEventId.trim() : "supplier-return";
        return "supplier-return:" + DigestUtil.sha256Hex(source + "\u001f" + returnLineId);
    }

    String nextAttemptToken() {
        return UUID.randomUUID().toString();
    }

    private static long multiplyMinor(BigDecimal quantity, Long unitCostAmountMinor) {
        require(unitCostAmountMinor != null, "unitCostAmountMinor is required");
        try {
            return quantity.multiply(BigDecimal.valueOf(unitCostAmountMinor)).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("supplier return movement cost must be an integral minor-unit amount",
                    exception);
        }
    }

    private static void validateEnvelope(SupplierReturnCommand command, String actorPrincipalId) {
        require(command != null, "supplier return command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (StringUtils.hasText(command.getSourceEventId())) {
            requireRef(command.getSourceEventId(), "sourceEventId", 128);
        }
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
    }

    private static String fingerprint(Long tenantId, SupplierReturnCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\n" + JsonUtils.toJsonString(command));
    }

    private static BigDecimal normalizeQuantity(BigDecimal value) {
        require(value != null, "quantity is required");
        BigDecimal normalized = value.setScale(6);
        require(normalized.compareTo(ZERO) > 0, "quantity must be greater than 0");
        return normalized;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6);
    }

    private static String valueOrUuid(String value) {
        return StringUtils.hasText(value) ? requireText(value, "id") : UUID.randomUUID().toString();
    }

    private static String valueOrCode(String value, String prefix) {
        return StringUtils.hasText(value) ? requireText(value, "code") : prefix + "-" + UUID.randomUUID();
    }

    private static String valueOrRef(String value, String fallback) {
        return StringUtils.hasText(value) ? requireText(value, "reference") : fallback;
    }

    private static String requireText(String value, String field) {
        require(StringUtils.hasText(value), field + " is required");
        String trimmed = value.trim();
        require(trimmed.length() <= 255, field + " exceeds max length 255");
        return trimmed;
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(StringUtils.hasText(value), field + " is required");
        String trimmed = value.trim();
        require(trimmed.length() <= maxLength, field + " exceeds max length " + maxLength);
        require(SAFE_REF.matcher(trimmed).matches(), field + " contains unsafe characters");
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static <T> List<T> requireNonEmpty(List<T> value, String field) {
        require(value != null && !value.isEmpty(), field + " is required");
        return value;
    }

    private static Long requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static Integer requirePositive(Integer value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static void require(boolean expression, String message) {
        if (!expression) {
            throw new IllegalArgumentException(message);
        }
    }

    private record HeaderSnapshot(String receiptId, String purchaseOrderId, String supplierId,
                                  String ownerType, String ownerId, String warehouseId) {
    }

    private record Stage(String code, String label, boolean terminal) {
    }
}
