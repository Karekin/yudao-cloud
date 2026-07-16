package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_resolution_saga")
public class AfterSaleResolutionSagaDO {
    @TableId(type = IdType.INPUT)
    private String sagaId;
    private Long tenantId;
    private String afterSaleId;
    private String afterSaleItemId;
    private String runId;
    private String orderId;
    private String orderNo;
    private String orderItemId;
    private Long orderVersionAtRequest;
    private Long orderVersion;
    private String paymentId;
    private Long paymentVersionAtRequest;
    private String returnFulfillmentId;
    private String returnShipmentId;
    private String inspectionId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String ownerId;
    private String warehouseId;
    private String uomCode;
    private Long approvedAmountMinor;
    private Long grossAmountMinor;
    private Long benefitAmountMinor;
    private Long netAmountMinor;
    private String benefitReversalStatus;
    private String benefitReversalBatchId;
    private Long benefitReversalAmountMinor;
    private LocalDateTime benefitReversalOccurredAt;
    private String currencyCode;
    private String reason;
    private String status;
    private String activeStep;
    private Integer attemptCount;
    private Integer maxAttempts;
    private Long version;
    private Long inventoryOperationId;
    private Long inventoryLedgerTransactionId;
    private Long paymentRefundTransactionId;
    private Long orderRefundOperationId;
    private Long orderReturnOperationId;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime nextRetryAt;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String correlationId;
    private String causationId;
    private LocalDateTime inventoryOccurredAt;
    private LocalDateTime paymentOccurredAt;
    private LocalDateTime orderRefundOccurredAt;
    private LocalDateTime orderReturnOccurredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
