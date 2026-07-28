package cn.iocoder.yudao.module.cloudmold.payment.service.workflow;

import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsView;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowQueryPort;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowResult;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowResult.Artifact;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowResult.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinanceOperationsWorkflowQueryService implements FinanceOperationsWorkflowQueryPort {

    private static final String PAYMENT_RECONCILIATION = "PaymentReconciliationWorkflow";
    private static final String FINANCE_CLOSE = "FinanceCloseWorkflow";

    private final PaymentWorkflowFactsApi paymentWorkflowFactsApi;

    @Override
    public FinanceOperationsWorkflowResult inspectPaymentReconciliation(String orderId, String paymentId) {
        requireText(orderId, "orderId");
        requireText(paymentId, "paymentId");
        PaymentWorkflowFactsView payment = paymentWorkflowFactsApi.get(orderId.trim(), paymentId.trim());
        List<Artifact> artifacts = List.of(Artifact.builder().type("PAYMENT").id(payment.getPaymentId())
                .status(payment.getStatus()).version(payment.getAggregateVersion())
                .label("订单 " + payment.getOrderId() + " 的支付记录").build());

        if (Boolean.TRUE.equals(payment.getTestMode()) || "INTERNAL_TEST".equals(payment.getProviderCode())) {
            return result(PAYMENT_RECONCILIATION, payment.getPaymentId(), Status.PREPARE, "真实渠道接入",
                    "当前支付属于内部测试，不能作为渠道对账或财务结算依据", payment.getAggregateVersion(),
                    List.of("支付渠道为内部测试", "缺少真实渠道账单和结算批次"),
                    List.of("接入真实支付渠道", "导入渠道账单并建立对账批次"), artifacts);
        }

        String captured = money(payment.getCapturedAmountMinor(), payment.getCurrencyCode());
        String refunded = money(payment.getRefundedAmountMinor(), payment.getCurrencyCode());
        if ("PENDING".equals(payment.getStatus())) {
            return result(PAYMENT_RECONCILIATION, payment.getPaymentId(), Status.RUNNING, "等待支付终态",
                    "支付尚未完成，当前已收款 " + captured, payment.getAggregateVersion(),
                    List.of("支付仍为 PENDING"), List.of("等待支付渠道终态回执"), artifacts);
        }
        if (!List.of("CAPTURED", "PARTIALLY_REFUNDED", "REFUNDED").contains(payment.getStatus())) {
            return result(PAYMENT_RECONCILIATION, payment.getPaymentId(), Status.WAITING, "支付状态核对",
                    "支付处于未识别状态，暂不能进入财务对账", payment.getAggregateVersion(),
                    List.of("未知支付状态：" + payment.getStatus()), List.of("人工核对支付状态"), artifacts);
        }

        return result(PAYMENT_RECONCILIATION, payment.getPaymentId(), Status.WAITING, "渠道与财务对账",
                "业务账已记录收款 " + captured + "、退款 " + refunded
                        + "；仍需渠道账单、手续费和财务凭证完成对账",
                payment.getAggregateVersion(),
                List.of("缺少渠道账单事实", "缺少手续费与结算批次", "缺少规范 Finance 总账凭证"),
                List.of("导入渠道账单", "核对收款、退款和手续费", "生成差异工作单", "经审批后完成财务入账"),
                artifacts);
    }

    @Override
    public FinanceOperationsWorkflowResult inspectFinanceClose(String closeKey) {
        requireText(closeKey, "closeKey");
        return result(FINANCE_CLOSE, closeKey.trim(), Status.PREPARE, "财务权威准备",
                "日结/月结尚不能自动执行：当前没有规范 Finance System of Record", 0L,
                List.of("缺少会计期间、科目、凭证、应收应付、结算批次和关账状态权威"),
                List.of("建立 Finance 权威模型", "接入支付渠道账单和商家结算", "建立三账对账与差异工作单",
                        "配置财务审批和不可变关账证据"),
                List.of());
    }

    private static FinanceOperationsWorkflowResult result(String workflowType, String key, Status status,
                                                           String phase, String summary, Long version,
                                                           List<String> blockers, List<String> nextActions,
                                                           List<Artifact> artifacts) {
        return FinanceOperationsWorkflowResult.builder().workflowType(workflowType)
                .workflowInstanceKey(workflowType + ":" + key).businessKey(key).status(status).phase(phase)
                .terminal(false).actionRequired(!nextActions.isEmpty()).summary(summary).aggregateVersion(version)
                .blockers(blockers).nextActions(nextActions).artifacts(artifacts).build();
    }

    private static String money(Long amountMinor, String currency) {
        long value = amountMinor == null ? 0L : amountMinor;
        String code = StringUtils.hasText(currency) ? currency : "UNKNOWN";
        return BigDecimal.valueOf(value, 2).setScale(2, RoundingMode.UNNECESSARY) + " " + code;
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
