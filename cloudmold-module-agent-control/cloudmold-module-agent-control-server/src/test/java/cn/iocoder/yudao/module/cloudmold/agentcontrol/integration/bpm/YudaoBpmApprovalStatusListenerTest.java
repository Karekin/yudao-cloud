package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class YudaoBpmApprovalStatusListenerTest {

    private final AgentApprovalWorkflowProperties properties = properties();
    private final AgentApprovalWorkflowService workflows = mock(AgentApprovalWorkflowService.class);
    private final YudaoBpmApprovalStatusListener listener =
            new YudaoBpmApprovalStatusListener(properties, workflows);

    @Test
    void acceptsOnlyTheConfiguredVersionedProcessKey() {
        BpmProcessInstanceStatusEvent matching = event("cloudmold-agent-approval-v1");
        BpmProcessInstanceStatusEvent unrelated = event("leave");

        listener.onApplicationEvent(unrelated);
        listener.onApplicationEvent(matching);

        verify(workflows).observe(matching);
        verifyNoMoreInteractions(workflows);
    }

    private static AgentApprovalWorkflowProperties properties() {
        AgentApprovalWorkflowProperties value = new AgentApprovalWorkflowProperties();
        value.setProcessDefinitionKey("cloudmold-agent-approval-v1");
        return value;
    }

    private static BpmProcessInstanceStatusEvent event(String processDefinitionKey) {
        return new BpmProcessInstanceStatusEvent("test").setId("process-1")
                .setProcessDefinitionKey(processDefinitionKey).setBusinessKey("business-1").setStatus(2);
    }

}
