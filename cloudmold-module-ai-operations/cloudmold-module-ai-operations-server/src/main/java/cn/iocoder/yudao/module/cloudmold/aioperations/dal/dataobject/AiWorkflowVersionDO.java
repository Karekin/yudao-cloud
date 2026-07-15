package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_workflow_version")
public class AiWorkflowVersionDO {
    @TableId(type = IdType.INPUT)
    private String workflowVersionId;
    private Long tenantId;
    private String workflowId;
    private String applicationId;
    private Long workflowVersion;
    private String definitionRef;
    private String definitionSha256;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
