package cn.iocoder.yudao.module.cloudmold.merchant.api.deposit;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class MerchantDepositResult implements Serializable {
    private Long operationId;
    private String accountId;
    private String ledgerEntryId;
    private String depositEventId;
    private String merchantId;
    private String currency;
    private Long accountVersion;
    private Long requiredAmountMinor;
    private Long heldAmountMinor;
    private Long frozenAmountMinor;
    private Long availableAmountMinor;
    private Long paidAmountMinor;
    private Long deductedAmountMinor;
    private String previousCoverageStatus;
    private String coverageStatus;
    private String enforcementStatus;
    private String merchantStatus;
    private Long merchantVersion;
    private String listingUnpublishSagaId;
    private Integer affectedListingCount;
    private boolean duplicate;
}
