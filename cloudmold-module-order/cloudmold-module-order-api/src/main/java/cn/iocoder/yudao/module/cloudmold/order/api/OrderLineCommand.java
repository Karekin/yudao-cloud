package cn.iocoder.yudao.module.cloudmold.order.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderLineCommand {
    private String lineKey;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private Long unitPriceMinor;
    private Long merchandiseCostMinor;
    private String listingId;
    private String listingOfferId;

    public OrderLineCommand(String canonicalSkuId, BigDecimal quantity, Long unitPriceMinor) {
        this.canonicalSkuId = canonicalSkuId;
        this.quantity = quantity;
        this.unitPriceMinor = unitPriceMinor;
    }
}
