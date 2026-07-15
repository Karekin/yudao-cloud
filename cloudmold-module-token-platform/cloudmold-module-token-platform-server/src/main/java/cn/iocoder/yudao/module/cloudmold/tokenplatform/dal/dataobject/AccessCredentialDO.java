package cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_token_platform_access_credential")
@Data
@Accessors(chain = true)
public class AccessCredentialDO {
    @TableId(type = IdType.INPUT)
    private String credentialId;
    private Long tenantId;
    private String principalId;
    private String offeringId;
    private String credentialFingerprint;
    private String secretRef;
    private String last4;
    private Integer keyVersion;
    private String status;
    private LocalDateTime expiresAt;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
