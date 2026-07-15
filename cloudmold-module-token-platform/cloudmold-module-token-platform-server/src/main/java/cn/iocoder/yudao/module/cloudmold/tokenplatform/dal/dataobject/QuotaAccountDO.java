package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_quota_account")
@Data
@Accessors(chain = true)
public class QuotaAccountDO {
    @TableId(type = IdType.INPUT)
    private String accountId;
    private Long tenantId;
    private String principalId;
    private Long balanceMicrounits;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
