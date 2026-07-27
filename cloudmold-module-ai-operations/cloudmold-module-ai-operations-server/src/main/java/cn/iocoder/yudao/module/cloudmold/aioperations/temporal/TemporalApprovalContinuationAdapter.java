package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentApprovalContinuationApi;
import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalApprovalContinuationAdapter implements AgentApprovalContinuationApi {

    private final WorkflowClient workflowClient;
    private final AiOperationsTemporalMapper mapper;

    @Override
    public void onApprovalDecision(Long tenantId, String workOrderId, String approvalId, String decision) {
        TemporalRunBindingRecord binding = mapper.selectRunBindingByWorkOrder(tenantId, workOrderId);
        if (binding == null || !approvalId.equals(binding.getApprovalId())) {
            return;
        }
        workflowClient.newUntypedWorkflowStub(binding.getTemporalWorkflowId(),
                        Optional.of(binding.getTemporalRunId()), Optional.empty())
                .signal("approvalDecision", decision);
        mapper.markApprovalSignaled(tenantId, binding.getTemporalRunId(),
                LocalDateTime.now(ZoneOffset.UTC));
    }
}
