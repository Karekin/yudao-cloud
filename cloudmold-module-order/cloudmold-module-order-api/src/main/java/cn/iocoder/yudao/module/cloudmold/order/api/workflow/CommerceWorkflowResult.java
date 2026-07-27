package cn.iocoder.yudao.module.cloudmold.order.api.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Stable, read-only workflow result. The port never fabricates a domain success:
 * unsupported execution authorities are returned as PREPARE with explicit blockers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommerceWorkflowResult {
    private String workflowType;
    private String workflowInstanceKey;
    private String businessKey;
    private CommerceWorkflowStatus status;
    private String phase;
    private Boolean terminal;
    private Boolean actionRequired;
    private String summary;
    private Long aggregateVersion;
    private List<String> blockers;
    private List<CommerceWorkflowArtifact> artifacts;
}
