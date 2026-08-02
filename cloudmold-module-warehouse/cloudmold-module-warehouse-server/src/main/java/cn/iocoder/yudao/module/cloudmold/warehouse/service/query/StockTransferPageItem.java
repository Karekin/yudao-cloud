package cn.iocoder.yudao.module.cloudmold.warehouse.service.query;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockTransferPageItem {
    private String requestId;
    private String requestCode;
    private String requestStatus;
    private Long requestVersion;
    private String orderId;
    private String orderCode;
    private String orderStatus;
    private Long orderVersion;
    private String sourceBusinessType;
    private String sourceBusinessRef;
    private String ownerType;
    private String ownerId;
    private String sourceWarehouseId;
    private String sourceWarehouseCode;
    private String sourceWarehouseName;
    private String targetWarehouseId;
    private String targetWarehouseCode;
    private String targetWarehouseName;
    private Integer lineCount;
    private BigDecimal totalRequestedQuantity;
    private String uomCode;
    private String currentStageCode;
    private String currentStageLabel;
    private boolean terminal;
    private LocalDateTime approvedAt;
    private LocalDateTime preparedAt;
    private LocalDateTime updatedAt;
}
