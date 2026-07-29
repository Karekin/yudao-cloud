package cn.iocoder.yudao.module.cloudmold.customerservice.service.workflow;

import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow.CustomerResolutionWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerResolutionWorkflowQueryApiAdapter implements CustomerResolutionWorkflowQueryApi {

    private final CustomerResolutionWorkflowQueryPort delegate;

    @Override
    public CustomerResolutionWorkflowResult inspect(String ticketId) {
        return delegate.inspect(ticketId);
    }
}
