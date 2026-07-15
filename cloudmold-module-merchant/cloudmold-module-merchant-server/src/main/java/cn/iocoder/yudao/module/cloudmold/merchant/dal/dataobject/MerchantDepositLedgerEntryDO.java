package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_deposit_ledger_entry")
@Data
@Accessors(chain = true)
public class MerchantDepositLedgerEntryDO {
    @TableId(type = IdType.INPUT)
    private String ledgerEntryId;
    private Long tenantId;
    private String accountId;
    private String merchantId;
    private String currency;
    private Long accountVersion;
    private String entryType;
    private Long amountMinor;
    private Long heldDeltaMinor;
    private Long frozenDeltaMinor;
    private Long heldBeforeMinor;
    private Long heldAfterMinor;
    private Long frozenBeforeMinor;
    private Long frozenAfterMinor;
    private Long requiredBeforeMinor;
    private Long requiredAfterMinor;
    private Long paidAfterMinor;
    private Long deductedAfterMinor;
    private String previousCoverageStatus;
    private String currentCoverageStatus;
    private String previousEnforcementStatus;
    private String currentEnforcementStatus;
    private String policyVersion;
    private String businessReference;
    private String reasonCode;
    private String evidenceRef;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
