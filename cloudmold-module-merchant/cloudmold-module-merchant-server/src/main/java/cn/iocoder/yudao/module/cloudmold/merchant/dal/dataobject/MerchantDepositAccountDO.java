package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_deposit_account")
@Data
@Accessors(chain = true)
public class MerchantDepositAccountDO {
    @TableId(type = IdType.INPUT)
    private String accountId;
    private Long tenantId;
    private String merchantId;
    private String currency;
    private Long requiredAmountMinor;
    private Long heldAmountMinor;
    private Long frozenAmountMinor;
    private Long paidAmountMinor;
    private Long deductedAmountMinor;
    private String coverageStatus;
    private String enforcementStatus;
    private String policyVersion;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
