package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.agent-control.approval-workflow")
public class AgentApprovalWorkflowProperties {

    private boolean enabled = false;
    private String processDefinitionKey = "cloudmold-agent-approval-v1";
    private int batchSize = 20;
    /** Minimum age before an uncertain start may be proven absent and retried. */
    private int recoveryGraceSeconds = 60;
    /** Enables the AI reviewer as an OR-sign peer of the human approval path. */
    private boolean aiReviewerOrSignEnabled = false;
    /** Dedicated System user ids that may act as AI reviewers. Empty means fail closed. */
    private Set<Long> aiReviewerUserIds = new LinkedHashSet<>();
    /** Risk levels allowed for AI review. Empty means fail closed. */
    private Set<String> aiReviewAllowedRiskLevels = new LinkedHashSet<>();

}
