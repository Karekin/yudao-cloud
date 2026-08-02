package cn.iocoder.yudao.module.cloudmold.merchant.service.workflow;

import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantManagedAdmissionWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantManagedAdmissionWorkflowQueryApiAdapter implements MerchantManagedAdmissionWorkflowQueryApi {

    private final MerchantManagedAdmissionWorkflowQueryPort delegate;

    @Override
    public MerchantManagedAdmissionWorkflowResult inspect(String applicationId) {
        return delegate.inspect(applicationId);
    }

    @Override
    public MerchantManagedAdmissionWorkflowResult inspectByMerchantId(String merchantId) {
        return delegate.inspectByMerchantId(merchantId);
    }
}
