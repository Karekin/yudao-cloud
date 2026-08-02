package cn.iocoder.yudao.module.cloudmold.quality.service;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class ProcurementReceiptInspectionPageItem {
    String inspectionId;
    String inspectionCode;
    String receiptId;
    String purchaseOrderId;
    String supplierId;
    String ownerType;
    String ownerId;
    String businessNo;
    String status;
    String finalDecision;
    BigDecimal receivedQuantity;
    BigDecimal sampledQuantity;
    BigDecimal acceptedQuantity;
    BigDecimal rejectedQuantity;
    BigDecimal quarantinedQuantity;
    Long version;
    Instant completedAt;
    Instant updatedAt;
}
