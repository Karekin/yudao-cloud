package cn.iocoder.yudao.module.cloudmold.aioperations.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_ai_ops_workflow_run")
public class AiWorkflowRunDO {
    @TableId(type = IdType.INPUT)
    private String runId;
    private Long tenantId;
    private String runKey;
    private String applicationId;
    private String workflowId;
    private String workflowVersionId;
    private Long workflowVersion;
    private String triggerType;
    private String businessRef;
    private String status;
    private Integer expectedInvocationCount;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
