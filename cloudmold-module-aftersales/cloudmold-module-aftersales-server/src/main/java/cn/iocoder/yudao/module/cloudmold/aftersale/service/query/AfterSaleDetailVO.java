package cn.iocoder.yudao.module.cloudmold.aftersale.service.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - CloudMold 规范 AfterSale 详情，含售后行")
@Data
public class AfterSaleDetailVO {

    private String afterSaleId;
    private String afterSaleNo;
    private String orderId;
    private String orderNo;
    private String caseStatus;
    private String refundStatus;
    private String afterSaleType;
    private String reasonCode;
    private String reason;
    private String responsibility;
    private Long approvedAmountMinor;
    private String currencyCode;
    private String returnFulfillmentId;
    private String returnFulfillmentStatus;
    private String resolutionSagaId;
    private String resolutionSagaStatus;
    private String resolutionSagaActiveStep;
    private Integer resolutionSagaAttemptCount;
    private Integer resolutionSagaMaxAttempts;
    private String resolutionSagaLastErrorCode;
    private String resolutionSagaLastErrorMessage;
    private String buyerId;
    private String paymentId;
    private String reviewerId;
    private String forwardFulfillmentId;
    private String ownerId;
    private String warehouseId;
    private String uomCode;
    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private List<AfterSaleDetailItem> items;
}
