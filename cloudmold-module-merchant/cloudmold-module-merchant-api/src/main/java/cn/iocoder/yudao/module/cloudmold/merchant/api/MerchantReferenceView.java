package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantReferenceView {
    private String merchantId;
    private String merchantStatus;
    private String legalEntityId;
    private String shopId;
    private String shopStatus;
    private String channelCode;
}
