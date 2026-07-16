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
public class OrderLineView {
    private String orderItemId;
    private String lineKey;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private Long unitPriceMinor;
    private Long lineAmountMinor;
    private Long discountAmountMinor;
    private Long netAmountMinor;
    private String reservationId;
    private String listingId;
    private String listingOfferId;
    private Integer listingRevision;
    private Long listingVersion;
    private String channelCode;
    private String shopId;
}
