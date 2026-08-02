package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotQuery;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockCountStoreMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
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
public class StockCountCommandServiceImpl implements StockCountCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_-]{0,63}");
    static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    static final BigDecimal ZERO = new BigDecimal("0.000000");
    static final Set<String> COUNT_MODES = Set.of("OPEN_COUNT", "BLIND_COUNT");
    static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");
    static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");

    private final StockCountStoreMapper mapper;
    private final InventoryStockCountSnapshotApi inventorySnapshotApi;
    private final InventoryStockCountAdjustmentApi inventoryAdjustmentApi;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final WarehouseActorPrincipalPort actorPrincipalPort;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockCountResult execute(StockCountCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.operation().name(), requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve stock count operation");
        StockCountOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "stock count operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different stock count payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing stock count operation is incomplete");
            StockCountResult replay = JsonUtils.parseObject(operation.getResultJson(), StockCountResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        actorPrincipalPort.requireActive(command.actorPrincipalId());
        StockCountResult result = switch (command.operation()) {
            case CREATE_DRAFT -> createDraft(tenantId, operationId, command, now);
            case SUBMIT -> submit(tenantId, operationId, command, now);
            case START_COUNTING -> startCounting(tenantId, operationId, command, now);
            case RECORD_COUNT_BATCH -> recordCountBatch(tenantId, operationId, command, now);
            case APPROVE_DIFFERENCE -> approveDifference(tenantId, operationId, command, now);
            case ADJUST -> adjust(tenantId, operationId, command, now);
            case COMPLETE -> complete(tenantId, operationId, command, now);
            case CANCEL -> cancel(tenantId, operationId, command, now);
        };
        result.setOperationId(operationId);
        require(mapper.markOperationSucceeded(operationId, tenantId, result.getStockCountId(),
                        JsonUtils.toJsonString(result), now) == 1,
                "stock count operation completion conflict");
        return result;
    }

    private StockCountResult createDraft(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        require(command.lines() != null && !command.lines().isEmpty(), "stock count draft must contain at least one line");
        if (command.sourceBusinessType() != null && command.sourceBusinessRef() != null) {
            require(mapper.selectBySourceBusiness(tenantId, command.sourceBusinessType(), command.sourceBusinessRef()) == null,
                    "stock count already exists for this source business");
        }
        String stockCountId = valueOrUuid(command.stockCountId());
        String stockCountCode = valueOrCode(command.stockCountCode(), "STKCNT");
        StockCountDO stockCount = new StockCountDO()
                .setStockCountId(stockCountId)
                .setTenantId(tenantId)
                .setStockCountCode(stockCountCode)
                .setCountMode(command.countMode())
                .setScopeType(command.scopeType())
                .setScopeLabel(command.scopeLabel())
                .setSourceBusinessType(command.sourceBusinessType())
                .setSourceBusinessRef(command.sourceBusinessRef())
                .setReasonCode(command.reasonCode())
                .setRemark(command.remark())
                .setStatus("DRAFT")
                .setVersion(1L)
                .setFreezeLedgerTransactionId(0L)
                .setFreezeCapturedAt(null)
                .setLineCount(command.lines().size())
                .setCountedLineCount(0)
                .setDifferenceLineCount(0)
                .setCreatedByPrincipalId(command.actorPrincipalId())
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertStockCount(stockCount) == 1, "failed to persist stock count draft");
        int generatedLineNumber = 10;
        for (NormalizedLine line : command.lines()) {
            validateScopeLine(line);
            require(mapper.insertLine(new StockCountLineDO()
                    .setLineId(line.lineId() == null ? stockCountId + ":line:" + generatedLineNumber : line.lineId())
                    .setTenantId(tenantId)
                    .setStockCountId(stockCountId)
                    .setLineNumber(line.lineNumber() == null ? generatedLineNumber : line.lineNumber())
                    .setOwnerType(line.ownerType())
                    .setOwnerId(line.ownerId())
                    .setCanonicalSkuId(line.canonicalSkuId())
                    .setWarehouseId(line.warehouseId())
                    .setLocationId(line.locationId())
                    .setLotId(line.lotId())
                    .setStockStatus(line.stockStatus())
                    .setQualityStatus(line.qualityStatus())
                    .setBaseUomCode(line.baseUomCode())
                    .setBalanceId(null)
                    .setBookOnHandQuantity(ZERO)
                    .setBookReservedQuantity(ZERO)
                    .setBookInTransitQuantity(ZERO)
                    .setBookAvailableQuantity(ZERO)
                    .setBookAggregateVersion(0L)
                    .setCountedOnHandQuantity(null)
                    .setDifferenceQuantity(null)
                    .setCountStatus("DRAFT")
                    .setRemark(line.remark())
                    .setVersion(1L)
                    .setFreezeCapturedAt(null)
                    .setCreatedAt(now)
                    .setUpdatedAt(now)) == 1,
                    "failed to persist stock count line");
            generatedLineNumber += 10;
        }
        insertHistory(tenantId, operationId, stockCountId, "DRAFT", 1L, "DRAFT", "盘点草稿",
                command.actorPrincipalId(), command.remark(), now);
        appendEvent(tenantId, command, stockCountId, 1L, "warehouse.stock_count.draft_created",
                Map.of("stock_count_code", stockCountCode, "count_mode", command.countMode(), "line_count", command.lines().size()));
        return StockCountResult.builder()
                .stockCountId(stockCountId)
                .stockCountCode(stockCountCode)
                .status("DRAFT")
                .currentStageCode("DRAFT")
                .currentStageLabel("盘点草稿")
                .aggregateVersion(1L)
                .freezeLedgerTransactionId(0L)
                .processedLineCount(command.lines().size())
                .differenceLineCount(0)
                .duplicate(false)
                .build();
    }

    private StockCountResult submit(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require("DRAFT".equals(stockCount.getStatus()), "stock count can only be submitted from DRAFT");
        List<StockCountLineDO> lines = mapper.selectLines(tenantId, stockCount.getStockCountId());
        require(!lines.isEmpty(), "stock count has no lines");
        Long freezeLedgerId = mapper.selectFreezeLedgerTransactionId(tenantId);
        LocalDateTime freezeCapturedAt = now;
        for (StockCountLineDO line : lines) {
            InventoryStockCountSnapshotView snapshot = inventorySnapshotApi.requireSnapshot(InventoryStockCountSnapshotQuery.builder()
                    .ownerType(line.getOwnerType())
                    .ownerId(line.getOwnerId())
                    .canonicalSkuId(line.getCanonicalSkuId())
                    .warehouseId(line.getWarehouseId())
                    .locationId(line.getLocationId())
                    .lotId(line.getLotId())
                    .stockStatus(line.getStockStatus())
                    .qualityStatus(line.getQualityStatus())
                    .baseUomCode(line.getBaseUomCode())
                    .build());
            BigDecimal reserved = scaled(snapshot.getReservedQuantity());
            BigDecimal inTransit = scaled(snapshot.getInTransitQuantity());
            require(reserved.compareTo(ZERO) == 0, "stock count requires zero reserved quantity");
            require(inTransit.compareTo(ZERO) == 0, "stock count requires zero in-transit quantity");
            BigDecimal onHand = scaled(snapshot.getOnHandQuantity());
            BigDecimal available = onHand.subtract(reserved).setScale(6, RoundingMode.HALF_UP);
            require(mapper.updateLineSnapshotCas(tenantId, line.getLineId(), line.getVersion(), snapshot.getBalanceId(),
                            onHand, reserved, inTransit, available, snapshot.getAggregateVersion(), "SUBMITTED", freezeCapturedAt, now) == 1,
                    "stock count line snapshot version conflict");
        }
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        "SUBMITTED", freezeLedgerId, freezeCapturedAt, lines.size(), 0, 0, stockCount.getRemark(), now) == 1,
                "stock count submit version conflict");
        insertHistory(tenantId, operationId, stockCount.getStockCountId(), "SUBMITTED", nextVersion, "SUBMITTED",
                "盘点已提交", command.actorPrincipalId(), stockCount.getRemark(), now);
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.submitted",
                Map.of("freeze_ledger_transaction_id", freezeLedgerId, "line_count", lines.size()));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), "SUBMITTED",
                "SUBMITTED", "盘点已提交", nextVersion, freezeLedgerId, null, null, lines.size(), 0);
    }

    private StockCountResult startCounting(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require("SUBMITTED".equals(stockCount.getStatus()), "stock count can only enter COUNTING from SUBMITTED");
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        "COUNTING", stockCount.getFreezeLedgerTransactionId(), stockCount.getFreezeCapturedAt(),
                        stockCount.getLineCount(), stockCount.getCountedLineCount(), stockCount.getDifferenceLineCount(),
                        stockCount.getRemark(), now) == 1,
                "stock count counting version conflict");
        insertHistory(tenantId, operationId, stockCount.getStockCountId(), "COUNTING", nextVersion, "COUNTING",
                "盘点中", command.actorPrincipalId(), stockCount.getRemark(), now);
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.counting_started",
                Map.of("line_count", stockCount.getLineCount()));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), "COUNTING",
                "COUNTING", "盘点中", nextVersion, stockCount.getFreezeLedgerTransactionId(), null, null,
                stockCount.getLineCount(), stockCount.getDifferenceLineCount());
    }

    private StockCountResult recordCountBatch(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require("COUNTING".equals(stockCount.getStatus()), "stock count can only record batches in COUNTING");
        StockCountCommand.CountBatchDefinition batch = requireNonNull(command.countBatch(), "countBatch is required");
        require(batch.getLines() != null && !batch.getLines().isEmpty(), "count batch must contain at least one line");
        String batchId = valueOrUuid(batch.getBatchId());
        String batchNo = valueOrCode(batch.getBatchNo(), "STKCBT");
        require(mapper.insertExecutionBatch(new StockCountExecutionBatchDO()
                        .setBatchId(batchId).setTenantId(tenantId).setStockCountId(stockCount.getStockCountId())
                        .setBatchNo(batchNo).setStatus("RECORDED").setLineCount(batch.getLines().size())
                        .setCountedByPrincipalId(command.actorPrincipalId()).setRemark(batch.getRemark()).setVersion(1L)
                        .setOccurredAt(now).setCreatedAt(now).setUpdatedAt(now)) == 1,
                "failed to persist stock count execution batch");

        int countedLines = stockCount.getCountedLineCount();
        int differenceLines = 0;
        List<StockCountLineDO> allLines = mapper.selectLines(tenantId, stockCount.getStockCountId());
        Map<String, StockCountLineDO> lineById = new LinkedHashMap<>();
        for (StockCountLineDO line : allLines) {
            if ("COUNTED".equals(line.getCountStatus()) || "ADJUSTED".equals(line.getCountStatus())) {
                countedLines++;
                if (line.getDifferenceQuantity() != null && line.getDifferenceQuantity().compareTo(ZERO) != 0) {
                    differenceLines++;
                }
            }
            lineById.put(line.getLineId(), line);
        }

        int newlyCounted = 0;
        for (StockCountCommand.CountLineDefinition countLine : batch.getLines()) {
            StockCountLineDO line = requireNonNull(mapper.selectLineForUpdate(tenantId,
                    requireText(countLine.getStockCountLineId(), "stockCountLineId")), "stock count line not found");
            require(Objects.equals(line.getStockCountId(), stockCount.getStockCountId()),
                    "count batch line does not belong to stock count");
            require("SUBMITTED".equals(line.getCountStatus()), "stock count line is not awaiting count");
            BigDecimal counted = scaled(requireNonNull(countLine.getCountedOnHandQuantity(), "countedOnHandQuantity is required"));
            require(counted.signum() >= 0, "countedOnHandQuantity cannot be negative");
            BigDecimal difference = counted.subtract(scaled(line.getBookOnHandQuantity())).setScale(6, RoundingMode.HALF_UP);
            require(mapper.updateLineCountCas(tenantId, line.getLineId(), line.getVersion(), counted, difference,
                            "COUNTED", command.actorPrincipalId(), now, countLine.getRemark(), now) == 1,
                    "stock count line count version conflict");
            require(mapper.insertExecutionLine(new StockCountExecutionLineDO()
                            .setExecutionLineId(valueOrUuid(countLine.getExecutionLineId()))
                            .setTenantId(tenantId)
                            .setBatchId(batchId)
                            .setStockCountId(stockCount.getStockCountId())
                            .setStockCountLineId(line.getLineId())
                            .setCountedOnHandQuantity(counted)
                            .setDifferenceQuantity(difference)
                            .setRemark(countLine.getRemark())
                            .setCreatedAt(now)) == 1,
                    "failed to persist stock count execution line");
            newlyCounted++;
        }

        List<StockCountLineDO> refreshedLines = mapper.selectLines(tenantId, stockCount.getStockCountId());
        int totalCounted = 0;
        int totalDifference = 0;
        boolean allCounted = true;
        for (StockCountLineDO line : refreshedLines) {
            if ("COUNTED".equals(line.getCountStatus()) || "ADJUSTED".equals(line.getCountStatus())) {
                totalCounted++;
                if (line.getDifferenceQuantity() != null && line.getDifferenceQuantity().compareTo(ZERO) != 0) {
                    totalDifference++;
                }
            } else {
                allCounted = false;
            }
        }
        String nextStatus = allCounted ? "COUNTED" : "COUNTING";
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        nextStatus, stockCount.getFreezeLedgerTransactionId(), stockCount.getFreezeCapturedAt(),
                        stockCount.getLineCount(), totalCounted, totalDifference, stockCount.getRemark(), now) == 1,
                "stock count batch version conflict");
        if (allCounted) {
            insertHistory(tenantId, operationId, stockCount.getStockCountId(), "COUNTED", nextVersion, "COUNTED",
                    "盘点完成待差异审批", command.actorPrincipalId(), batch.getRemark(), now);
        }
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.batch_recorded",
                Map.of("batch_id", batchId, "batch_no", batchNo, "processed_line_count", newlyCounted,
                        "counted_line_count", totalCounted, "difference_line_count", totalDifference));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), nextStatus,
                allCounted ? "COUNTED" : "COUNTING",
                allCounted ? "盘点完成待差异审批" : "盘点中", nextVersion, stockCount.getFreezeLedgerTransactionId(),
                batchId, batchNo, newlyCounted, totalDifference);
    }

    private StockCountResult approveDifference(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require("COUNTED".equals(stockCount.getStatus()), "stock count can only approve differences from COUNTED");
        List<StockCountLineDO> lines = mapper.selectLines(tenantId, stockCount.getStockCountId());
        require(lines.stream().allMatch(line -> "COUNTED".equals(line.getCountStatus())), "all stock count lines must be COUNTED");
        BigDecimal totalBook = ZERO;
        BigDecimal totalCounted = ZERO;
        BigDecimal totalDifference = ZERO;
        for (StockCountLineDO line : lines) {
            totalBook = totalBook.add(scaled(line.getBookOnHandQuantity()));
            totalCounted = totalCounted.add(scaled(line.getCountedOnHandQuantity()));
            totalDifference = totalDifference.add(scaled(line.getDifferenceQuantity()));
        }
        require(mapper.insertApproval(new StockCountDifferenceApprovalDO()
                        .setApprovalId(UUID.randomUUID().toString())
                        .setTenantId(tenantId)
                        .setStockCountId(stockCount.getStockCountId())
                        .setApprovalType("DIFFERENCE_APPROVAL")
                        .setApprovedByPrincipalId(command.actorPrincipalId())
                        .setTotalBookOnHandQuantity(totalBook)
                        .setTotalCountedOnHandQuantity(totalCounted)
                        .setTotalDifferenceQuantity(totalDifference)
                        .setRemark(command.approvalRemark())
                        .setApprovedAt(now)
                        .setCreatedAt(now)) == 1,
                "failed to persist stock count difference approval");
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        "DIFFERENCE_APPROVED", stockCount.getFreezeLedgerTransactionId(), stockCount.getFreezeCapturedAt(),
                        stockCount.getLineCount(), stockCount.getCountedLineCount(), stockCount.getDifferenceLineCount(),
                        stockCount.getRemark(), now) == 1,
                "stock count difference approval version conflict");
        insertHistory(tenantId, operationId, stockCount.getStockCountId(), "DIFFERENCE_APPROVED", nextVersion,
                "DIFFERENCE_APPROVED", "差异已批准", command.actorPrincipalId(), command.approvalRemark(), now);
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.difference_approved",
                Map.of("total_book_on_hand_quantity", decimal(totalBook),
                        "total_counted_on_hand_quantity", decimal(totalCounted),
                        "total_difference_quantity", decimal(totalDifference)));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), "DIFFERENCE_APPROVED",
                "DIFFERENCE_APPROVED", "差异已批准", nextVersion, stockCount.getFreezeLedgerTransactionId(), null, null,
                stockCount.getLineCount(), stockCount.getDifferenceLineCount());
    }

    private StockCountResult adjust(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require("DIFFERENCE_APPROVED".equals(stockCount.getStatus()),
                "stock count can only adjust from DIFFERENCE_APPROVED");
        List<StockCountLineDO> lines = mapper.selectLines(tenantId, stockCount.getStockCountId());
        int processed = 0;
        for (StockCountLineDO line : lines) {
            if (line.getDifferenceQuantity() == null || line.getDifferenceQuantity().compareTo(ZERO) == 0) {
                if (!"ADJUSTED".equals(line.getCountStatus())) {
                    require(mapper.updateLineAdjustmentCas(tenantId, line.getLineId(), line.getVersion(),
                                    line.getLineId() + ":noop", null, line.getBookAggregateVersion(), now) == 1,
                            "stock count zero-difference line version conflict");
                    processed++;
                }
                continue;
            }
            require("COUNTED".equals(line.getCountStatus()), "stock count line is not ready for adjustment");
            InventoryStockCountAdjustmentResult adjustment = inventoryAdjustmentApi.execute(
                    InventoryStockCountAdjustmentCommand.builder()
                            .idempotencyKey(command.idempotencyKey() + ":line:" + line.getLineId())
                            .sourceEventId(eventId(command.sourceEventId(), "adjust", line.getLineId()))
                            .stockCountId(stockCount.getStockCountId())
                            .stockCountLineId(line.getLineId())
                            .ownerType(line.getOwnerType())
                            .ownerId(line.getOwnerId())
                            .canonicalSkuId(line.getCanonicalSkuId())
                            .warehouseId(line.getWarehouseId())
                            .locationId(line.getLocationId())
                            .lotId(line.getLotId())
                            .stockStatus(line.getStockStatus())
                            .qualityStatus(line.getQualityStatus())
                            .baseUomCode(line.getBaseUomCode())
                            .bookOnHandQuantity(line.getBookOnHandQuantity())
                            .countedOnHandQuantity(line.getCountedOnHandQuantity())
                            .adjustmentQuantity(line.getDifferenceQuantity())
                            .businessType("WAREHOUSE_STOCK_COUNT")
                            .businessId(stockCount.getStockCountId())
                            .businessItemId(line.getLineId())
                            .businessNo(stockCount.getStockCountCode())
                            .correlationId(command.correlationId())
                            .causationId(command.causationId())
                            .occurredAt(command.occurredAt())
                            .build());
            require(mapper.updateLineAdjustmentCas(tenantId, line.getLineId(), line.getVersion(),
                            adjustment.getAdjustmentId(), adjustment.getLedgerTransactionId(), adjustment.getAggregateVersion(), now) == 1,
                    "stock count line adjustment version conflict");
            processed++;
        }
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        "ADJUSTED", stockCount.getFreezeLedgerTransactionId(), stockCount.getFreezeCapturedAt(),
                        stockCount.getLineCount(), stockCount.getLineCount(), stockCount.getDifferenceLineCount(),
                        stockCount.getRemark(), now) == 1,
                "stock count adjustment version conflict");
        insertHistory(tenantId, operationId, stockCount.getStockCountId(), "ADJUSTED", nextVersion, "ADJUSTED",
                "盘点差异已入账", command.actorPrincipalId(), stockCount.getRemark(), now);
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.adjusted",
                Map.of("processed_line_count", processed, "difference_line_count", stockCount.getDifferenceLineCount()));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), "ADJUSTED",
                "ADJUSTED", "盘点差异已入账", nextVersion, stockCount.getFreezeLedgerTransactionId(), null, null,
                processed, stockCount.getDifferenceLineCount());
    }

    private StockCountResult complete(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require("ADJUSTED".equals(stockCount.getStatus()), "stock count can only complete from ADJUSTED");
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        "COMPLETED", stockCount.getFreezeLedgerTransactionId(), stockCount.getFreezeCapturedAt(),
                        stockCount.getLineCount(), stockCount.getCountedLineCount(), stockCount.getDifferenceLineCount(),
                        stockCount.getRemark(), now) == 1,
                "stock count completion version conflict");
        insertHistory(tenantId, operationId, stockCount.getStockCountId(), "COMPLETED", nextVersion, "COMPLETED",
                "盘点已完成", command.actorPrincipalId(), stockCount.getRemark(), now);
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.completed",
                Map.of("line_count", stockCount.getLineCount(), "difference_line_count", stockCount.getDifferenceLineCount()));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), "COMPLETED",
                "COMPLETED", "盘点已完成", nextVersion, stockCount.getFreezeLedgerTransactionId(), null, null,
                stockCount.getLineCount(), stockCount.getDifferenceLineCount());
    }

    private StockCountResult cancel(Long tenantId, Long operationId, NormalizedCommand command, LocalDateTime now) {
        StockCountDO stockCount = requireNonNull(mapper.selectStockCountForUpdate(tenantId, command.stockCountId()),
                "stock count not found");
        require(!Set.of("ADJUSTED", "COMPLETED", "CANCELLED").contains(stockCount.getStatus()),
                "stock count cannot be cancelled in current status");
        long nextVersion = stockCount.getVersion() + 1;
        require(mapper.updateStockCountCas(tenantId, stockCount.getStockCountId(), stockCount.getVersion(), nextVersion,
                        "CANCELLED", stockCount.getFreezeLedgerTransactionId(), stockCount.getFreezeCapturedAt(),
                        stockCount.getLineCount(), stockCount.getCountedLineCount(), stockCount.getDifferenceLineCount(),
                        stockCount.getRemark(), now) == 1,
                "stock count cancellation version conflict");
        insertHistory(tenantId, operationId, stockCount.getStockCountId(), "CANCELLED", nextVersion, "CANCELLED",
                "盘点已取消", command.actorPrincipalId(), stockCount.getRemark(), now);
        appendEvent(tenantId, command, stockCount.getStockCountId(), nextVersion, "warehouse.stock_count.cancelled",
                Map.of("status", "CANCELLED"));
        return baseResult(stockCount.getStockCountId(), stockCount.getStockCountCode(), "CANCELLED",
                "CANCELLED", "盘点已取消", nextVersion, stockCount.getFreezeLedgerTransactionId(), null, null,
                stockCount.getCountedLineCount(), stockCount.getDifferenceLineCount());
    }

    private void appendEvent(Long tenantId, NormalizedCommand command, String aggregateId, long aggregateVersion,
                             String eventType, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(eventType)
                .schemaVersion(1)
                .sourceSystem("cloudmold-warehouse")
                .tenantId(tenantId)
                .aggregateType("stock_count")
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .eventSequence((short) 1)
                .occurredAt(command.occurredAt())
                .correlationId(command.correlationId())
                .causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey() + ":" + eventType)
                .payload(payload)
                .headers(Map.of("stock_count_id", aggregateId, "status", eventType))
                .destination("lakehouse")
                .build());
    }

    private void insertHistory(Long tenantId, Long operationId, String stockCountId, String status, long version,
                               String stageCode, String stageLabel, String changedByPrincipalId, String remark,
                               LocalDateTime now) {
        require(mapper.insertStatusHistory(new StockCountStatusHistoryDO()
                        .setHistoryId(UUID.randomUUID().toString())
                        .setTenantId(tenantId)
                        .setOperationId(operationId)
                        .setStockCountId(stockCountId)
                        .setStatus(status)
                        .setStatusVersion(version)
                        .setStageCode(stageCode)
                        .setStageLabel(stageLabel)
                        .setChangedByPrincipalId(changedByPrincipalId)
                        .setRemark(remark)
                        .setChangedAt(now)
                        .setCreatedAt(now)) == 1,
                "failed to persist stock count status history");
    }

    private StockCountResult baseResult(String stockCountId, String stockCountCode, String status, String stageCode,
                                        String stageLabel, long version, Long freezeLedgerId, String batchId,
                                        String batchNo, int processedLineCount, int differenceLineCount) {
        return StockCountResult.builder()
                .stockCountId(stockCountId)
                .stockCountCode(stockCountCode)
                .status(status)
                .currentStageCode(stageCode)
                .currentStageLabel(stageLabel)
                .aggregateVersion(version)
                .freezeLedgerTransactionId(freezeLedgerId)
                .batchId(batchId)
                .batchNo(batchNo)
                .processedLineCount(processedLineCount)
                .differenceLineCount(differenceLineCount)
                .duplicate(false)
                .build();
    }

    private void validateScopeLine(NormalizedLine line) {
        catalogSkuValidationApi.requireActiveSku(line.canonicalSkuId());
        warehouseValidationApi.requireActiveLocation(line.warehouseId(), line.locationId());
        require(STOCK_STATUSES.contains(line.stockStatus()), "unsupported stockStatus");
        require(QUALITY_STATUSES.contains(line.qualityStatus()), "unsupported qualityStatus");
    }

    private static NormalizedCommand normalize(StockCountCommand command) {
        require(command != null, "stock count command is required");
        StockCountOperation operation = requireNonNull(command.getOperation(), "operation is required");
        String countMode = command.getCountMode() == null ? null : requireUpper(command.getCountMode(), "countMode");
        if (operation == StockCountOperation.CREATE_DRAFT) {
            require(countMode != null && COUNT_MODES.contains(countMode), "unsupported countMode");
        }
        List<NormalizedLine> lines = null;
        if (command.getLines() != null) {
            lines = command.getLines().stream().map(line -> new NormalizedLine(
                    line.getLineId() == null || line.getLineId().isBlank() ? null : line.getLineId().trim(),
                    line.getLineNumber(),
                    requireUpper(line.getOwnerType(), "ownerType"),
                    requireText(line.getOwnerId(), "ownerId"),
                    requireText(line.getCanonicalSkuId(), "canonicalSkuId"),
                    requireText(line.getWarehouseId(), "warehouseId"),
                    requireText(line.getLocationId(), "locationId"),
                    line.getLotId() == null || line.getLotId().isBlank() ? null : line.getLotId().trim(),
                    requireUpper(line.getStockStatus(), "stockStatus"),
                    requireUpper(line.getQualityStatus(), "qualityStatus"),
                    requireUpper(line.getBaseUomCode(), "baseUomCode"),
                    line.getRemark())).toList();
        }
        return new NormalizedCommand(
                operation,
                requireText(command.getIdempotencyKey(), "idempotencyKey"),
                command.getSourceEventId() == null || command.getSourceEventId().isBlank()
                        ? null : command.getSourceEventId().trim(),
                command.getCorrelationId() == null || command.getCorrelationId().isBlank()
                        ? UUID.randomUUID().toString() : command.getCorrelationId().trim(),
                command.getCausationId() == null || command.getCausationId().isBlank()
                        ? UUID.randomUUID().toString() : command.getCausationId().trim(),
                command.getOccurredAt() == null ? Instant.now() : command.getOccurredAt(),
                command.getStockCountId() == null || command.getStockCountId().isBlank() ? null : command.getStockCountId().trim(),
                command.getStockCountCode() == null || command.getStockCountCode().isBlank() ? null : command.getStockCountCode().trim(),
                countMode,
                command.getScopeType() == null || command.getScopeType().isBlank() ? "EXPLICIT_LINES"
                        : requireUpper(command.getScopeType(), "scopeType"),
                command.getScopeLabel() == null || command.getScopeLabel().isBlank() ? "EXPLICIT_LINES" : command.getScopeLabel().trim(),
                command.getSourceBusinessType() == null || command.getSourceBusinessType().isBlank() ? null
                        : requireUpper(command.getSourceBusinessType(), "sourceBusinessType"),
                command.getSourceBusinessRef() == null || command.getSourceBusinessRef().isBlank() ? null
                        : requireText(command.getSourceBusinessRef(), "sourceBusinessRef"),
                command.getReasonCode() == null || command.getReasonCode().isBlank()
                        ? "STOCK_COUNT" : requireUpper(command.getReasonCode(), "reasonCode"),
                command.getRemark(),
                requireText(command.getActorPrincipalId(), "actorPrincipalId"),
                command.getApprovalRemark(),
                lines,
                command.getCountBatch());
    }

    static String fingerprint(Long tenantId, NormalizedCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("operation", command.operation().name());
        payload.put("stockCountId", command.stockCountId());
        payload.put("stockCountCode", command.stockCountCode());
        payload.put("countMode", command.countMode());
        payload.put("scopeType", command.scopeType());
        payload.put("scopeLabel", command.scopeLabel());
        payload.put("sourceBusinessType", command.sourceBusinessType());
        payload.put("sourceBusinessRef", command.sourceBusinessRef());
        payload.put("reasonCode", command.reasonCode());
        payload.put("lines", command.lines());
        payload.put("countBatch", command.countBatch());
        payload.put("approvalRemark", command.approvalRemark());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
    }

    private static String eventId(String sourceEventId, String phase, String suffix) {
        if (sourceEventId == null || sourceEventId.isBlank()) {
            return null;
        }
        return sourceEventId + ":" + phase + ":" + suffix;
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static String valueOrCode(String value, String prefix) {
        if (value != null && !value.isBlank()) {
            return requireCode(value.trim(), "code");
        }
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        return value.trim();
    }

    private static String requireUpper(String value, String field) {
        return requireText(value, field).toUpperCase(Locale.ROOT);
    }

    private static String requireCode(String value, String field) {
        require(SAFE_CODE.matcher(value).matches(), field + " format is invalid");
        return value;
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    record NormalizedLine(String lineId,
                          Integer lineNumber,
                          String ownerType,
                          String ownerId,
                          String canonicalSkuId,
                          String warehouseId,
                          String locationId,
                          String lotId,
                          String stockStatus,
                          String qualityStatus,
                          String baseUomCode,
                          String remark) {
    }

    record NormalizedCommand(StockCountOperation operation,
                             String idempotencyKey,
                             String sourceEventId,
                             String correlationId,
                             String causationId,
                             Instant occurredAt,
                             String stockCountId,
                             String stockCountCode,
                             String countMode,
                             String scopeType,
                             String scopeLabel,
                             String sourceBusinessType,
                             String sourceBusinessRef,
                             String reasonCode,
                             String remark,
                             String actorPrincipalId,
                             String approvalRemark,
                             List<NormalizedLine> lines,
                             StockCountCommand.CountBatchDefinition countBatch) {
    }
}
