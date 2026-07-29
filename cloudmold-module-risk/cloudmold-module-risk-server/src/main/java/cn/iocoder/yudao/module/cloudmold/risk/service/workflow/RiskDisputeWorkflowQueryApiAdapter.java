package cn.iocoder.yudao.module.cloudmold.risk.service.workflow;

import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RiskDisputeWorkflowQueryApiAdapter implements RiskDisputeWorkflowQueryApi {

    private final RiskDisputeWorkflowQueryPort delegate;

    @Override
    public RiskDisputeWorkflowResult inspect(String disputeId) {
        return delegate.inspect(disputeId);
    }
}
