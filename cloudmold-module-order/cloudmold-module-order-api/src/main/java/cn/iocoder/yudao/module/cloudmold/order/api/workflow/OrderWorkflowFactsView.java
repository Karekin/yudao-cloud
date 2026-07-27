package cn.iocoder.yudao.module.cloudmold.order.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderWorkflowFactsView {
    private String orderId;
    private String orderNo;
    private String runId;
    private String status;
    private Long aggregateVersion;
    private Long payableAmountMinor;
    private String currencyCode;
    private String paymentId;
    private String fulfillmentId;
    private String shipmentId;
    private String refundId;
    private String cancellationSagaId;
}
