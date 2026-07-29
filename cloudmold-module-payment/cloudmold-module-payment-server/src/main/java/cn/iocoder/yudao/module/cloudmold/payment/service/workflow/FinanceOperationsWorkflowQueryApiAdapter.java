package cn.iocoder.yudao.module.cloudmold.payment.service.workflow;

import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinanceOperationsWorkflowQueryApiAdapter implements FinanceOperationsWorkflowQueryApi {

    private final FinanceOperationsWorkflowQueryPort delegate;

    @Override
    public FinanceOperationsWorkflowResult inspectPaymentReconciliation(String orderId, String paymentId) {
        return delegate.inspectPaymentReconciliation(orderId, paymentId);
    }

    @Override
    public FinanceOperationsWorkflowResult inspectFinanceClose(String closeKey) {
        return delegate.inspectFinanceClose(closeKey);
    }
}
