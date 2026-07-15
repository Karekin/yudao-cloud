package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_quota_ledger")
@Data
@Accessors(chain = true)
public class QuotaLedgerDO {
    @TableId(type = IdType.INPUT)
    private String ledgerEntryId;
    private Long tenantId;
    private String accountId;
    private String principalId;
    private Long operationId;
    private String entryType;
    private Long signedDeltaMicrounits;
    private Long balanceAfterMicrounits;
    private Long accountVersion;
    private String referenceType;
    private String referenceId;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
