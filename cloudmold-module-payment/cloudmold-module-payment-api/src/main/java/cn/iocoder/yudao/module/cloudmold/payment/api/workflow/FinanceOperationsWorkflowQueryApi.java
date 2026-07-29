package cn.iocoder.yudao.module.cloudmold.payment.api.workflow;

/**
 * Governed, repeatable read contracts for finance operations.
 */
public interface FinanceOperationsWorkflowQueryApi {

    FinanceOperationsWorkflowResult inspectPaymentReconciliation(String orderId, String paymentId);

    FinanceOperationsWorkflowResult inspectFinanceClose(String closeKey);
}
