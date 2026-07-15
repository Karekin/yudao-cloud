package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_workflow_definition")
public class AiWorkflowDefinitionDO {
    @TableId(type = IdType.INPUT)
    private String workflowId;
    private Long tenantId;
    private String applicationId;
    private String workflowCode;
    private Long currentVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
