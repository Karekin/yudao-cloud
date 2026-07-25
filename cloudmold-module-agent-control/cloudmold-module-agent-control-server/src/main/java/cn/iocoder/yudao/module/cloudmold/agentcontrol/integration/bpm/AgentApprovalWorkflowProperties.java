package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "cloudmold.agent-control.approval-workflow")
public class AgentApprovalWorkflowProperties {

    private boolean enabled = false;
    private String processDefinitionKey = "cloudmold-agent-approval-v1";
    private int batchSize = 20;

}
