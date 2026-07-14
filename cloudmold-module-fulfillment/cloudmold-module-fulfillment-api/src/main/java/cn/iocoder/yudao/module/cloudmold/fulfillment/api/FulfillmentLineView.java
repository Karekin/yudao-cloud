package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentLineView {
    private String fulfillmentItemId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String reservationId;
}
