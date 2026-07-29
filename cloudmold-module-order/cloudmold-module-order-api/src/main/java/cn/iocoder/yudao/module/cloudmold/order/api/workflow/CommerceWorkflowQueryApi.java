package cn.iocoder.yudao.module.cloudmold.order.api.workflow;

/**
 * Governed, repeatable read contracts for commerce workflow outcomes.
 */
public interface CommerceWorkflowQueryApi {

    CommerceWorkflowResult inspectOrderToCash(String orderId);

    CommerceWorkflowResult inspectOrderCancellation(String cancellationSagaId);

    CommerceWorkflowResult inspectFulfillmentException(String orderId);

    CommerceWorkflowResult inspectReturnRefund(String afterSaleId);
}
