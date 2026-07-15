package cn.iocoder.yudao.module.cloudmold.merchant.api.deposit;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MerchantDepositView {
    private String accountId;
    private String merchantId;
    private String currency;
    private Long accountVersion;
    private Long requiredAmountMinor;
    private Long heldAmountMinor;
    private Long frozenAmountMinor;
    private Long availableAmountMinor;
    private Long paidAmountMinor;
    private Long deductedAmountMinor;
    private String coverageStatus;
    private String enforcementStatus;
    private String policyVersion;
}
