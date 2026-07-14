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
public class OrderFulfillmentView {
    private String orderId;
    private String orderNo;
    private String status;
    private Long aggregateVersion;
    private List<OrderLineView> items;
}
