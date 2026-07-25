package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppFulfillmentView {
    private String fulfillmentId;
    private String fulfillmentNo;
    private String orderId;
    private String status;
    private Long aggregateVersion;
    private String warehouseId;
    private String carrierCode;
    private String waybillNo;
    private String shipmentId;
    private List<Item> items;
    private List<TrackingEvent> trackingEvents;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private String orderItemId;
        private String canonicalSkuId;
        private BigDecimal quantity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrackingEvent {
        private String status;
        private LocalDateTime occurredAt;
        private String description;
    }
}
