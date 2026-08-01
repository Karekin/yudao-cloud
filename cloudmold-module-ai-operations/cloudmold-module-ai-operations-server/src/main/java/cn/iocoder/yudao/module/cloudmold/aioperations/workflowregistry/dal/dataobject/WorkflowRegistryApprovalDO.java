package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_registry_approval")
public class WorkflowRegistryApprovalDO {

    @TableId(type = IdType.INPUT)
    private String approvalId;
    private Long tenantId;
    private String skillId;
    private String registryVersionId;
    private String evaluationId;
    private String riskLevel;
    private String approvalDecision;
    private String proposedBySubject;
    private String evaluatorSubject;
    private String approverSubject;
    private String rationale;
    private String evidenceRefsJson;
    private String metricsJson;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
