package cn.iocoder.yudao.module.cloudmold.order.api.cancellation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancellationSagaItemView {
    private String sagaItemId;
    private String orderItemId;
    private String reservationId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String status;
    private Integer attemptCount;
    private Long inventoryOperationId;
}
