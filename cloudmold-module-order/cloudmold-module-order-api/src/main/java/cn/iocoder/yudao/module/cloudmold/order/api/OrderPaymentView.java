package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentView {
    private String orderId;
    private String orderNo;
    private String runId;
    private String buyerId;
    private String status;
    private Long payableAmountMinor;
    private String currencyCode;
    private Long aggregateVersion;
}
