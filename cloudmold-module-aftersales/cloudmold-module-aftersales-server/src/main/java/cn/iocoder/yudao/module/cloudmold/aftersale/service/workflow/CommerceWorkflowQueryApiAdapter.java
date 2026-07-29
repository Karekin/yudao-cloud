package cn.iocoder.yudao.module.cloudmold.aftersale.service.workflow;

import cn.iocoder.yudao.module.cloudmold.order.api.workflow.CommerceWorkflowQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.workflow.CommerceWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.order.api.workflow.CommerceWorkflowResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommerceWorkflowQueryApiAdapter implements CommerceWorkflowQueryApi {

    private final CommerceWorkflowQueryPort delegate;

    @Override
    public CommerceWorkflowResult inspectOrderToCash(String orderId) {
        return delegate.inspectOrderToCash(orderId);
    }

    @Override
    public CommerceWorkflowResult inspectOrderCancellation(String cancellationSagaId) {
        return delegate.inspectOrderCancellation(cancellationSagaId);
    }

    @Override
    public CommerceWorkflowResult inspectFulfillmentException(String orderId) {
        return delegate.inspectFulfillmentException(orderId);
    }

    @Override
    public CommerceWorkflowResult inspectReturnRefund(String afterSaleId) {
        return delegate.inspectReturnRefund(afterSaleId);
    }
}
