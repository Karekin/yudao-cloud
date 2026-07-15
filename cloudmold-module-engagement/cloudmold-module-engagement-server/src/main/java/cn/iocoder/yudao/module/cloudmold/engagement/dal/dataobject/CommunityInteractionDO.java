package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_community_interaction")
public class CommunityInteractionDO {
    @TableId(type = IdType.INPUT)
    private String interactionId;
    private Long tenantId;
    private String actorPrincipalId;
    private String interactionType;
    private String targetType;
    private String targetId;
    private String payloadRef;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
