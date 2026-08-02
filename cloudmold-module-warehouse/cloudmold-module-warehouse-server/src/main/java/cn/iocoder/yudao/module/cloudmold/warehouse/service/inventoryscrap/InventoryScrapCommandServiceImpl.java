package cn.iocoder.yudao.module.cloudmold.warehouse.service.inventoryscrap;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDispositionBatchDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDispositionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDocumentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InventoryScrapStoreMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryScrapCommandServiceImpl implements InventoryScrapCommandApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> DOCUMENT_STATUSES = Set.of(
            "DRAFT", "SUBMITTED", "APPROVED", "PARTIALLY_DISPOSED", "DISPOSED", "COMPLETED", "CANCELLED");
    private static final Set<String> LINE_STATUSES = Set.of(
            "DRAFT", "SUBMITTED", "APPROVED", "PARTIALLY_DISPOSED", "DISPOSED", "COMPLETED", "CANCELLED");
    private static final Set<String> EVIDENCE_TYPES = Set.of("QUALITY", "COUNT", "INVENTORY");
    private static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");
    private static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");
    private static final Set<String> DISPOSITION_TYPES = Set.of("DESTROYED", "RECYCLED");

    private final InventoryScrapStoreMapper mapper;
    private final WarehouseActorPrincipalPort actorPrincipalPort;
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final InventoryScrapDispositionApi inventoryScrapDispositionApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryScrapResult execute(InventoryScrapCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = toUtc(command.occurredAt());
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.operation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory scrap operation");
        InventoryScrapOperationDO operation = mapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "inventory scrap operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different inventory scrap payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getScrapId() != null,
                    "existing inventory scrap operation is incomplete");
            return InventoryScrapResult.builder()
                    .operationId(operation.getOperationId())
                    .scrapId(operation.getScrapId())
                    .scrapCode(operation.getScrapCode())
                    .scrapStatus(operation.getResultStatus())
                    .batchId(operation.getBatchId())
                    .batchNo(operation.getBatchNo())
                    .processedLineCount(operation.getProcessedLineCount())
                    .aggregateVersion(operation.getAggregateVersion())
                    .duplicate(true)
                    .build();
        }

        actorPrincipalPort.requireActive(command.actorPrincipalId());
        InventoryScrapResult result = switch (command.operation()) {
            case CREATE_DRAFT -> createDraft(tenantId, operationId, command, now);
            case SUBMIT -> submit(tenantId, operationId, command, now);
            case APPROVE -> approve(tenantId, operationId, command, now);
            case RECORD_DISPOSITION_BATCH -> recordDispositionBatch(tenantId, operationId, command, now);
            case COMPLETE -> complete(tenantId, operationId, command, now);
            case CANCEL -> cancel(tenantId, operationId, command, now);
        };
        result.setOperationId(operationId);
        require(mapper.markOperationSucceeded(tenantId, operationId, result.getScrapId(), result.getScrapCode(),
                result.getBatchId(), result.getBatchNo(), result.getScrapStatus(),
                result.getProcessedLineCount(), result.getAggregateVersion(), now) == 1,
                "inventory scrap operation completion conflict");
        return result;
    }

    private InventoryScrapResult createDraft(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        require(command.lines() != null && !command.lines().isEmpty(), "lines are required");
        warehouseReferenceValidationApi.requireActiveWarehouse(command.warehouseId());
        String scrapId = valueOrUuid(command.scrapId());
        String scrapCode = valueOrCode(command.scrapCode(), "SCRAP");
        require(mapper.selectDocument(tenantId, scrapId) == null, "inventory scrap document already exists");
        BigDecimal totalRequested = ZERO;
        int generatedLineNo = 10;
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        for (NormalizedLine line : command.lines()) {
            lineNumbers.add(line.lineNumber() == null ? generatedLineNo : line.lineNumber());
            catalogSkuValidationApi.requireActiveSku(line.canonicalSkuId());
            warehouseReferenceValidationApi.requireActiveLocation(command.warehouseId(), line.locationId());
            totalRequested = totalRequested.add(line.requestedQuantity());
            generatedLineNo += 10;
        }
        require(lineNumbers.size() == command.lines().size(), "duplicate lineNumber");

        InventoryScrapDocumentDO document = new InventoryScrapDocumentDO()
                .setScrapId(scrapId).setTenantId(tenantId).setScrapCode(scrapCode)
                .setReasonCode(command.reasonCode()).setRemark(command.remark())
                .setOwnerType(command.ownerType()).setOwnerId(command.ownerId())
                .setWarehouseId(command.warehouseId()).setStatus("DRAFT").setVersion(1L)
                .setTotalRequestedQuantity(totalRequested).setTotalDisposedQuantity(ZERO)
                .setLineCount(command.lines().size()).setRequestedByPrincipalId(command.actorPrincipalId())
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertDocument(document) == 1, "failed to persist inventory scrap document");

        generatedLineNo = 10;
        for (NormalizedLine line : command.lines()) {
            int lineNo = line.lineNumber() == null ? generatedLineNo : line.lineNumber();
            String lineId = line.lineId() == null ? scrapId + ":line:" + lineNo : line.lineId();
            require(mapper.insertLine(new InventoryScrapLineDO()
                    .setLineId(lineId).setTenantId(tenantId).setScrapId(scrapId).setLineNumber(lineNo)
                    .setCanonicalSkuId(line.canonicalSkuId()).setLocationId(line.locationId())
                    .setLotId(line.lotId()).setStockStatus(line.stockStatus()).setQualityStatus(line.qualityStatus())
                    .setBaseUomCode(line.baseUomCode()).setRequestedQuantity(line.requestedQuantity())
                    .setDisposedQuantity(ZERO).setEvidenceType(line.evidenceType()).setEvidenceRef(line.evidenceRef())
                    .setStatus("DRAFT").setVersion(1L).setRemark(line.remark()).setCreatedAt(now).setUpdatedAt(now)) == 1,
                    "failed to persist inventory scrap line");
            generatedLineNo += 10;
        }
        insertHistory(tenantId, operationId, scrapId, "DRAFT", 1L, command.actorPrincipalId(), command.remark(), now);
        return InventoryScrapResult.builder()
                .scrapId(scrapId).scrapCode(scrapCode).scrapStatus("DRAFT")
                .processedLineCount(command.lines().size()).aggregateVersion(1L).duplicate(false).build();
    }

    private InventoryScrapResult submit(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        InventoryScrapDocumentDO document = requireDocumentForUpdate(tenantId, command.scrapId(), "DRAFT");
        List<InventoryScrapLineDO> lines = mapper.selectLines(tenantId, document.getScrapId());
        for (InventoryScrapLineDO line : lines) {
            require(mapper.updateLineStatusCas(tenantId, line.getLineId(), line.getVersion(), line.getVersion() + 1,
                    "SUBMITTED", now) == 1, "inventory scrap line submit version conflict");
        }
        long nextVersion = document.getVersion() + 1;
        require(mapper.submitDocumentCas(tenantId, document.getScrapId(), document.getVersion(),
                nextVersion, "SUBMITTED", command.actorPrincipalId(), now) == 1,
                "inventory scrap document submit version conflict");
        insertHistory(tenantId, operationId, document.getScrapId(), "SUBMITTED", nextVersion,
                command.actorPrincipalId(), command.remark(), now);
        return result(document.getScrapId(), document.getScrapCode(), "SUBMITTED", null, null, lines.size(), nextVersion);
    }

    private InventoryScrapResult approve(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        InventoryScrapDocumentDO document = requireDocumentForUpdate(tenantId, command.scrapId(), "SUBMITTED");
        List<InventoryScrapLineDO> lines = mapper.selectLines(tenantId, document.getScrapId());
        for (InventoryScrapLineDO line : lines) {
            require(mapper.updateLineStatusCas(tenantId, line.getLineId(), line.getVersion(), line.getVersion() + 1,
                    "APPROVED", now) == 1, "inventory scrap line approve version conflict");
        }
        long nextVersion = document.getVersion() + 1;
        require(mapper.approveDocumentCas(tenantId, document.getScrapId(), document.getVersion(),
                nextVersion, "APPROVED", command.actorPrincipalId(), now) == 1,
                "inventory scrap document approve version conflict");
        insertHistory(tenantId, operationId, document.getScrapId(), "APPROVED", nextVersion,
                command.actorPrincipalId(), command.remark(), now);
        return result(document.getScrapId(), document.getScrapCode(), "APPROVED", null, null, lines.size(), nextVersion);
    }

    private InventoryScrapResult cancel(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        InventoryScrapDocumentDO document = requireDocument(tenantId, command.scrapId());
        require(Set.of("DRAFT", "SUBMITTED", "APPROVED").contains(document.getStatus()),
                "inventory scrap document cannot be cancelled in current status");
        require(document.getTotalDisposedQuantity().compareTo(ZERO) == 0,
                "inventory scrap document with executed disposition cannot be cancelled");
        document = requireDocumentForUpdate(tenantId, command.scrapId(), document.getStatus());
        List<InventoryScrapLineDO> lines = mapper.selectLines(tenantId, document.getScrapId());
        for (InventoryScrapLineDO line : lines) {
            require(mapper.updateLineStatusCas(tenantId, line.getLineId(), line.getVersion(), line.getVersion() + 1,
                    "CANCELLED", now) == 1, "inventory scrap line cancel version conflict");
        }
        long nextVersion = document.getVersion() + 1;
        require(mapper.cancelDocumentCas(tenantId, document.getScrapId(), document.getVersion(),
                nextVersion, "CANCELLED", command.actorPrincipalId(), now) == 1,
                "inventory scrap document cancel version conflict");
        insertHistory(tenantId, operationId, document.getScrapId(), "CANCELLED", nextVersion,
                command.actorPrincipalId(), command.remark(), now);
        return result(document.getScrapId(), document.getScrapCode(), "CANCELLED", null, null, lines.size(), nextVersion);
    }

    private InventoryScrapResult recordDispositionBatch(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        require(command.dispositionBatch() != null, "dispositionBatch is required");
        InventoryScrapDocumentDO document = requireDocument(tenantId, command.scrapId());
        require(Set.of("APPROVED", "PARTIALLY_DISPOSED").contains(document.getStatus()),
                "inventory scrap document is not ready for disposition");
        document = requireDocumentForUpdate(tenantId, command.scrapId(), document.getStatus());
        InventoryScrapBatch batch = command.dispositionBatch();
        require(batch.lines() != null && !batch.lines().isEmpty(), "disposition batch lines are required");
        String batchId = valueOrUuid(batch.batchId());
        String batchNo = valueOrCode(batch.batchNo(), "SCRAPB");
        BigDecimal batchDisposed = ZERO;
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        for (NormalizedDispositionLine line : batch.lines()) {
            require(lineNumbers.add(line.lineNumber()), "duplicate disposition lineNumber");
            batchDisposed = batchDisposed.add(line.disposedQuantity());
        }
        require(mapper.insertDispositionBatch(new InventoryScrapDispositionBatchDO()
                .setBatchId(batchId).setTenantId(tenantId).setScrapId(document.getScrapId()).setBatchNo(batchNo)
                .setDispositionType(batch.dispositionType()).setProofType(batch.proofType()).setProofRef(batch.proofRef())
                .setStatus("COMPLETED").setTotalDisposedQuantity(batchDisposed).setLineCount(batch.lines().size())
                .setVersion(1L).setExecutedByPrincipalId(command.actorPrincipalId()).setRemark(batch.remark())
                .setOccurredAt(now).setCreatedAt(now).setUpdatedAt(now)) == 1,
                "failed to persist inventory scrap disposition batch");

        int processed = 0;
        BigDecimal documentDisposed = scaled(document.getTotalDisposedQuantity());
        for (NormalizedDispositionLine lineInput : batch.lines()) {
            InventoryScrapLineDO line = requireNonNull(mapper.selectLineForUpdate(tenantId, document.getScrapId(), lineInput.lineNumber()),
                    "inventory scrap line not found");
            require(Set.of("APPROVED", "PARTIALLY_DISPOSED").contains(line.getStatus()),
                    "inventory scrap line is not ready for disposition");
            BigDecimal nextDisposed = scaled(line.getDisposedQuantity()).add(lineInput.disposedQuantity());
            require(nextDisposed.compareTo(line.getRequestedQuantity()) <= 0,
                    "disposed quantity exceeds requested quantity");
            InventoryScrapDispositionResult inventory = inventoryScrapDispositionApi.execute(
                    InventoryScrapDispositionCommand.builder()
                            .idempotencyKey(command.idempotencyKey() + ":line:" + lineInput.lineNumber())
                            .sourceEventId(command.sourceEventId())
                            .dispositionLineId(valueOrUuid(lineInput.dispositionLineId()))
                            .scrapDocumentId(document.getScrapId())
                            .scrapLineId(line.getLineId())
                            .dispositionBatchId(batchId)
                            .ownerType(document.getOwnerType())
                            .ownerId(document.getOwnerId())
                            .canonicalSkuId(line.getCanonicalSkuId())
                            .warehouseId(document.getWarehouseId())
                            .locationId(line.getLocationId())
                            .lotId(line.getLotId())
                            .stockStatus(line.getStockStatus())
                            .qualityStatus(line.getQualityStatus())
                            .baseUomCode(line.getBaseUomCode())
                            .quantity(lineInput.disposedQuantity())
                            .businessNo(document.getScrapCode())
                            .occurredAt(command.occurredAt())
                            .build());
            String nextStatus = nextDisposed.compareTo(line.getRequestedQuantity()) == 0 ? "DISPOSED" : "PARTIALLY_DISPOSED";
            require(mapper.updateLineDispositionCas(tenantId, line.getLineId(), line.getVersion(), line.getVersion() + 1,
                    nextDisposed, nextStatus, now) == 1, "inventory scrap line disposition version conflict");
            require(mapper.insertDispositionLine(new InventoryScrapDispositionLineDO()
                    .setDispositionLineId(valueOrUuid(lineInput.dispositionLineId())).setTenantId(tenantId)
                    .setBatchId(batchId).setScrapId(document.getScrapId()).setScrapLineId(line.getLineId())
                    .setLineNumber(line.getLineNumber()).setCanonicalSkuId(line.getCanonicalSkuId())
                    .setLocationId(line.getLocationId()).setLotId(line.getLotId())
                    .setStockStatus(line.getStockStatus()).setQualityStatus(line.getQualityStatus())
                    .setBaseUomCode(line.getBaseUomCode()).setDisposedQuantity(lineInput.disposedQuantity())
                    .setCumulativeDisposedQuantity(nextDisposed)
                    .setInventoryIdempotencyKey(command.idempotencyKey() + ":line:" + lineInput.lineNumber())
                    .setInventoryOperationId(inventory.getOperationId())
                    .setInventoryLedgerTransactionId(inventory.getLedgerTransactionId())
                    .setInventoryBalanceId(inventory.getBalanceId())
                    .setStatus("COMPLETED").setVersion(1L).setRemark(lineInput.remark()).setOccurredAt(now)
                    .setCreatedAt(now).setUpdatedAt(now)) == 1, "failed to persist disposition line");
            processed++;
        }
        documentDisposed = documentDisposed.add(batchDisposed);
        String documentStatus = documentDisposed.compareTo(document.getTotalRequestedQuantity()) == 0
                ? "DISPOSED" : "PARTIALLY_DISPOSED";
        long nextVersion = document.getVersion() + 1;
        require(mapper.updateDispositionCas(tenantId, document.getScrapId(), document.getVersion(),
                nextVersion, documentDisposed, documentStatus, now) == 1,
                "inventory scrap document disposition version conflict");
        insertHistory(tenantId, operationId, document.getScrapId(), documentStatus, nextVersion,
                command.actorPrincipalId(), batch.remark(), now);
        return result(document.getScrapId(), document.getScrapCode(), documentStatus, batchId, batchNo, processed, nextVersion);
    }

    private InventoryScrapResult complete(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        InventoryScrapDocumentDO document = requireDocumentForUpdate(tenantId, command.scrapId(), "DISPOSED");
        require(document.getTotalDisposedQuantity().compareTo(document.getTotalRequestedQuantity()) == 0,
                "inventory scrap document is not fully disposed");
        List<InventoryScrapLineDO> lines = mapper.selectLines(tenantId, document.getScrapId());
        for (InventoryScrapLineDO line : lines) {
            require(line.getDisposedQuantity().compareTo(line.getRequestedQuantity()) == 0,
                    "inventory scrap line is not fully disposed");
            require(mapper.updateLineStatusCas(tenantId, line.getLineId(), line.getVersion(), line.getVersion() + 1,
                    "COMPLETED", now) == 1, "inventory scrap line complete version conflict");
        }
        long nextVersion = document.getVersion() + 1;
        require(mapper.completeDocumentCas(tenantId, document.getScrapId(), document.getVersion(),
                nextVersion, "COMPLETED", command.actorPrincipalId(), now) == 1,
                "inventory scrap document complete version conflict");
        insertHistory(tenantId, operationId, document.getScrapId(), "COMPLETED", nextVersion,
                command.actorPrincipalId(), command.remark(), now);
        return result(document.getScrapId(), document.getScrapCode(), "COMPLETED", null, null, lines.size(), nextVersion);
    }

    private void insertHistory(Long tenantId, Long operationId, String scrapId, String status, Long version,
                               String actorPrincipalId, String note, LocalDateTime now) {
        require(DOCUMENT_STATUSES.contains(status), "invalid inventory scrap status");
        require(mapper.insertHistory(new InventoryScrapHistoryDO()
                .setTenantId(tenantId).setScrapId(scrapId).setStatus(status).setStatusVersion(version)
                .setActorPrincipalId(actorPrincipalId).setNote(note).setOperationId(operationId)
                .setChangedAt(now)) == 1, "failed to persist inventory scrap history");
    }

    private InventoryScrapDocumentDO requireDocument(Long tenantId, String scrapId) {
        return requireNonNull(mapper.selectDocument(tenantId, requireText(scrapId, "scrapId", 128)),
                "inventory scrap document not found");
    }

    private InventoryScrapDocumentDO requireDocumentForUpdate(Long tenantId, String scrapId, String expectedStatus) {
        InventoryScrapDocumentDO document = requireNonNull(mapper.selectDocumentForUpdate(tenantId,
                requireText(scrapId, "scrapId", 128)), "inventory scrap document not found");
        require(expectedStatus.equals(document.getStatus()), "inventory scrap document is not in expected status");
        return document;
    }

    private static InventoryScrapResult result(String scrapId, String scrapCode, String status,
                                               String batchId, String batchNo, int processedLineCount,
                                               long aggregateVersion) {
        return InventoryScrapResult.builder()
                .scrapId(scrapId).scrapCode(scrapCode).scrapStatus(status)
                .batchId(batchId).batchNo(batchNo).processedLineCount(processedLineCount)
                .aggregateVersion(aggregateVersion).duplicate(false).build();
    }

    private static NormalizedCommand normalize(InventoryScrapCommand command) {
        require(command != null, "inventory scrap command is required");
        List<NormalizedLine> lines = command.getLines() == null ? null : command.getLines().stream()
                .map(line -> new NormalizedLine(
                        normalizeText(line.getLineId(), 128),
                        line.getLineNumber(),
                        requireText(line.getCanonicalSkuId(), "canonicalSkuId", 128),
                        requireText(line.getLocationId(), "locationId", 128),
                        normalizeText(line.getLotId(), 128),
                        validateStatus(line.getStockStatus(), STOCK_STATUSES, "stockStatus"),
                        validateStatus(line.getQualityStatus(), QUALITY_STATUSES, "qualityStatus"),
                        normalizeUpper(line.getBaseUomCode(), "baseUomCode"),
                        scaled(requireNonNull(line.getRequestedQuantity(), "requestedQuantity is required")),
                        validateStatus(line.getEvidenceType(), EVIDENCE_TYPES, "evidenceType"),
                        requireText(line.getEvidenceRef(), "evidenceRef", 128),
                        normalizeText(line.getRemark(), 255)))
                .toList();
        InventoryScrapBatch batch = null;
        if (command.getDispositionBatch() != null) {
            batch = new InventoryScrapBatch(
                    normalizeText(command.getDispositionBatch().getBatchId(), 128),
                    normalizeText(command.getDispositionBatch().getBatchNo(), 64),
                    validateStatus(command.getDispositionBatch().getDispositionType(), DISPOSITION_TYPES, "dispositionType"),
                    normalizeUpper(command.getDispositionBatch().getProofType(), "proofType"),
                    requireText(command.getDispositionBatch().getProofRef(), "proofRef", 128),
                    normalizeText(command.getDispositionBatch().getRemark(), 255),
                    command.getDispositionBatch().getLines().stream().map(line -> new NormalizedDispositionLine(
                            normalizeText(line.getDispositionLineId(), 128),
                            requirePositive(line.getLineNumber(), "lineNumber"),
                            scaled(requireNonNull(line.getDisposedQuantity(), "disposedQuantity is required")),
                            normalizeText(line.getRemark(), 255))).toList());
        }
        return new NormalizedCommand(
                requireNonNull(command.getOperation(), "operation is required"),
                requireText(command.getIdempotencyKey(), "idempotencyKey", 128),
                normalizeText(command.getSourceEventId(), 128),
                requireText(command.getActorPrincipalId(), "actorPrincipalId", 128),
                requireNonNull(command.getOccurredAt(), "occurredAt is required"),
                normalizeText(command.getScrapId(), 128),
                normalizeText(command.getScrapCode(), 64),
                normalizeUpper(command.getReasonCode(), "reasonCode"),
                normalizeText(command.getRemark(), 255),
                normalizeUpper(command.getOwnerType(), "ownerType"),
                requireText(command.getOwnerId(), "ownerId", 128),
                requireText(command.getWarehouseId(), "warehouseId", 128),
                lines,
                batch);
    }

    private static String fingerprint(Long tenantId, NormalizedCommand command) {
        List<String> parts = new ArrayList<>();
        parts.add(tenantId.toString());
        parts.add(command.operation().name());
        parts.add(command.idempotencyKey());
        parts.add(nullToEmpty(command.sourceEventId()));
        parts.add(command.actorPrincipalId());
        parts.add(nullToEmpty(command.scrapId()));
        parts.add(nullToEmpty(command.scrapCode()));
        parts.add(command.reasonCode());
        parts.add(nullToEmpty(command.remark()));
        parts.add(command.ownerType());
        parts.add(command.ownerId());
        parts.add(command.warehouseId());
        parts.add(command.occurredAt().toString());
        if (command.lines() != null) {
            for (NormalizedLine line : command.lines()) {
                parts.add(nullToEmpty(line.lineId()));
                parts.add(line.lineNumber() == null ? "" : line.lineNumber().toString());
                parts.add(line.canonicalSkuId());
                parts.add(line.locationId());
                parts.add(nullToEmpty(line.lotId()));
                parts.add(line.stockStatus());
                parts.add(line.qualityStatus());
                parts.add(line.baseUomCode());
                parts.add(line.requestedQuantity().toPlainString());
                parts.add(line.evidenceType());
                parts.add(line.evidenceRef());
                parts.add(nullToEmpty(line.remark()));
            }
        }
        if (command.dispositionBatch() != null) {
            parts.add(nullToEmpty(command.dispositionBatch().batchId()));
            parts.add(nullToEmpty(command.dispositionBatch().batchNo()));
            parts.add(command.dispositionBatch().dispositionType());
            parts.add(command.dispositionBatch().proofType());
            parts.add(command.dispositionBatch().proofRef());
            parts.add(nullToEmpty(command.dispositionBatch().remark()));
            for (NormalizedDispositionLine line : command.dispositionBatch().lines()) {
                parts.add(nullToEmpty(line.dispositionLineId()));
                parts.add(line.lineNumber().toString());
                parts.add(line.disposedQuantity().toPlainString());
                parts.add(nullToEmpty(line.remark()));
            }
        }
        return DigestUtil.sha256Hex(String.join("\u001f", parts));
    }

    private static LocalDateTime toUtc(Instant occurredAt) {
        return LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
    }

    private static String valueOrUuid(String value) {
        return value == null ? UUID.randomUUID().toString() : value;
    }

    private static String valueOrCode(String value, String prefix) {
        return value == null ? prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT) : value;
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = normalizeText(value, maxLength);
        require(normalized != null, field + " is required");
        return normalized;
    }

    private static String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        require(normalized.length() <= maxLength, "field exceeds max length: " + maxLength);
        return normalized;
    }

    private static String normalizeUpper(String value, String field) {
        String normalized = normalizeText(value, 64);
        require(normalized != null, field + " is required");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String validateStatus(String value, Set<String> allowed, String field) {
        String normalized = normalizeUpper(value, field);
        require(allowed.contains(normalized), field + " is not allowed");
        return normalized;
    }

    private static Integer requirePositive(Integer value, String field) {
        require(value != null && value > 0, field + " must be positive");
        return value;
    }

    private static BigDecimal scaled(BigDecimal value) {
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("quantity must use scale 6", ex);
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record NormalizedCommand(
            InventoryScrapOperation operation,
            String idempotencyKey,
            String sourceEventId,
            String actorPrincipalId,
            Instant occurredAt,
            String scrapId,
            String scrapCode,
            String reasonCode,
            String remark,
            String ownerType,
            String ownerId,
            String warehouseId,
            List<NormalizedLine> lines,
            InventoryScrapBatch dispositionBatch) {
    }

    private record NormalizedLine(
            String lineId,
            Integer lineNumber,
            String canonicalSkuId,
            String locationId,
            String lotId,
            String stockStatus,
            String qualityStatus,
            String baseUomCode,
            BigDecimal requestedQuantity,
            String evidenceType,
            String evidenceRef,
            String remark) {
    }

    private record InventoryScrapBatch(
            String batchId,
            String batchNo,
            String dispositionType,
            String proofType,
            String proofRef,
            String remark,
            List<NormalizedDispositionLine> lines) {
    }

    private record NormalizedDispositionLine(
            String dispositionLineId,
            Integer lineNumber,
            BigDecimal disposedQuantity,
            String remark) {
    }
}
