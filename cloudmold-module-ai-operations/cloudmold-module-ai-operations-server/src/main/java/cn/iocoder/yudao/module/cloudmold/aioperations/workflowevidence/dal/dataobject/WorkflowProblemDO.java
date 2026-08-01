package cn.iocoder.yudao.module.cloudmold.aioperations.workflowevidence.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_problem")
public class WorkflowProblemDO {
    @TableId(type = IdType.INPUT)
    private String problemId;
    private Long tenantId;
    private String lineageId;
    private String workflowId;
    private String workflowVersion;
    private String proposalId;
    private String sourceType;
    private String headline;
    private String problemDetail;
    private String evidenceRef;
    private String summarySourceRefsJson;
    private String modelSummary;
    private String metricsJson;
    private String severity;
    private String dqcStatus;
    private String recordStatus;
    private Boolean releaseEligible;
    private String corroboratingSourceTypesJson;
    private LocalDateTime freshUntil;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private LocalDateTime observedAt;
    private String actorSubject;
    private LocalDateTime createdAt;
}
