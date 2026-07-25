package cn.iocoder.yudao.module.cloudmold.appcommerce.service;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class AppCheckoutView {
    String checkoutToken;
    String principalId;
    String listingId;
    String listingOfferId;
    String canonicalSpuId;
    String canonicalSkuId;
    String addressRef;
    Long addressSnapshotVersion;
    String destinationRegionCode;
    String receiverSummary;
    String mobileSummary;
    BigDecimal quantity;
    Long unitPriceMinor;
    Long productAmountMinor;
    Long shippingAmountMinor;
    Long discountAmountMinor;
    Long payableAmountMinor;
    String currencyCode;
    BigDecimal availableQuantity;
    LocalDateTime expiresAt;
    Boolean duplicate;
}
