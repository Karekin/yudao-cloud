package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_case")
public class AfterSaleCaseDO {
    @TableId(type = IdType.INPUT)
    private String afterSaleId;
    private Long tenantId;
    private String afterSaleNo;
    private String runId;
    private String orderId;
    private String orderNo;
    private String buyerId;
    private Long orderVersionAtRequest;
    private String paymentId;
    private Long paymentVersionAtRequest;
    private String forwardFulfillmentId;
    private String forwardShipmentId;
    private String ownerId;
    private String warehouseId;
    private String uomCode;
    private String status;
    private String refundStatus;
    private Long approvedAmountMinor;
    private String currencyCode;
    private String returnFulfillmentId;
    private String returnShipmentId;
    private String inspectionId;
    private String resolutionSagaId;
    private String afterSaleType;
    private String reasonCode;
    private String responsibility;
    private String reason;
    private String reviewerId;
    private Long version;
    private String correlationId;
    private String causationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
