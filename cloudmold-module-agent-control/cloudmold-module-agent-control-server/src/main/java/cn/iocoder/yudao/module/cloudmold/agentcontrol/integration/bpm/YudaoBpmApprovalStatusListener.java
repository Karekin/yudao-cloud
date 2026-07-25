package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class YudaoBpmApprovalStatusListener extends BpmProcessInstanceStatusEventListener {

    private final AgentApprovalWorkflowProperties properties;
    private final AgentApprovalWorkflowService workflows;

    @Override
    protected String getProcessDefinitionKey() {
        return properties.getProcessDefinitionKey();
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        workflows.observe(event);
    }

}
