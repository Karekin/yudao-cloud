package cn.iocoder.yudao.module.cloudmold.engagement.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_engagement_moderation_case")
public class CommunityModerationCaseDO {
    @TableId(type = IdType.INPUT)
    private String moderationCaseId;
    private Long tenantId;
    private String contentId;
    private String reporterPrincipalId;
    private String moderatorPrincipalId;
    private String reportReasonCode;
    private String evidenceRef;
    private String status;
    private String decision;
    private String decisionReasonCode;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
