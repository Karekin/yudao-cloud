package cn.iocoder.yudao.module.cloudmold.merchant.api;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantBenefitEntitlementItem {
    private String benefitCode;
    private String entitlementStatus;
    private String evidenceRef;
    private String note;
}
