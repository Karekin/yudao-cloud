package cn.iocoder.yudao.module.cloudmold.integration.yudao.dal;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class YudaoPurchasePromiseRow {
    private String promiseId;
    private String promiseKey;
    private Long tenantId;
    private Long purchaseOrderId;
    private String purchaseOrderNo;
    private Long purchaseOrderLineId;
    private Long supplierId;
    private Long productId;
    private Long productUnitId;
    private BigDecimal orderedQuantity;
    private LocalDateTime promisedReceiptAt;
    private String promiseTimezone;
    private Integer graceMinutes;
    private Integer pauseMinutes;
    private LocalDateTime promiseFrozenAt;
    private String status;
    private Long version;
    private String runId;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
