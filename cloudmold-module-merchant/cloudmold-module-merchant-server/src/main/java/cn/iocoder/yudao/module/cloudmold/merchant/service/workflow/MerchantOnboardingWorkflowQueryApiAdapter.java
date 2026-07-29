package cn.iocoder.yudao.module.cloudmold.merchant.service.workflow;

import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantOnboardingWorkflowQueryApiAdapter implements MerchantOnboardingWorkflowQueryApi {

    private final MerchantOnboardingWorkflowQueryPort delegate;

    @Override
    public MerchantOnboardingWorkflowResult inspect(String applicationId) {
        return delegate.inspect(applicationId);
    }
}
