package cn.iocoder.yudao.module.cloudmold.quality.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.ProcureToPayResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptDisposition;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptResult;
import cn.iocoder.yudao.module.cloudmold.quality.api.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.ProcurementReceiptInspectionRecords.*;
import cn.iocoder.yudao.module.cloudmold.quality.dal.dataobject.QualityRecords.StandardVersion;
import cn.iocoder.yudao.module.cloudmold.quality.dal.mysql.ProcurementReceiptInspectionMapper;
import cn.iocoder.yudao.module.cloudmold.quality.service.actor.QualityActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDecisionResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseProcurementQualityDisposition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProcurementReceiptInspectionService implements ProcurementReceiptInspectionCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-quality";
    private static final String AGGREGATE_TYPE = "procurement_receipt_inspection";
    private static final String DESTINATION = "cloudmold.quality.procurement-receipt-inspection.v1";
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> SEVERITIES = Set.of("MINOR", "MAJOR", "CRITICAL");
    private static final Set<String> ACCEPTED_DISPOSITIONS = Set.of("ACCEPT", "CONDITIONAL_ACCEPT");
    private static final Set<String> REJECTED_DISPOSITIONS =
            Set.of("REJECT", "RETURN_TO_SUPPLIER", "REWORK", "SCRAP");
    private static final Set<String> QUARANTINE_DISPOSITIONS = Set.of("QUARANTINE", "HOLD", "REWORK");
    private static final Set<String> OWNER_TYPES = Set.of("MERCHANT", "PLATFORM", "MEMBER");

    private final ProcurementReceiptInspectionMapper mapper;
    private final OutboxAppender outboxAppender;
    private final QualityActorPrincipalPort actorPrincipalPort;
    private final InventoryProcurementReceiptApi inventoryReceiptApi;
    private final WarehouseProcurementQualityDecisionApi warehouseQualityDecisionApi;
    private final P2pEvidenceIngestionApi p2pEvidenceIngestionApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementReceiptInspectionResult execute(
            ProcurementReceiptInspectionCommand command, String actorPrincipalId) {
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
        require(operationId != null && operationId > 0, "failed to resolve procurement receipt inspection operation");
        Operation operation = nonNull(mapper.selectOperationForUpdate(tenantId, operationId),
                "procurement receipt inspection operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getInspectionId() != null,
                    "existing procurement receipt inspection operation is incomplete");
            return resultFromOperation(operation, true);
        }

        Inspection outcome = switch (command.getOperation()) {
            case CREATE_INSPECTION -> createInspection(tenantId, operationId, command, actorPrincipalId, now);
            case RECORD_LINE_RESULTS -> recordLineResults(
                    tenantId, operationId, command, actorPrincipalId, now);
            case COMPLETE_INSPECTION -> completeInspection(
                    tenantId, operationId, command, actorPrincipalId, now);
        };
        require(mapper.markOperationSucceeded(tenantId, operationId, outcome.getInspectionId(),
                outcome.getVersion(), outcome.getStatus(), outcome.getFinalDecision(),
                outcome.getReceivedQuantity(), outcome.getSampledQuantity(), outcome.getAcceptedQuantity(),
                outcome.getRejectedQuantity(), outcome.getQuarantinedQuantity(), now) == 1,
                "procurement receipt inspection operation completion conflict");
        return resultFromInspection(operationId, outcome, false);
    }

    private Inspection createInspection(Long tenantId, Long operationId,
                                        ProcurementReceiptInspectionCommand command,
                                        String actorPrincipalId, LocalDateTime now) {
        ProcurementReceiptInspectionCommand.CreateDefinition input =
                nonNull(command.getCreate(), "create is required");
        String inspectionId = valueOrUuid(input.getInspectionId());
        requireRef(inspectionId, "inspectionId", 128);
        requireCode(input.getInspectionCode(), "inspectionCode");
        requireRef(input.getReceiptId(), "receiptId", 128);
        requireRef(input.getPurchaseOrderId(), "purchaseOrderId", 128);
        String receiptId = requireRequiredUuid(input.getReceiptId(), "receiptId");
        String purchaseOrderId = requireRequiredUuid(input.getPurchaseOrderId(), "purchaseOrderId");
        String supplierId = requireRequiredUuid(input.getSupplierId(), "supplierId");
        String ownerType = upper(input.getOwnerType());
        require(OWNER_TYPES.contains(ownerType), "ownerType is invalid");
        String ownerId = requireRequiredUuid(input.getOwnerId(), "ownerId");
        requireRef(input.getBusinessNo(), "businessNo", 128);
        requireRef(input.getStandardId(), "standardId", 128);
        requirePositiveVersion(input.getStandardVersion(), "standardVersion");
        requireRef(input.getStandardVersionId(), "standardVersionId", 128);
        requireSha256(input.getStandardContentSha256(), "standardContentSha256");
        StandardVersion standard = nonNull(mapper.selectStandardVersion(tenantId, input.getStandardId(),
                        input.getStandardVersion(), input.getStandardVersionId()),
                "published quality standard version not found");
        require(input.getStandardContentSha256().equals(standard.getContentSha256()),
                "quality standard snapshot hash mismatch");
        require(standard.getEffectiveAt() != null && !standard.getEffectiveAt().isAfter(now),
                "quality standard version is not effective at inspection occurrence");
        require(input.getLines() != null && !input.getLines().isEmpty(), "inspection lines are required");

        Set<String> lineIds = new HashSet<>();
        Set<String> receiptLineIds = new HashSet<>();
        Set<Integer> lineNumbers = new HashSet<>();
        BigDecimal inspectionReceived = ZERO;
        List<InspectionLine> lines = new ArrayList<>();
        List<List<InspectionSplit>> lineSplits = new ArrayList<>();
        for (ProcurementReceiptInspectionCommand.LineDefinition definition : input.getLines()) {
            InspectionLine line = validateAndBuildLine(
                    tenantId, inspectionId, purchaseOrderId, supplierId, ownerType, ownerId,
                    definition, now);
            require(lineIds.add(line.getInspectionLineId()), "duplicate inspectionLineId");
            require(receiptLineIds.add(line.getReceiptLineId()), "duplicate receiptLineId");
            require(lineNumbers.add(line.getLineNumber()), "duplicate lineNumber");
            List<InspectionSplit> splits = validateAndBuildSplits(tenantId, inspectionId, line, definition, now);
            BigDecimal splitTotal = splits.stream().map(InspectionSplit::getReceivedQuantity)
                    .reduce(ZERO, BigDecimal::add);
            require(equal(splitTotal, line.getReceivedQuantity()),
                    "split received quantity must equal line received quantity");
            inspectionReceived = inspectionReceived.add(line.getReceivedQuantity());
            lines.add(line);
            lineSplits.add(splits);
        }

        Inspection inspection = new Inspection().setInspectionId(inspectionId).setTenantId(tenantId)
                .setInspectionCode(input.getInspectionCode().toUpperCase(Locale.ROOT))
                .setReceiptId(receiptId).setPurchaseOrderId(purchaseOrderId)
                .setSupplierId(supplierId).setOwnerType(ownerType).setOwnerId(ownerId)
                .setBusinessNo(input.getBusinessNo())
                .setStandardId(input.getStandardId()).setStandardVersion(input.getStandardVersion())
                .setStandardVersionId(input.getStandardVersionId())
                .setStandardContentSha256(input.getStandardContentSha256())
                .setCreatedByPrincipalId(actorPrincipalId)
                .setStatus("OPEN").setReceivedQuantity(inspectionReceived)
                .setSampledQuantity(ZERO).setAcceptedQuantity(ZERO).setRejectedQuantity(ZERO)
                .setQuarantinedQuantity(ZERO).setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertInspection(inspection) == 1, "failed to persist procurement receipt inspection");
        for (int index = 0; index < lines.size(); index++) {
            InspectionLine line = lines.get(index);
            require(mapper.insertLine(line) == 1, "failed to persist procurement receipt inspection line");
            for (InspectionSplit split : lineSplits.get(index)) {
                require(mapper.insertSplit(split) == 1, "failed to persist procurement receipt inspection split");
            }
            insertLineHistory(tenantId, operationId, line.getInspectionLineId(), 1L, null, "OPEN",
                    ZERO, ZERO, ZERO, ZERO, actorPrincipalId, now);
        }
        insertInspectionHistory(tenantId, operationId, inspectionId, 1L, null, "OPEN", null,
                actorPrincipalId, now);
        appendEvent(tenantId, command, inspection, "quality.procurement_receipt_inspection.created",
                Map.of("receipt_id", inspection.getReceiptId(),
                        "purchase_order_id", inspection.getPurchaseOrderId(),
                        "standard_id", inspection.getStandardId(),
                        "standard_version", inspection.getStandardVersion(),
                        "standard_version_id", inspection.getStandardVersionId(),
                        "standard_content_sha256", inspection.getStandardContentSha256(),
                        "line_count", lines.size(),
                        "received_quantity", decimal(inspectionReceived)));
        return inspection;
    }

    private Inspection recordLineResults(Long tenantId, Long operationId,
                                         ProcurementReceiptInspectionCommand command,
                                         String actorPrincipalId, LocalDateTime now) {
        String inspectionId = requireRef(command.getInspectionId(), "inspectionId", 128);
        Long expectedVersion = requirePositiveVersion(command.getExpectedVersion(), "expectedVersion");
        Inspection inspection = nonNull(mapper.selectInspectionForUpdate(tenantId, inspectionId),
                "procurement receipt inspection not found");
        require(inspection.getVersion().equals(expectedVersion), "inspection optimistic lock conflict");
        require(Set.of("OPEN", "IN_PROGRESS", "PARTIALLY_COMPLETED").contains(inspection.getStatus()),
                "inspection does not accept additional results");
        ProcurementReceiptInspectionCommand.ResultsDefinition input =
                nonNull(command.getResults(), "results are required");
        String resultBatchId = valueOrUuid(input.getResultBatchId());
        requireRef(resultBatchId, "resultBatchId", 128);
        require(input.getLines() != null && !input.getLines().isEmpty(), "result lines are required");
        require(mapper.insertResultBatch(new ResultBatch().setResultBatchId(resultBatchId).setTenantId(tenantId)
                .setInspectionId(inspectionId).setInspectionVersionBefore(expectedVersion)
                .setInspectionVersionAfter(expectedVersion + 1).setDecisionVersion(expectedVersion + 1)
                .setActorPrincipalId(actorPrincipalId)
                .setOperationId(operationId).setOccurredAt(now).setCreatedAt(now)) == 1,
                "failed to persist inspection result batch");

        Set<String> lineIds = new HashSet<>();
        for (ProcurementReceiptInspectionCommand.LineResultDefinition lineInput : input.getLines()) {
            require(lineInput != null, "result line is required");
            String lineId = requireRef(lineInput.getInspectionLineId(), "inspectionLineId", 128);
            require(lineIds.add(lineId), "duplicate result inspectionLineId");
            Long expectedLineVersion = requirePositiveVersion(lineInput.getExpectedVersion(), "line expectedVersion");
            InspectionLine line = nonNull(mapper.selectLineForUpdate(tenantId, inspectionId, lineId),
                    "procurement receipt inspection line not found");
            require(line.getVersion().equals(expectedLineVersion), "inspection line optimistic lock conflict");
            require(!"COMPLETED".equals(line.getStatus()), "completed inspection line is immutable");
            require(lineInput.getSplits() != null && !lineInput.getSplits().isEmpty(),
                    "result splits are required");
            Set<String> splitIds = new HashSet<>();
            for (ProcurementReceiptInspectionCommand.SplitResultDefinition splitInput : lineInput.getSplits()) {
                applySplitResult(tenantId, operationId, resultBatchId, inspectionId, lineId,
                        inspection, line, expectedVersion + 1, command, splitInput,
                        actorPrincipalId, now, splitIds);
            }
            QuantityTotals totals = nonNull(mapper.selectLineTotals(tenantId, lineId),
                    "inspection line totals unavailable");
            require(equal(totals.getReceivedQuantity(), line.getReceivedQuantity()),
                    "inspection line split quantity drift detected");
            BigDecimal decided = decided(totals);
            require(decided.compareTo(line.getReceivedQuantity()) <= 0,
                    "inspection line disposition quantity exceeds received quantity");
            String lineStatus = equal(decided, line.getReceivedQuantity()) ? "COMPLETED" : "PARTIALLY_INSPECTED";
            LocalDateTime completedAt = "COMPLETED".equals(lineStatus) ? now : null;
            require(mapper.updateLineTotals(tenantId, lineId, expectedLineVersion, totals, lineStatus,
                    completedAt, now) == 1, "inspection line optimistic lock conflict");
            insertLineHistory(tenantId, operationId, lineId, expectedLineVersion + 1, line.getStatus(),
                    lineStatus, totals.getSampledQuantity(), totals.getAcceptedQuantity(),
                    totals.getRejectedQuantity(), totals.getQuarantinedQuantity(), actorPrincipalId, now);
        }

        QuantityTotals totals = nonNull(mapper.selectInspectionTotals(tenantId, inspectionId),
                "inspection totals unavailable");
        require(equal(totals.getReceivedQuantity(), inspection.getReceivedQuantity()),
                "inspection line quantity drift detected");
        String nextStatus = Objects.equals(totals.getCompletedLines(), totals.getTotalLines())
                ? "READY_TO_COMPLETE"
                : totals.getCompletedLines() != null && totals.getCompletedLines() > 0
                ? "PARTIALLY_COMPLETED" : "IN_PROGRESS";
        String previousStatus = inspection.getStatus();
        require(mapper.updateInspectionTotals(tenantId, inspectionId, expectedVersion, totals, nextStatus,
                actorPrincipalId, now) == 1,
                "inspection optimistic lock conflict");
        Inspection outcome = inspection.setStatus(nextStatus).setVersion(expectedVersion + 1)
                .setSampledQuantity(totals.getSampledQuantity()).setAcceptedQuantity(totals.getAcceptedQuantity())
                .setRejectedQuantity(totals.getRejectedQuantity())
                .setQuarantinedQuantity(totals.getQuarantinedQuantity())
                .setLastDecisionActorPrincipalId(actorPrincipalId).setUpdatedAt(now);
        insertInspectionHistory(tenantId, operationId, inspectionId, outcome.getVersion(), previousStatus,
                nextStatus, null, actorPrincipalId, now);
        appendEvent(tenantId, command, outcome, "quality.procurement_receipt_inspection.results_recorded",
                Map.of("result_batch_id", resultBatchId,
                        "decision_version", outcome.getVersion(),
                        "receipt_id", outcome.getReceiptId(),
                        "purchase_order_id", outcome.getPurchaseOrderId(),
                        "sampled_quantity", decimal(totals.getSampledQuantity()),
                        "accepted_quantity", decimal(totals.getAcceptedQuantity()),
                        "rejected_quantity", decimal(totals.getRejectedQuantity()),
                        "quarantined_quantity", decimal(totals.getQuarantinedQuantity()),
                        "completed_lines", totals.getCompletedLines(), "total_lines", totals.getTotalLines()));
        return outcome;
    }

    private void applySplitResult(Long tenantId, Long operationId, String resultBatchId,
                                  String inspectionId, String lineId,
                                  Inspection inspection, InspectionLine line, Long decisionVersion,
                                  ProcurementReceiptInspectionCommand command,
                                  ProcurementReceiptInspectionCommand.SplitResultDefinition input,
                                  String actorPrincipalId, LocalDateTime now, Set<String> splitIds) {
        require(input != null, "split result is required");
        String splitId = requireRef(input.getInspectionSplitId(), "inspectionSplitId", 128);
        require(splitIds.add(splitId), "duplicate result inspectionSplitId");
        String resultSplitId = valueOrUuid(input.getResultSplitId());
        requireRef(resultSplitId, "resultSplitId", 128);
        String qualityDecisionId = requireRequiredUuid(input.getQualityDecisionId(), "qualityDecisionId");
        Long expectedSplitVersion = requirePositiveVersion(input.getExpectedVersion(), "split expectedVersion");
        InspectionSplit split = nonNull(mapper.selectSplitForUpdate(tenantId, inspectionId, lineId, splitId),
                "procurement receipt inspection split not found");
        require(split.getVersion().equals(expectedSplitVersion), "inspection split optimistic lock conflict");
        require(!"COMPLETED".equals(split.getStatus()), "completed inspection split is immutable");
        BigDecimal sampled = quantity(input.getSampledQuantity(), "sampledQuantity", true);
        BigDecimal accepted = quantity(input.getAcceptedQuantity(), "acceptedQuantity", true);
        BigDecimal rejected = quantity(input.getRejectedQuantity(), "rejectedQuantity", true);
        BigDecimal quarantined = quantity(input.getQuarantinedQuantity(), "quarantinedQuantity", true);
        BigDecimal disposition = accepted.add(rejected).add(quarantined);
        require(disposition.signum() > 0, "a split result must decide a positive quantity");
        require(sampled.signum() > 0, "a split result must record a positive sampled quantity");
        requireSha256(input.getDecisionEvidenceSha256(), "decisionEvidenceSha256");
        if (input.getEvidenceRef() != null) requireRef(input.getEvidenceRef(), "evidenceRef", 256);
        validateDisposition(accepted, input.getAcceptedDispositionCode(), ACCEPTED_DISPOSITIONS,
                "acceptedDispositionCode");
        validateDisposition(rejected, input.getRejectedDispositionCode(), REJECTED_DISPOSITIONS,
                "rejectedDispositionCode");
        validateDisposition(quarantined, input.getQuarantineDispositionCode(), QUARANTINE_DISPOSITIONS,
                "quarantineDispositionCode");
        BigDecimal newSampled = split.getSampledQuantity().add(sampled);
        BigDecimal newDecided = split.getAcceptedQuantity().add(split.getRejectedQuantity())
                .add(split.getQuarantinedQuantity()).add(disposition);
        require(newSampled.compareTo(split.getReceivedQuantity()) <= 0,
                "sampled quantity exceeds split received quantity");
        require(newDecided.compareTo(split.getReceivedQuantity()) <= 0,
                "disposition quantity exceeds split received quantity");
        String splitStatus = equal(newDecided, split.getReceivedQuantity()) ? "COMPLETED" : "PARTIALLY_INSPECTED";
        LocalDateTime completedAt = "COMPLETED".equals(splitStatus) ? now : null;
        require(mapper.applySplitResult(tenantId, splitId, expectedSplitVersion, sampled, accepted, rejected,
                quarantined, splitStatus, completedAt, now) == 1, "inspection split optimistic lock conflict");

        ResultSplit result = new ResultSplit().setResultSplitId(resultSplitId).setTenantId(tenantId)
                .setQualityDecisionId(qualityDecisionId).setDecisionVersion(decisionVersion)
                .setResultBatchId(resultBatchId).setInspectionId(inspectionId).setInspectionLineId(lineId)
                .setInspectionSplitId(splitId).setSampledQuantity(sampled).setAcceptedQuantity(accepted)
                .setRejectedQuantity(rejected).setQuarantinedQuantity(quarantined)
                .setAcceptedDispositionCode(upper(input.getAcceptedDispositionCode()))
                .setRejectedDispositionCode(upper(input.getRejectedDispositionCode()))
                .setQuarantineDispositionCode(upper(input.getQuarantineDispositionCode()))
                .setDecisionEvidenceSha256(input.getDecisionEvidenceSha256()).setEvidenceRef(input.getEvidenceRef())
                .setActorPrincipalId(actorPrincipalId).setOperationId(operationId)
                .setOccurredAt(now).setCreatedAt(now);
        DispositionEffect acceptedEffect = applyDispositionEffect(result, inspection, line, split, command, accepted,
                InventoryProcurementReceiptOperation.ACCEPT_QUALITY,
                InventoryProcurementReceiptDisposition.ACCEPTED);
        DispositionEffect rejectedEffect = applyDispositionEffect(result, inspection, line, split, command, rejected,
                InventoryProcurementReceiptOperation.REJECT_QUALITY,
                InventoryProcurementReceiptDisposition.REJECTED);
        DispositionEffect quarantinedEffect = applyDispositionEffect(result, inspection, line, split, command, quarantined,
                InventoryProcurementReceiptOperation.QUARANTINE_QUALITY,
                InventoryProcurementReceiptDisposition.QUARANTINED);
        List<DispositionEffect> effects = java.util.stream.Stream.of(
                        acceptedEffect, rejectedEffect, quarantinedEffect)
                .filter(Objects::nonNull).toList();
        ReceiptFinanceEvidence receiptEvidence = requireConsistentReceiptFinanceEvidence(effects);
        result.setFinanceReceiptEvidenceOperationId(receiptEvidence.operationId())
                .setFinanceReceiptEvidenceId(receiptEvidence.evidenceId())
                .setFinanceReceiptEvidenceVersion(receiptEvidence.evidenceVersion());
        ingestFinanceEvidence(result, line, command, actorPrincipalId, disposition, receiptEvidence, effects);
        require(mapper.insertResultSplit(result) == 1, "failed to persist immutable split result");
        validateAndInsertDefects(tenantId, result, input.getDefects(), rejected.add(quarantined),
                disposition, now);
    }

    private DispositionEffect applyDispositionEffect(ResultSplit result, Inspection inspection, InspectionLine line,
                                                     InspectionSplit split,
                                                     ProcurementReceiptInspectionCommand envelope,
                                                     BigDecimal quantity,
                                                     InventoryProcurementReceiptOperation operation,
                                                     InventoryProcurementReceiptDisposition disposition) {
        if (quantity.signum() == 0) {
            return null;
        }
        long movementCost;
        try {
            movementCost = quantity.multiply(BigDecimal.valueOf(line.getUnitCostAmountMinor())).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "disposition movement cost must be an integral minor-unit amount", exception);
        }
        String effectName = disposition.name().toLowerCase(Locale.ROOT);
        InventoryProcurementReceiptCommand command = InventoryProcurementReceiptCommand.builder()
                .operation(operation).disposition(disposition)
                .idempotencyKey("quality:" + result.getQualityDecisionId() + ":" + effectName)
                .sourceEventId(deterministicUuid(result.getQualityDecisionId() + ":" + effectName))
                .receiptId(inspection.getReceiptId()).receiptLineId(line.getReceiptLineId())
                .purchaseOrderId(line.getPurchaseOrderId()).purchaseOrderItemId(line.getItemId())
                .purchaseOrderScheduleId(line.getScheduleId()).supplierId(line.getSupplierId())
                .ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                .canonicalSkuId(line.getCanonicalSkuId()).warehouseId(split.getWarehouseId())
                .locationId(split.getLocationId()).lotId(split.getLotId()).baseUomCode(line.getUomCode())
                .quantity(quantity).qualityDecisionId(result.getQualityDecisionId())
                .decisionVersion(result.getDecisionVersion())
                .qualityEvidenceRef("sha256:" + result.getDecisionEvidenceSha256())
                .valuationPolicy(line.getValuationPolicy())
                .valuationPolicyVersion(line.getValuationPolicyVersion())
                .valuationPolicyHash(line.getValuationPolicyHash())
                .unitCostAmountMinor(line.getUnitCostAmountMinor()).movementCostAmountMinor(movementCost)
                .currencyCode(line.getCurrencyCode()).businessNo(inspection.getBusinessNo())
                .correlationId(envelope.getCorrelationId()).causationId(envelope.getCausationId())
                .occurredAt(envelope.getOccurredAt()).build();
        InventoryProcurementReceiptResult outcome = nonNull(inventoryReceiptApi.execute(command),
                "inventory quality effect returned no result");
        require(outcome.getOperationId() != null && outcome.getOperationId() > 0,
                "inventory quality effect returned no operationId");
        require(outcome.getLedgerTransactionId() != null && outcome.getLedgerTransactionId() > 0,
                "inventory quality effect returned no ledgerTransactionId");
        require(positive(outcome.getTargetAggregateVersion()),
                "inventory quality effect returned no target aggregate version");
        require(Objects.equals(outcome.getReceiptId(), inspection.getReceiptId())
                        && Objects.equals(outcome.getReceiptLineId(), line.getReceiptLineId()),
                "inventory quality effect returned mismatched receipt identity");
        require(Objects.equals(outcome.getUnitCostAmountMinor(), line.getUnitCostAmountMinor())
                        && Objects.equals(outcome.getMovementCostAmountMinor(), movementCost)
                        && Objects.equals(outcome.getCurrencyCode(), line.getCurrencyCode())
                        && Objects.equals(outcome.getValuationPolicyId(), line.getValuationPolicy())
                        && Objects.equals(outcome.getValuationPolicyVersion(), line.getValuationPolicyVersion())
                        && Objects.equals(outcome.getValuationPolicyHash(), line.getValuationPolicyHash()),
                "inventory quality effect returned mismatched frozen valuation snapshot");
        WarehouseProcurementQualityDisposition warehouseDisposition = switch (disposition) {
            case ACCEPTED -> WarehouseProcurementQualityDisposition.ACCEPTED;
            case REJECTED -> WarehouseProcurementQualityDisposition.REJECTED;
            case QUARANTINED -> WarehouseProcurementQualityDisposition.QUARANTINED;
            default -> throw new IllegalArgumentException("unsupported quality warehouse disposition");
        };
        WarehouseProcurementQualityDecisionResult warehouseOutcome = nonNull(
                warehouseQualityDecisionApi.execute(WarehouseProcurementQualityDecisionCommand.builder()
                        .idempotencyKey("quality:" + result.getQualityDecisionId() + ":" + effectName)
                        .sourceEventId(deterministicUuid(result.getQualityDecisionId() + ":" + effectName))
                        .qualityDecisionId(result.getQualityDecisionId())
                        .decisionVersion(result.getDecisionVersion())
                        .inspectionSplitId(result.getInspectionSplitId()).disposition(warehouseDisposition)
                        .receiptId(inspection.getReceiptId()).receiptLineId(line.getReceiptLineId())
                        .procurementOrderId(line.getPurchaseOrderId())
                        .procurementOrderItemId(line.getItemId()).deliveryScheduleId(line.getScheduleId())
                        .warehouseId(split.getWarehouseId()).locationId(split.getLocationId())
                        .lotId(split.getLotId()).baseUomCode(line.getUomCode()).quantity(quantity)
                        .evidenceRef("sha256:" + result.getDecisionEvidenceSha256())
                        .correlationId(envelope.getCorrelationId()).causationId(envelope.getCausationId())
                        .occurredAt(envelope.getOccurredAt()).build()),
                "warehouse quality effect returned no result");
        require(warehouseOutcome.getOperationId() != null && warehouseOutcome.getOperationId() > 0,
                "warehouse quality effect returned no operationId");
        require(positive(warehouseOutcome.getReceiptVersion())
                        && positive(warehouseOutcome.getReceiptLineVersion())
                        && positive(warehouseOutcome.getScheduleFulfillmentVersion()),
                "warehouse quality effect returned invalid aggregate versions");
        require(positive(warehouseOutcome.getFinanceReceiptEvidenceOperationId())
                        && positive(warehouseOutcome.getFinanceReceiptEvidenceVersion())
                        && isUuid(warehouseOutcome.getFinanceReceiptEvidenceId()),
                "warehouse quality effect returned invalid finance receipt evidence reference");
        require(warehouseOutcome.getReceiptStatus() != null && !warehouseOutcome.getReceiptStatus().isBlank()
                        && warehouseOutcome.getReceiptLineStatus() != null
                        && !warehouseOutcome.getReceiptLineStatus().isBlank(),
                "warehouse quality effect returned invalid statuses");
        require(Objects.equals(warehouseOutcome.getReceiptId(), inspection.getReceiptId())
                        && Objects.equals(warehouseOutcome.getReceiptLineId(), line.getReceiptLineId()),
                "warehouse quality effect returned mismatched receipt identity");
        require(nonNegative(warehouseOutcome.getPendingQualityQuantity())
                        && nonNegative(warehouseOutcome.getAcceptedQuantity())
                        && nonNegative(warehouseOutcome.getRejectedQuantity())
                        && nonNegative(warehouseOutcome.getQuarantinedQuantity()),
                "warehouse quality effect returned invalid partition quantities");
        switch (disposition) {
            case ACCEPTED -> result.setAcceptedInventoryOperationId(outcome.getOperationId())
                    .setAcceptedLedgerTransactionId(outcome.getLedgerTransactionId())
                    .setAcceptedInventoryAggregateVersion(outcome.getTargetAggregateVersion())
                    .setAcceptedWarehouseOperationId(warehouseOutcome.getOperationId())
                    .setAcceptedWarehouseReceiptVersion(warehouseOutcome.getReceiptVersion())
                    .setAcceptedWarehouseReceiptLineVersion(warehouseOutcome.getReceiptLineVersion())
                    .setAcceptedWarehouseScheduleFulfillmentVersion(
                            warehouseOutcome.getScheduleFulfillmentVersion());
            case REJECTED -> result.setRejectedInventoryOperationId(outcome.getOperationId())
                    .setRejectedLedgerTransactionId(outcome.getLedgerTransactionId())
                    .setRejectedInventoryAggregateVersion(outcome.getTargetAggregateVersion())
                    .setRejectedWarehouseOperationId(warehouseOutcome.getOperationId())
                    .setRejectedWarehouseReceiptVersion(warehouseOutcome.getReceiptVersion())
                    .setRejectedWarehouseReceiptLineVersion(warehouseOutcome.getReceiptLineVersion())
                    .setRejectedWarehouseScheduleFulfillmentVersion(
                            warehouseOutcome.getScheduleFulfillmentVersion());
            case QUARANTINED -> result.setQuarantinedInventoryOperationId(outcome.getOperationId())
                    .setQuarantinedLedgerTransactionId(outcome.getLedgerTransactionId())
                    .setQuarantinedInventoryAggregateVersion(outcome.getTargetAggregateVersion())
                    .setQuarantinedWarehouseOperationId(warehouseOutcome.getOperationId())
                    .setQuarantinedWarehouseReceiptVersion(warehouseOutcome.getReceiptVersion())
                    .setQuarantinedWarehouseReceiptLineVersion(warehouseOutcome.getReceiptLineVersion())
                    .setQuarantinedWarehouseScheduleFulfillmentVersion(
                            warehouseOutcome.getScheduleFulfillmentVersion());
            default -> throw new IllegalArgumentException("unsupported quality inventory disposition");
        }
        return new DispositionEffect(disposition, quantity, outcome, warehouseOutcome);
    }

    private void ingestFinanceEvidence(ResultSplit result, InspectionLine line,
                                       ProcurementReceiptInspectionCommand envelope,
                                       String actorPrincipalId, BigDecimal inspectedQuantity,
                                       ReceiptFinanceEvidence receiptEvidence,
                                       List<DispositionEffect> effects) {
        String qualitySourceEventId = deterministicUuid(
                result.getQualityDecisionId() + ":finance:quality-disposition");
        ProcureToPayResult qualityEvidence = nonNull(p2pEvidenceIngestionApi.ingestQualityDisposition(
                        P2pEvidenceCommands.QualityDisposition.builder()
                                .envelope(financeEnvelope(envelope,
                                        "quality-finance:" + result.getQualityDecisionId() + ":quality-disposition"))
                                .sourceEventId(qualitySourceEventId).sourceVersion(result.getDecisionVersion())
                                .evidenceSha256(result.getDecisionEvidenceSha256())
                                .sourceOccurredAt(envelope.getOccurredAt())
                                .qualityDispositionId(result.getQualityDecisionId())
                                .receiptLineId(line.getReceiptLineId())
                                .receiptLineVersion(receiptEvidence.evidenceVersion())
                                .purchaseOrderItemId(line.getItemId()).inspectedQuantity(inspectedQuantity)
                                .acceptedQuantity(result.getAcceptedQuantity())
                                .rejectedQuantity(result.getRejectedQuantity())
                                .heldQuantity(result.getQuarantinedQuantity()).unitOfMeasure(line.getUomCode())
                                .build(), actorPrincipalId),
                "finance quality disposition evidence returned no result");
        validateFinanceEvidence(qualityEvidence, "finance_quality_disposition_evidence",
                result.getDecisionVersion(), "finance quality disposition evidence");
        result.setFinanceQualityOperationId(qualityEvidence.getOperationId())
                .setFinanceQualityEvidenceId(qualityEvidence.getAggregateId())
                .setFinanceQualityEvidenceVersion(qualityEvidence.getAggregateVersion());

        for (DispositionEffect effect : effects) {
            InventoryProcurementReceiptResult inventory = effect.inventory();
            String disposition = effect.disposition().name();
            String effectName = disposition.toLowerCase(Locale.ROOT);
            String inventorySourceEventId = deterministicUuid(
                    result.getQualityDecisionId() + ":finance:inventory:" + effectName);
            ProcureToPayResult inventoryEvidence = nonNull(p2pEvidenceIngestionApi.ingestInventoryMovement(
                            P2pEvidenceCommands.InventoryMovement.builder()
                                    .envelope(financeEnvelope(envelope,
                                            "quality-finance:" + result.getQualityDecisionId()
                                                    + ":inventory:" + effectName))
                                    .sourceEventId(inventorySourceEventId)
                                    .sourceVersion(inventory.getTargetAggregateVersion())
                                    .evidenceSha256(inventoryEvidenceSha256(result, line, effect))
                                    .sourceOccurredAt(envelope.getOccurredAt())
                                    .inventoryMovementId(inventory.getLedgerTransactionId().toString())
                                    .receiptLineId(line.getReceiptLineId())
                                    .qualityDispositionId(result.getQualityDecisionId())
                                    .qualityDispositionVersion(result.getDecisionVersion())
                                    .disposition(disposition).purchaseOrderItemId(line.getItemId())
                                    .movementQuantity(effect.quantity()).unitOfMeasure(line.getUomCode())
                                    .movementCostAmountMinor(inventory.getMovementCostAmountMinor())
                                    .unitCostAmountMinor(inventory.getUnitCostAmountMinor())
                                    .currencyCode(inventory.getCurrencyCode())
                                    .valuationPolicyId(inventory.getValuationPolicyId())
                                    .valuationPolicyVersion(inventory.getValuationPolicyVersion())
                                    .build(), actorPrincipalId),
                    "finance inventory movement evidence returned no result");
            validateFinanceEvidence(inventoryEvidence, "finance_inventory_movement_evidence",
                    inventory.getTargetAggregateVersion(), "finance inventory movement evidence");
            switch (effect.disposition()) {
                case ACCEPTED -> result
                        .setAcceptedFinanceInventoryOperationId(inventoryEvidence.getOperationId())
                        .setAcceptedFinanceInventoryEvidenceId(inventoryEvidence.getAggregateId())
                        .setAcceptedFinanceInventoryEvidenceVersion(inventoryEvidence.getAggregateVersion());
                case REJECTED -> result
                        .setRejectedFinanceInventoryOperationId(inventoryEvidence.getOperationId())
                        .setRejectedFinanceInventoryEvidenceId(inventoryEvidence.getAggregateId())
                        .setRejectedFinanceInventoryEvidenceVersion(inventoryEvidence.getAggregateVersion());
                case QUARANTINED -> result
                        .setQuarantinedFinanceInventoryOperationId(inventoryEvidence.getOperationId())
                        .setQuarantinedFinanceInventoryEvidenceId(inventoryEvidence.getAggregateId())
                        .setQuarantinedFinanceInventoryEvidenceVersion(inventoryEvidence.getAggregateVersion());
                default -> throw new IllegalArgumentException("unsupported finance inventory disposition");
            }
        }
    }

    private static ReceiptFinanceEvidence requireConsistentReceiptFinanceEvidence(
            List<DispositionEffect> effects) {
        require(!effects.isEmpty(), "a split result must produce a positive cross-domain effect");
        WarehouseProcurementQualityDecisionResult first = effects.get(0).warehouse();
        ReceiptFinanceEvidence reference = new ReceiptFinanceEvidence(
                first.getFinanceReceiptEvidenceOperationId(), first.getFinanceReceiptEvidenceId(),
                first.getFinanceReceiptEvidenceVersion());
        for (DispositionEffect effect : effects) {
            WarehouseProcurementQualityDecisionResult outcome = effect.warehouse();
            require(Objects.equals(reference.operationId(), outcome.getFinanceReceiptEvidenceOperationId())
                            && Objects.equals(reference.evidenceId(), outcome.getFinanceReceiptEvidenceId())
                            && Objects.equals(reference.evidenceVersion(), outcome.getFinanceReceiptEvidenceVersion()),
                    "warehouse quality effects returned inconsistent finance receipt evidence references");
        }
        return reference;
    }

    private static FinanceCommandEnvelope financeEnvelope(
            ProcurementReceiptInspectionCommand envelope, String idempotencyKey) {
        return FinanceCommandEnvelope.builder().correlationId(envelope.getCorrelationId())
                .causationId(envelope.getSourceEventId()).idempotencyKey(idempotencyKey)
                .occurredAt(envelope.getOccurredAt()).build();
    }

    private static String inventoryEvidenceSha256(ResultSplit result, InspectionLine line,
                                                  DispositionEffect effect) {
        InventoryProcurementReceiptResult inventory = effect.inventory();
        return DigestUtil.sha256Hex(String.join("\n",
                inventory.getLedgerTransactionId().toString(),
                inventory.getTargetAggregateVersion().toString(),
                result.getQualityDecisionId(), result.getDecisionVersion().toString(),
                effect.disposition().name(), decimal(effect.quantity()), line.getReceiptLineId(), line.getItemId(),
                line.getUomCode(), inventory.getUnitCostAmountMinor().toString(),
                inventory.getMovementCostAmountMinor().toString(), inventory.getCurrencyCode(),
                inventory.getValuationPolicyId(), inventory.getValuationPolicyVersion(),
                inventory.getValuationPolicyHash()));
    }

    private static void validateFinanceEvidence(ProcureToPayResult outcome, String aggregateType,
                                                Long aggregateVersion, String subject) {
        require(outcome.getOperationId() != null && outcome.getOperationId() > 0,
                subject + " returned no operationId");
        require(Objects.equals(aggregateType, outcome.getAggregateType())
                        && isUuid(outcome.getAggregateId())
                        && Objects.equals(aggregateVersion, outcome.getAggregateVersion())
                        && "RECORDED".equals(outcome.getStatus()),
                subject + " returned invalid immutable evidence identity");
    }

    private void validateAndInsertDefects(Long tenantId, ResultSplit result,
                                          List<ProcurementReceiptInspectionCommand.DefectDefinition> inputs,
                                          BigDecimal nonconformingQuantity, BigDecimal decidedQuantity,
                                          LocalDateTime now) {
        List<ProcurementReceiptInspectionCommand.DefectDefinition> defects =
                inputs == null ? List.of() : inputs;
        require(nonconformingQuantity.signum() == 0 || !defects.isEmpty(),
                "rejected or quarantined quantity requires defect evidence");
        Set<String> ids = new HashSet<>();
        BigDecimal affectedTotal = ZERO;
        for (ProcurementReceiptInspectionCommand.DefectDefinition input : defects) {
            String defectId = valueOrUuid(input.getDefectId());
            requireRef(defectId, "defectId", 128);
            require(ids.add(defectId), "duplicate defectId");
            requireCode(input.getDefectCode(), "defectCode");
            requireCode(input.getDefectCategory(), "defectCategory");
            String severity = upper(input.getSeverity());
            require(SEVERITIES.contains(severity), "severity is invalid");
            BigDecimal affected = quantity(input.getAffectedQuantity(), "affectedQuantity", false);
            affectedTotal = affectedTotal.add(affected);
            requireSha256(input.getEvidenceSha256(), "defect evidenceSha256");
            if (input.getEvidenceRef() != null) requireRef(input.getEvidenceRef(), "defect evidenceRef", 256);
            Defect defect = new Defect().setDefectId(defectId).setTenantId(tenantId)
                    .setResultSplitId(result.getResultSplitId()).setInspectionId(result.getInspectionId())
                    .setInspectionLineId(result.getInspectionLineId())
                    .setInspectionSplitId(result.getInspectionSplitId())
                    .setDefectCode(upper(input.getDefectCode())).setDefectCategory(upper(input.getDefectCategory()))
                    .setSeverity(severity).setAffectedQuantity(affected)
                    .setEvidenceSha256(input.getEvidenceSha256()).setEvidenceRef(input.getEvidenceRef())
                    .setCreatedAt(now);
            require(mapper.insertDefect(defect) == 1, "failed to persist immutable inspection defect");
        }
        require(affectedTotal.compareTo(decidedQuantity) <= 0,
                "defect affected quantity exceeds decided quantity");
    }

    private Inspection completeInspection(Long tenantId, Long operationId,
                                          ProcurementReceiptInspectionCommand command,
                                          String actorPrincipalId, LocalDateTime now) {
        String inspectionId = requireRef(command.getInspectionId(), "inspectionId", 128);
        Long expectedVersion = requirePositiveVersion(command.getExpectedVersion(), "expectedVersion");
        Inspection inspection = nonNull(mapper.selectInspectionForUpdate(tenantId, inspectionId),
                "procurement receipt inspection not found");
        require(inspection.getVersion().equals(expectedVersion), "inspection optimistic lock conflict");
        require("READY_TO_COMPLETE".equals(inspection.getStatus()),
                "inspection can complete only after every line is complete");
        require(inspection.getLastDecisionActorPrincipalId() != null,
                "inspection has no immutable decision maker");
        require(!actorPrincipalId.equals(inspection.getLastDecisionActorPrincipalId()),
                "inspection checker must be independent from the latest decision maker");
        require(mapper.countResultBatchesByActor(tenantId, inspectionId, actorPrincipalId) == 0,
                "inspection checker must be independent from every decision maker");
        QuantityTotals totals = nonNull(mapper.selectInspectionTotals(tenantId, inspectionId),
                "inspection totals unavailable");
        require(Objects.equals(totals.getCompletedLines(), totals.getTotalLines()) && totals.getTotalLines() > 0,
                "inspection has incomplete lines");
        require(equal(decided(totals), totals.getReceivedQuantity()),
                "inspection quantity conservation failed");
        String finalDecision = finalDecision(totals);
        require(mapper.completeInspection(tenantId, inspectionId, expectedVersion, finalDecision,
                actorPrincipalId, now) == 1,
                "inspection optimistic lock conflict");
        Inspection outcome = inspection.setStatus("COMPLETED").setFinalDecision(finalDecision)
                .setVersion(expectedVersion + 1).setCompletedAt(now).setUpdatedAt(now)
                .setCompletedByPrincipalId(actorPrincipalId)
                .setSampledQuantity(totals.getSampledQuantity()).setAcceptedQuantity(totals.getAcceptedQuantity())
                .setRejectedQuantity(totals.getRejectedQuantity())
                .setQuarantinedQuantity(totals.getQuarantinedQuantity());
        insertInspectionHistory(tenantId, operationId, inspectionId, outcome.getVersion(),
                "READY_TO_COMPLETE", "COMPLETED", finalDecision, actorPrincipalId, now);
        appendEvent(tenantId, command, outcome, "quality.procurement_receipt_inspection.completed",
                Map.of("receipt_id", inspection.getReceiptId(),
                        "purchase_order_id", inspection.getPurchaseOrderId(),
                        "final_decision", finalDecision,
                        "received_quantity", decimal(totals.getReceivedQuantity()),
                        "sampled_quantity", decimal(totals.getSampledQuantity()),
                        "accepted_quantity", decimal(totals.getAcceptedQuantity()),
                        "rejected_quantity", decimal(totals.getRejectedQuantity()),
                        "quarantined_quantity", decimal(totals.getQuarantinedQuantity())));
        return outcome;
    }

    private InspectionLine validateAndBuildLine(Long tenantId, String inspectionId, String purchaseOrderId,
                                                String supplierId, String ownerType, String ownerId,
                                                ProcurementReceiptInspectionCommand.LineDefinition input,
                                                LocalDateTime now) {
        require(input != null, "inspection line is required");
        String lineId = valueOrUuid(input.getInspectionLineId());
        requireRef(lineId, "inspectionLineId", 128);
        require(input.getLineNumber() != null && input.getLineNumber() > 0, "lineNumber must be positive");
        requireRef(input.getReceiptLineId(), "receiptLineId", 128);
        requireRef(input.getItemId(), "itemId", 128);
        requireRef(input.getScheduleId(), "scheduleId", 128);
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        String receiptLineId = requireRequiredUuid(input.getReceiptLineId(), "receiptLineId");
        String itemId = requireRequiredUuid(input.getItemId(), "itemId");
        String scheduleId = requireRequiredUuid(input.getScheduleId(), "scheduleId");
        String canonicalSkuId = requireRequiredUuid(input.getCanonicalSkuId(), "canonicalSkuId");
        requireCode(input.getUomCode(), "uomCode");
        String valuationPolicy = upper(input.getValuationPolicy());
        requireCode(valuationPolicy, "valuationPolicy");
        String valuationPolicyVersion = upper(input.getValuationPolicyVersion());
        requireCode(valuationPolicyVersion, "valuationPolicyVersion");
        requireSha256(input.getValuationPolicyHash(), "valuationPolicyHash");
        require(input.getUnitCostAmountMinor() != null && input.getUnitCostAmountMinor() >= 0,
                "unitCostAmountMinor is required and cannot be negative");
        String currencyCode = upper(input.getCurrencyCode());
        require(currencyCode != null && currencyCode.matches("[A-Z]{3}"),
                "currencyCode must be ISO-4217");
        BigDecimal received = quantity(input.getReceivedQuantity(), "receivedQuantity", false);
        return new InspectionLine().setInspectionLineId(lineId).setTenantId(tenantId)
                .setInspectionId(inspectionId).setLineNumber(input.getLineNumber())
                .setReceiptLineId(receiptLineId).setPurchaseOrderId(purchaseOrderId)
                .setItemId(itemId).setScheduleId(scheduleId)
                .setCanonicalSkuId(canonicalSkuId).setUomCode(upper(input.getUomCode()))
                .setSupplierId(supplierId).setOwnerType(ownerType).setOwnerId(ownerId)
                .setValuationPolicy(valuationPolicy).setValuationPolicyVersion(valuationPolicyVersion)
                .setValuationPolicyHash(input.getValuationPolicyHash())
                .setUnitCostAmountMinor(input.getUnitCostAmountMinor()).setCurrencyCode(currencyCode)
                .setReceivedQuantity(received).setSampledQuantity(ZERO).setAcceptedQuantity(ZERO)
                .setRejectedQuantity(ZERO).setQuarantinedQuantity(ZERO).setStatus("OPEN")
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
    }

    private List<InspectionSplit> validateAndBuildSplits(Long tenantId, String inspectionId,
                                                         InspectionLine line,
                                                         ProcurementReceiptInspectionCommand.LineDefinition input,
                                                         LocalDateTime now) {
        require(input.getSplits() != null && !input.getSplits().isEmpty(), "line splits are required");
        Set<String> ids = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();
        List<InspectionSplit> splits = new ArrayList<>();
        for (ProcurementReceiptInspectionCommand.SplitDefinition definition : input.getSplits()) {
            require(definition != null, "inspection split is required");
            String splitId = valueOrUuid(definition.getInspectionSplitId());
            requireRef(splitId, "inspectionSplitId", 128);
            require(ids.add(splitId), "duplicate inspectionSplitId");
            require(definition.getSplitNumber() != null && definition.getSplitNumber() > 0,
                    "splitNumber must be positive");
            require(numbers.add(definition.getSplitNumber()), "duplicate splitNumber");
            requireRef(definition.getWarehouseId(), "warehouseId", 128);
            requireRef(definition.getLocationId(), "locationId", 128);
            if (definition.getLotId() != null) requireRef(definition.getLotId(), "lotId", 128);
            String warehouseId = requireRequiredUuid(definition.getWarehouseId(), "warehouseId");
            String locationId = requireRequiredUuid(definition.getLocationId(), "locationId");
            String lotId = definition.getLotId() == null ? null
                    : requireRequiredUuid(definition.getLotId(), "lotId");
            BigDecimal received = quantity(definition.getReceivedQuantity(), "split receivedQuantity", false);
            splits.add(new InspectionSplit().setInspectionSplitId(splitId).setTenantId(tenantId)
                    .setInspectionId(inspectionId).setInspectionLineId(line.getInspectionLineId())
                    .setSplitNumber(definition.getSplitNumber()).setWarehouseId(warehouseId)
                    .setLocationId(locationId).setLotId(lotId)
                    .setUomCode(line.getUomCode()).setReceivedQuantity(received).setSampledQuantity(ZERO)
                    .setAcceptedQuantity(ZERO).setRejectedQuantity(ZERO).setQuarantinedQuantity(ZERO)
                    .setStatus("OPEN").setVersion(1L).setCreatedAt(now).setUpdatedAt(now));
        }
        return splits;
    }

    private void insertInspectionHistory(Long tenantId, Long operationId, String inspectionId,
                                         Long version, String previousStatus, String currentStatus,
                                         String finalDecision, String actorPrincipalId, LocalDateTime now) {
        require(mapper.insertInspectionHistory(new InspectionHistory().setTenantId(tenantId)
                .setInspectionId(inspectionId).setInspectionVersion(version)
                .setPreviousStatus(previousStatus).setCurrentStatus(currentStatus)
                .setFinalDecision(finalDecision).setActorPrincipalId(actorPrincipalId)
                .setOperationId(operationId).setOccurredAt(now).setCreatedAt(now)) == 1,
                "failed to persist immutable inspection history");
    }

    private void insertLineHistory(Long tenantId, Long operationId, String lineId, Long version,
                                   String previousStatus, String currentStatus, BigDecimal sampled,
                                   BigDecimal accepted, BigDecimal rejected, BigDecimal quarantined,
                                   String actorPrincipalId, LocalDateTime now) {
        require(mapper.insertLineHistory(new LineHistory().setTenantId(tenantId)
                .setInspectionLineId(lineId).setLineVersion(version).setPreviousStatus(previousStatus)
                .setCurrentStatus(currentStatus).setSampledQuantity(sampled).setAcceptedQuantity(accepted)
                .setRejectedQuantity(rejected).setQuarantinedQuantity(quarantined)
                .setActorPrincipalId(actorPrincipalId).setOperationId(operationId)
                .setOccurredAt(now).setCreatedAt(now)) == 1,
                "failed to persist immutable inspection line history");
    }

    private void appendEvent(Long tenantId, ProcurementReceiptInspectionCommand command,
                             Inspection inspection, String eventType, Map<String, Object> facts) {
        Map<String, Object> payload = new LinkedHashMap<>(facts);
        payload.put("inspection_id", inspection.getInspectionId());
        payload.put("inspection_code", inspection.getInspectionCode());
        payload.put("status", inspection.getStatus());
        payload.put("inspection_version", inspection.getVersion());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString()).eventType(eventType).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(AGGREGATE_TYPE)
                .aggregateId(inspection.getInspectionId()).aggregateVersion(inspection.getVersion())
                .eventSequence((short) 1).occurredAt(command.getOccurredAt())
                .traceId(command.getCorrelationId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId() != null ? command.getCausationId() : command.getSourceEventId())
                .idempotencyKey(command.getIdempotencyKey()).payload(payload).headers(Map.of())
                .destination(DESTINATION).maxAttempts(16).build());
    }

    private static ProcurementReceiptInspectionResult resultFromInspection(
            Long operationId, Inspection value, boolean duplicate) {
        return ProcurementReceiptInspectionResult.builder().operationId(operationId).duplicate(duplicate)
                .inspectionId(value.getInspectionId()).aggregateVersion(value.getVersion())
                .status(value.getStatus()).finalDecision(value.getFinalDecision())
                .receivedQuantity(value.getReceivedQuantity()).sampledQuantity(value.getSampledQuantity())
                .acceptedQuantity(value.getAcceptedQuantity()).rejectedQuantity(value.getRejectedQuantity())
                .quarantinedQuantity(value.getQuarantinedQuantity()).build();
    }

    private static ProcurementReceiptInspectionResult resultFromOperation(Operation value, boolean duplicate) {
        return ProcurementReceiptInspectionResult.builder().operationId(value.getOperationId()).duplicate(duplicate)
                .inspectionId(value.getInspectionId()).aggregateVersion(value.getAggregateVersion())
                .status(value.getResultStatus()).finalDecision(value.getFinalDecision())
                .receivedQuantity(value.getReceivedQuantity()).sampledQuantity(value.getSampledQuantity())
                .acceptedQuantity(value.getAcceptedQuantity()).rejectedQuantity(value.getRejectedQuantity())
                .quarantinedQuantity(value.getQuarantinedQuantity()).build();
    }

    private static String finalDecision(QuantityTotals totals) {
        boolean accepted = totals.getAcceptedQuantity().signum() > 0;
        boolean rejected = totals.getRejectedQuantity().signum() > 0;
        boolean quarantined = totals.getQuarantinedQuantity().signum() > 0;
        int classes = (accepted ? 1 : 0) + (rejected ? 1 : 0) + (quarantined ? 1 : 0);
        if (classes > 1) return "MIXED";
        if (quarantined) return "QUARANTINED";
        if (rejected) return "REJECTED";
        return "ACCEPTED";
    }

    private static BigDecimal decided(QuantityTotals totals) {
        return totals.getAcceptedQuantity().add(totals.getRejectedQuantity())
                .add(totals.getQuarantinedQuantity());
    }

    private static void validateEnvelope(ProcurementReceiptInspectionCommand command) {
        require(command != null, "command is required");
        require(command.getOperation() != null, "operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRequiredUuid(command.getSourceEventId(), "sourceEventId");
        requireRequiredUuid(command.getCorrelationId(), "correlationId");
        requireRequiredUuid(command.getCausationId(), "causationId");
    }

    private static void validateDisposition(BigDecimal quantity, String code, Set<String> allowed, String field) {
        if (quantity.signum() > 0) {
            require(code != null && allowed.contains(upper(code)), field + " is required and invalid");
        } else {
            require(code == null, field + " must be null when its quantity is zero");
        }
    }

    private static BigDecimal quantity(BigDecimal value, String field, boolean zeroAllowed) {
        require(value != null, field + " is required");
        require(value.scale() <= 6, field + " supports at most 6 decimal places");
        require(value.precision() <= 24, field + " exceeds decimal(24,6)");
        require(zeroAllowed ? value.signum() >= 0 : value.signum() > 0,
                field + (zeroAllowed ? " must be non-negative" : " must be positive"));
        return value;
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(upper(value)).matches(), field + " is invalid");
    }

    private static String requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " is invalid");
        return value;
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be lowercase SHA-256");
    }

    private static Long requirePositiveVersion(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static void requireUuid(String value, String field) {
        if (value == null) return;
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static String requireRequiredUuid(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        requireUuid(value, field);
        return UUID.fromString(value).toString();
    }

    private static String deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String valueOrUuid(String value) {
        return value == null ? UUID.randomUUID().toString() : value;
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static boolean equal(BigDecimal left, BigDecimal right) {
        return left != null && right != null && left.compareTo(right) == 0;
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean nonNegative(BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record DispositionEffect(InventoryProcurementReceiptDisposition disposition,
                                     BigDecimal quantity,
                                     InventoryProcurementReceiptResult inventory,
                                     WarehouseProcurementQualityDecisionResult warehouse) {
    }

    private record ReceiptFinanceEvidence(Long operationId, String evidenceId, Long evidenceVersion) {
    }
}
