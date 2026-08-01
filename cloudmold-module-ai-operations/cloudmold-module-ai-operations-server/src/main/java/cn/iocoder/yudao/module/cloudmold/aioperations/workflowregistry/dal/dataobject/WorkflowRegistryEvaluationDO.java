package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_registry_evaluation")
public class WorkflowRegistryEvaluationDO {

    @TableId(type = IdType.INPUT)
    private String evaluationId;
    private Long tenantId;
    private String skillId;
    private String registryVersionId;
    private String validationRequestId;
    private String stableVersionId;
    private Long pointerVersion;
    private String riskLevel;
    private String evaluationStatus;
    private String proposedBySubject;
    private String evaluatorSubject;
    private String evaluatorRunId;
    private String datasetSha256;
    private Integer sampleCount;
    private LocalDateTime observationStartedAt;
    private LocalDateTime observationEndedAt;
    private Boolean replayPassed;
    private Boolean shadowPassed;
    private Boolean canaryPassed;
    private Boolean guardrailsPassed;
    private Boolean samplePassed;
    private Boolean dqcPassed;
    private Boolean observationWindowPassed;
    private String evidenceRefsJson;
    private String metricsJson;
    private String failureSamplesJson;
    private String validatorAttestationJson;
    private String idempotencyKey;
    private LocalDateTime createdAt;
}
