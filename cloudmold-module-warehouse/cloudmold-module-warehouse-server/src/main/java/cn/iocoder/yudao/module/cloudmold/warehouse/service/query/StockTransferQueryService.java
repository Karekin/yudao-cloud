package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockTransferView;
import cn.iocoder.yudao.module.cloudmold.warehouse.controller.admin.vo.StockTransferPageReqVO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferQueryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockTransferStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockTransferQueryService implements StockTransferQueryApi {

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
            Stage stage = stage(row.getOrderStatus(), row.getRequestStatus());
            row.setCurrentStageCode(stage.code());
            row.setCurrentStageLabel(stage.label());
            row.setTerminal(stage.terminal());
        });
        return new PageResult<>(rows, total);
    }

    private StockTransferView toView(Long tenantId, StockTransferRequestDO request) {
        StockTransferOrderDO order = mapper.selectOrderByRequestId(tenantId, request.getRequestId());
        List<StockTransferRequestLineDO> lines = mapper.selectRequestLines(tenantId, request.getRequestId());
        List<StockTransferStatusHistoryDO> history = mapper.selectStatusHistory(tenantId, request.getRequestId(),
                order == null ? request.getRequestId() : order.getOrderId());
        StockTransferWarehouseIdentity sourceWarehouse = requireWarehouse(tenantId, request.getSourceWarehouseId());
        StockTransferWarehouseIdentity targetWarehouse = requireWarehouse(tenantId, request.getTargetWarehouseId());
        Stage stage = stage(order == null ? null : order.getStatus(), request.getStatus());
        return StockTransferView.builder()
                .requestId(request.getRequestId()).requestCode(request.getRequestCode())
                .requestStatus(request.getStatus()).requestVersion(request.getVersion())
                .orderId(order == null ? null : order.getOrderId())
                .orderCode(order == null ? null : order.getOrderCode())
                .orderStatus(order == null ? null : order.getStatus())
                .orderVersion(order == null ? null : order.getVersion())
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
                .lines(lines.stream().map(line -> StockTransferView.LineView.builder()
                        .lineId(line.getLineId()).lineNumber(line.getLineNumber())
                        .canonicalSkuId(line.getCanonicalSkuId())
                        .requestedQuantity(line.getRequestedQuantity()).uomCode(line.getUomCode())
                        .remark(line.getRemark()).build()).toList())
                .statusHistory(history.stream().map(item -> StockTransferView.StatusHistoryView.builder()
                        .historyId(item.getHistoryId()).businessObjectType(item.getBusinessObjectType())
                        .businessObjectId(item.getBusinessObjectId()).status(item.getStatus())
                        .statusVersion(item.getStatusVersion()).stageCode(item.getStageCode())
                        .stageLabel(item.getStageLabel()).changedAt(item.getChangedAt()).build()).toList())
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

    private static Stage stage(String orderStatus, String requestStatus) {
        if (orderStatus == null) {
            return new Stage("REQUEST_APPROVED", "调拨请求已批准", false);
        }
        return switch (orderStatus) {
            case "PREPARE" -> new Stage("TRANSFER_OUTBOUND", "等待调拨出库", false);
            case "RELEASED", "IN_TRANSIT" -> new Stage("TRANSFER_INBOUND", "等待调拨入库", false);
            case "COMPLETED" -> new Stage("NONE", "调拨已完成", true);
            case "CANCELED" -> new Stage("NONE", "调拨单已取消", true);
            default -> "CANCELED".equals(requestStatus)
                    ? new Stage("NONE", "调拨请求已取消", true)
                    : new Stage("MANUAL_RECONCILIATION", "调拨状态需要人工复核", false);
        };
    }

    private record Stage(String code, String label, boolean terminal) {
    }
}
