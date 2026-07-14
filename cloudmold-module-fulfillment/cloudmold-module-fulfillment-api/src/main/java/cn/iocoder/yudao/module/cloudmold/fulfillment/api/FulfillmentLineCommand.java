package cn.iocoder.yudao.module.cloudmold.fulfillment.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FulfillmentLineCommand {
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String reservationId;
}
