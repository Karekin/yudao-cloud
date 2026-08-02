package cn.iocoder.yudao.module.cloudmold.agentcontrol.api.review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentApprovalReviewContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private String approvalId;
    private String workOrderId;
    private String title;
    private String roleCode;
    private String actionCode;
    private String riskLevel;
    private String scopeHash;
    private String workflowStatus;
    private String businessAction;
    private String impactObjects;
    private String impactMetrics;
    private String recommendation;
    private String evidenceSummary;
    private String nonExecutionConsequence;
    private String executionSteps;
    private String taskName;
    private String taskDefinitionKey;
    private Instant requestedAt;
}
