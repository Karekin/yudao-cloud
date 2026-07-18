package cn.iocoder.yudao.module.cloudmold.commercebehavior.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_commerce_session_identity_link")
public class CommerceSessionIdentityLinkDO {
    @TableId
    private String linkId;
    private Long tenantId;
    private String sessionId;
    private String principalId;
    private Long linkVersion;
    private Long sessionVersion;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private LocalDateTime linkedAt;
    private LocalDateTime createdAt;
}
