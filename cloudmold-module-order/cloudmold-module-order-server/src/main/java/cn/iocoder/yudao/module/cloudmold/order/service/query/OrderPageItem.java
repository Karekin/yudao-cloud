package cn.iocoder.yudao.module.cloudmold.order.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderPageItem {

    private String orderId;
    private String orderNo;
    private String buyerId;
    private String status;

    private Long itemCount;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal totalQuantity;

    private Long productAmountMinor;
    private Long shippingAmountMinor;
    private Long discountAmountMinor;
    private Long payableAmountMinor;
    private String currencyCode;

    private String paymentId;
    private String paymentStatus;
    private String fulfillmentId;
    private String fulfillmentStatus;
    private String shipmentId;
    private String refundId;
    private String cancellationSagaId;

    private Long aggregateVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
