package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCommandResult {
    private Long operationId;
    private String orderId;
    private String orderNo;
    private String previousStatus;
    private String currentStatus;
    private Long aggregateVersion;
    private Long productAmountMinor;
    private Long shippingAmountMinor;
    private Long discountAmountMinor;
    private Long payableAmountMinor;
    private String currencyCode;
    private String paymentId;
    private String fulfillmentId;
    private String shipmentId;
    private String cancellationSagaId;
    private String preCancellationStatus;
    private String refundId;
    private List<OrderLineView> items;
    private List<OrderBenefitApplicationView> benefitApplications;
    private Boolean duplicate;
}
