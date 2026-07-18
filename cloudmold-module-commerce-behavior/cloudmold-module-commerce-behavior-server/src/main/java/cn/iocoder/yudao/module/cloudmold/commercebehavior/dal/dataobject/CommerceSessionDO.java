package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_commerce_session")
public class CommerceSessionDO {
    @TableId
    private String sessionId;
    private Long tenantId;
    private String principalId;
    private String channelCode;
    private String entrypointCode;
    private String status;
    private Long version;
    private Long identityLinkVersion;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime startedAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
