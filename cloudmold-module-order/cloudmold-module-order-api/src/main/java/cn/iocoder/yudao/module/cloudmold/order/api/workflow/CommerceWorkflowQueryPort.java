package cn.iocoder.yudao.module.cloudmold.order.api.workflow;

/**
 * Fixed idempotent read port used by durable orchestrators after domain commands.
 *
 * Domain writes remain on their existing governed command/Saga APIs. Repeated calls
 * only read the current tenant's System-of-Record facts.
 */
public interface CommerceWorkflowQueryPort {
    CommerceWorkflowResult inspectOrderToCash(String orderId);

    CommerceWorkflowResult inspectOrderCancellation(String cancellationSagaId);

    CommerceWorkflowResult inspectFulfillmentException(String orderId);

    CommerceWorkflowResult inspectReturnRefund(String afterSaleId);
}
