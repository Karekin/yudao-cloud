package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_registry_release")
public class WorkflowRegistryReleaseDO {

    @TableId(type = IdType.INPUT)
    private String releaseId;
    private Long tenantId;
    private String skillId;
    private String registryVersionId;
    private String previousStableVersionId;
    private String targetStatus;
    private String releaseReason;
    private String actorSubject;
    private Boolean approvalRequired;
    private Boolean killSwitchArmed;
    private String sourceIdempotencyKey;
    private String evidenceRefsJson;
    private String metricsJson;
    private LocalDateTime createdAt;
}
