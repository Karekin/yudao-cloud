package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_community_reaction_state")
public class CommunityReactionStateDO {
    @TableId(type = IdType.INPUT)
    private String reactionId;
    private Long tenantId;
    private String actorPrincipalId;
    private String reactionType;
    private String targetType;
    private String targetId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
