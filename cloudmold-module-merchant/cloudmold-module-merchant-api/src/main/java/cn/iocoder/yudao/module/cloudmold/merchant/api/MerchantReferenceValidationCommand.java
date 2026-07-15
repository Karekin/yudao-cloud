package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantReferenceValidationCommand {
    private String merchantId;
    private String shopId;
}
