package cn.iocoder.yudao.module.cloudmold.appcommerce.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AppCartItemDO {
    private String lineId;
    private Long tenantId;
    private String cartId;
    private String buyerPrincipalId;
    private String listingId;
    private String listingOfferId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private Boolean selected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
