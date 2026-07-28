package cn.iocoder.yudao.module.cloudmold.payment.api.workflow;

public interface FinanceOperationsWorkflowQueryPort {

    FinanceOperationsWorkflowResult inspectPaymentReconciliation(String orderId, String paymentId);

    FinanceOperationsWorkflowResult inspectFinanceClose(String closeKey);
}
