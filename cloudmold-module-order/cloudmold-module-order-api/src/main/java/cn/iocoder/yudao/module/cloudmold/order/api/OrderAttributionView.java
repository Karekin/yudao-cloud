package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAttributionView {
    private String orderId;
    private String orderNo;
    private String buyerId;
    private String status;
    private String paymentId;
    private Long aggregateVersion;
    private String merchantId;
    private String shopId;
    private String channelCode;
}
