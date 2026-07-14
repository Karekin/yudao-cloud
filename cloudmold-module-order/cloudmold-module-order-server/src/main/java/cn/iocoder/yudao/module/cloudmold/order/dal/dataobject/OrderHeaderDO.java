package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_header")
public class OrderHeaderDO {
    @TableId(type = IdType.INPUT)
    private String orderId;
    private Long tenantId;
    private String orderNo;
    private String runId;
    private String buyerId;
    private String status;
    private BigDecimal totalQuantity;
    private Long productAmountMinor;
    private Long shippingAmountMinor;
    private Long discountAmountMinor;
    private Long payableAmountMinor;
    private String currencyCode;
    private String paymentId;
    private String fulfillmentId;
    private String shipmentId;
    private String refundId;
    private String cancellationSagaId;
    private String preCancellationStatus;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
