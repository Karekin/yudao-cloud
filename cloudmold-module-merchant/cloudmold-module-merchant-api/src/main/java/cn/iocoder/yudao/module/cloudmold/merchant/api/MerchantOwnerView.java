package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantOwnerView {
    private String merchantId;
    private String merchantStatus;
    private String legalEntityId;
}
