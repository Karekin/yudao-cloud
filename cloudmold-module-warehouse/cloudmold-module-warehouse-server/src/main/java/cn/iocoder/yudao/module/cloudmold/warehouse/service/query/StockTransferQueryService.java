package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockTransferPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionBatchDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferExecutionLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferOrderLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferRequestLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockTransferStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferQueryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StockTransferQueryService implements StockTransferQueryApi {

    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final StockTransferStoreMapper mapper;
    private final StockTransferQueryMapper queryMapper;

    @Override
    public StockTransferView requireByRequestId(String requestId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        StockTransferRequestDO request = mapper.selectRequestByRequestId(tenantId, requireText(requestId, "requestId"));
        if (request == null) {
            throw new IllegalArgumentException("stock transfer request not found");
        }
        return toView(tenantId, request);
    }

    @Override
    public StockTransferView requireBySourceBusiness(String sourceBusinessType, String sourceBusinessRef) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        StockTransferRequestDO request = mapper.selectRequestBySourceBusiness(tenantId,
                sourceBusinessType, sourceBusinessRef);
        if (request == null) {
            throw new IllegalArgumentException("stock transfer request not found");
        }
        return toView(tenantId, request);
    }

    public PageResult<StockTransferPageItem> getPage(StockTransferPageReqVO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String keyword = normalize(request.getKeyword());
        String orderStatus = normalize(request.getOrderStatus());
        String sourceWarehouseId = normalize(request.getSourceWarehouseId());
        String targetWarehouseId = normalize(request.getTargetWarehouseId());
        long total = queryMapper.countPage(tenantId, keyword, orderStatus, sourceWarehouseId, targetWarehouseId);
        if (total == 0) {
            return PageResult.empty();
        }
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<StockTransferPageItem> rows = queryMapper.selectPage(tenantId, keyword, orderStatus,
                sourceWarehouseId, targetWarehouseId, offset, request.getPageSize());
        rows.forEach(row -> {
            Stage stage = stage(scaled(row.getTotalRequestedQuantity()), scaled(row.getTotalOutboundQuantity()),
                    scaled(row.getTotalReceivedQuantity()), row.getRequestStatus());
            row.setCurrentStageCode(stage.code());
            row.setCurrentStageLabel(stage.label());
            row.setTerminal(stage.terminal());
        });
        return new PageResult<>(rows, total);
    }

    private StockTransferView toView(Long tenantId, StockTransferRequestDO request) {
        StockTransferOrderDO order = mapper.selectOrderByRequestId(tenantId, request.getRequestId());
        List<StockTransferRequestLineDO> requestLines = mapper.selectRequestLines(tenantId, request.getRequestId());
        List<StockTransferOrderLineDO> orderLines = mapper.selectOrderLines(tenantId, order.getOrderId());
        List<StockTransferStatusHistoryDO> history = mapper.selectStatusHistory(tenantId, request.getRequestId(),
                order.getOrderId());
        List<StockTransferExecutionBatchDO> executionBatches = mapper.selectExecutionBatches(tenantId, order.getOrderId());
        List<StockTransferExecutionLineDO> executionLines = mapper.selectExecutionLines(tenantId, order.getOrderId());
        StockTransferWarehouseIdentity sourceWarehouse = requireWarehouse(tenantId, request.getSourceWarehouseId());
        StockTransferWarehouseIdentity targetWarehouse = requireWarehouse(tenantId, request.getTargetWarehouseId());

        Map<Integer, StockTransferOrderLineDO> orderLineByNumber = new LinkedHashMap<>();
        for (StockTransferOrderLineDO line : orderLines) {
            orderLineByNumber.put(line.getLineNumber(), line);
        }
        Totals totals = totals(orderLines);
        Stage stage = stage(totals.requested(), totals.outbound(), totals.received(), request.getStatus());
        Map<String, List<StockTransferExecutionLineDO>> batchLines = new LinkedHashMap<>();
        for (StockTransferExecutionLineDO line : executionLines) {
            batchLines.computeIfAbsent(line.getBatchId(), ignored -> new ArrayList<>()).add(line);
        }
        return StockTransferView.builder()
                .requestId(request.getRequestId()).requestCode(request.getRequestCode())
                .requestStatus(request.getStatus()).requestVersion(request.getVersion())
                .orderId(order.getOrderId()).orderCode(order.getOrderCode())
                .orderStatus(order.getStatus()).orderVersion(order.getVersion())
                .sourceBusinessType(request.getSourceBusinessType())
                .sourceBusinessRef(request.getSourceBusinessRef())
                .ownerType(request.getOwnerType()).ownerId(request.getOwnerId())
                .sourceWarehouseId(request.getSourceWarehouseId())
                .sourceWarehouseCode(sourceWarehouse.getWarehouseCode())
                .sourceWarehouseName(sourceWarehouse.getWarehouseName())
                .targetWarehouseId(request.getTargetWarehouseId())
                .targetWarehouseCode(targetWarehouse.getWarehouseCode())
                .targetWarehouseName(targetWarehouse.getWarehouseName())
                .reasonCode(request.getReasonCode()).remark(request.getRemark())
                .currentStageCode(stage.code()).currentStageLabel(stage.label()).terminal(stage.terminal())
                .lines(requestLines.stream().map(line -> {
                    StockTransferOrderLineDO orderLine = orderLineByNumber.get(line.getLineNumber());
                    Stage lineStage = lineStage(orderLine);
                    return StockTransferView.LineView.builder()
                            .requestLineId(line.getLineId())
                            .orderLineId(orderLine == null ? null : orderLine.getLineId())
                            .lineNumber(line.getLineNumber())
                            .canonicalSkuId(line.getCanonicalSkuId())
                            .movementGroupId(orderLine == null ? null : orderLine.getMovementGroupId())
                            .requestedQuantity(line.getRequestedQuantity())
                            .outboundQuantity(orderLine == null ? ZERO : scaled(orderLine.getOutboundQuantity()))
                            .receivedQuantity(orderLine == null ? ZERO : scaled(orderLine.getReceivedQuantity()))
                            .uomCode(line.getUomCode())
                            .lineStatus(orderLine == null ? "PREPARE" : orderLine.getStatus())
                            .currentStageCode(lineStage.code())
                            .currentStageLabel(lineStage.label())
                            .remark(line.getRemark())
                            .build();
                }).toList())
                .statusHistory(history.stream().map(item -> StockTransferView.StatusHistoryView.builder()
                        .historyId(item.getHistoryId()).businessObjectType(item.getBusinessObjectType())
                        .businessObjectId(item.getBusinessObjectId()).status(item.getStatus())
                        .statusVersion(item.getStatusVersion()).stageCode(item.getStageCode())
                        .stageLabel(item.getStageLabel()).changedAt(item.getChangedAt()).build()).toList())
                .executionBatches(executionBatches.stream().map(batch -> StockTransferView.ExecutionBatchView.builder()
                        .batchId(batch.getBatchId()).batchNo(batch.getBatchNo()).batchType(batch.getBatchType())
                        .status(batch.getStatus()).version(batch.getVersion()).occurredAt(batch.getOccurredAt())
                        .remark(batch.getRemark())
                        .lines(batchLines.getOrDefault(batch.getBatchId(), List.of()).stream()
                                .map(line -> StockTransferView.ExecutionLineView.builder()
                                        .executionLineId(line.getExecutionLineId())
                                        .outboundExecutionLineId(line.getOutboundExecutionLineId())
                                        .lineNumber(line.getLineNumber())
                                        .canonicalSkuId(line.getCanonicalSkuId())
                                        .movementGroupId(line.getMovementGroupId())
                                        .executedQuantity(line.getExecutedQuantity())
                                        .receivedQuantity(line.getReceivedQuantity())
                                        .cumulativeDispatchedQuantity(line.getCumulativeDispatchedQuantity())
                                        .cumulativeReceivedQuantity(line.getCumulativeReceivedQuantity())
                                        .outstandingQuantity(line.getOutstandingQuantity())
                                        .lineStatus(line.getStatus()).lotId(line.getLotId())
                                        .sourceLocationId(line.getSourceLocationId())
                                        .sourceStockStatus(line.getSourceStockStatus())
                                        .sourceQualityStatus(line.getSourceQualityStatus())
                                        .targetLocationId(line.getTargetLocationId())
                                        .targetStockStatus(line.getTargetStockStatus())
                                        .targetQualityStatus(line.getTargetQualityStatus())
                                        .dispatchLedgerTransactionId(line.getDispatchLedgerTransactionId())
                                        .receiveLedgerTransactionId(line.getReceiveLedgerTransactionId())
                                        .remark(line.getRemark()).build())
                                .toList())
                        .build()).toList())
                .build();
    }

    private StockTransferWarehouseIdentity requireWarehouse(Long tenantId, String warehouseId) {
        StockTransferWarehouseIdentity identity = queryMapper.selectWarehouseIdentity(tenantId, warehouseId);
        if (identity == null) {
            throw new IllegalStateException("canonical warehouse not found: " + warehouseId);
        }
        return identity;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static Stage stage(BigDecimal requested, BigDecimal outbound, BigDecimal received, String requestStatus) {
        if ("CANCELED".equals(requestStatus)) {
            return new Stage("NONE", "调拨单已取消", true);
        }
        if (requested.compareTo(ZERO) > 0 && received.compareTo(requested) == 0) {
            return new Stage("NONE", "调拨已完成", true);
        }
        if (received.compareTo(ZERO) > 0 || outbound.compareTo(requested) == 0) {
            return new Stage("TRANSFER_INBOUND", "等待调拨入库", false);
        }
        return new Stage("TRANSFER_OUTBOUND", "等待调拨出库", false);
    }

    private static Stage lineStage(StockTransferOrderLineDO line) {
        if (line == null) {
            return new Stage("TRANSFER_OUTBOUND", "等待调拨出库", false);
        }
        return switch (line.getStatus()) {
            case "FULL_RECEIVED" -> new Stage("NONE", "行已收齐", true);
            case "PARTIAL_RECEIVED", "FULL_OUTBOUND" -> new Stage("TRANSFER_INBOUND", "等待调拨入库", false);
            default -> new Stage("TRANSFER_OUTBOUND", "等待调拨出库", false);
        };
    }

    private static Totals totals(List<StockTransferOrderLineDO> lines) {
        BigDecimal requested = ZERO;
        BigDecimal outbound = ZERO;
        BigDecimal received = ZERO;
        for (StockTransferOrderLineDO line : lines) {
            requested = requested.add(scaled(line.getRequestedQuantity()));
            outbound = outbound.add(scaled(line.getOutboundQuantity()));
            received = received.add(scaled(line.getReceivedQuantity()));
        }
        return new Totals(requested, outbound, received);
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value == null ? ZERO : value.setScale(6);
    }

    private record Totals(BigDecimal requested, BigDecimal outbound, BigDecimal received) {
    }

    private record Stage(String code, String label, boolean terminal) {
    }
}
