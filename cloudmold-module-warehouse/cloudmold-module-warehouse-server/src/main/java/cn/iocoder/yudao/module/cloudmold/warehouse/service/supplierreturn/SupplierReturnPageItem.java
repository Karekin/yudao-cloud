package cn.iocoder.yudao.module.cloudmold.warehouse.service.supplierreturn;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SupplierReturnPageItem {
    private String returnId;
    private String returnCode;
    private String purchaseOrderId;
    private String receiptId;
    private String supplierId;
    private String ownerId;
    private String warehouseId;
    private String status;
    private Long version;
    private BigDecimal totalReturnQuantity;
    private BigDecimal totalDispatchedQuantity;
    private Long lineCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String currentStageCode;
    private String currentStageLabel;
    private boolean terminal;
}
