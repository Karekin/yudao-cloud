package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AppCheckoutDO {
    private String checkoutToken;
    private Long tenantId;
    private String buyerPrincipalId;
    private String idempotencyKey;
    private String requestHash;
    private String listingId;
    private String listingOfferId;
    private String canonicalSpuId;
    private String canonicalSkuId;
    private String addressRef;
    private Long addressSnapshotVersion;
    private String destinationRegionCode;
    private BigDecimal quantity;
    private Long unitPriceMinor;
    private Long productAmountMinor;
    private Long shippingAmountMinor;
    private Long discountAmountMinor;
    private Long payableAmountMinor;
    private String currencyCode;
    private String status;
    private String orderId;
    private Long version;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
