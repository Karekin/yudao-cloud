package cn.iocoder.yudao.module.cloudmold.aftersale.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范售后分页项")
@Data
public class AfterSalePageItem {

    private String afterSaleId;
    private String afterSaleNo;
    private String orderId;
    private String orderNo;
    private String caseStatus;
    private String refundStatus;
    private String afterSaleType;
    private String reasonCode;
    private String responsibility;
    private String afterSaleItemId;
    private String orderItemId;
    private String canonicalSkuId;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;
    private Long approvedAmountMinor;
    private String currencyCode;
    private String returnFulfillmentId;
    private String returnFulfillmentStatus;
    private String resolutionSagaId;
    private String resolutionSagaStatus;
    private String resolutionSagaActiveStep;
    private Integer resolutionSagaAttemptCount;
    private String resolutionSagaLastErrorCode;
    private Long aggregateVersion;
    private Long resolutionSagaVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
