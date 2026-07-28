package cn.iocoder.yudao.module.cloudmold.payment.service.workflow;

import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsView;
import cn.iocoder.yudao.module.cloudmold.payment.api.workflow.FinanceOperationsWorkflowResult.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FinanceOperationsWorkflowQueryServiceTest {

    private final PaymentWorkflowFactsApi factsApi = mock(PaymentWorkflowFactsApi.class);
    private final FinanceOperationsWorkflowQueryService service = new FinanceOperationsWorkflowQueryService(factsApi);

    @Test
    void realCapturedPaymentWaitsForChannelAndFinanceEvidence() {
        when(factsApi.get("order-1", "payment-1")).thenReturn(payment("CAPTURED", "WECHAT_PAY", false,
                530L, 0L, 2L));

        assertThat(service.inspectPaymentReconciliation("order-1", "payment-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.WAITING);
            assertThat(result.getSummary()).contains("5.30 CNY").contains("渠道账单");
            assertThat(result.getBlockers()).contains("缺少规范 Finance 总账凭证");
        });
    }

    @Test
    void refundedPaymentStillCannotClaimFinancialClose() {
        when(factsApi.get("order-1", "payment-1")).thenReturn(payment("REFUNDED", "ALIPAY", false,
                530L, 530L, 3L));

        assertThat(service.inspectPaymentReconciliation("order-1", "payment-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.WAITING);
            assertThat(result.getSummary()).contains("退款 5.30 CNY");
            assertThat(result.getTerminal()).isFalse();
        });
    }

    @Test
    void internalTestPaymentIsNotFinancialEvidence() {
        when(factsApi.get("order-1", "payment-1")).thenReturn(payment("CAPTURED", "INTERNAL_TEST", true,
                530L, 0L, 1L));

        assertThat(service.inspectPaymentReconciliation("order-1", "payment-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.PREPARE);
            assertThat(result.getSummary()).contains("不能作为渠道对账");
        });
    }

    @Test
    void financeCloseRemainsPrepareWithoutCanonicalAuthority() {
        assertThat(service.inspectFinanceClose("2026-07")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.PREPARE);
            assertThat(result.getBlockers()).anyMatch(value -> value.contains("会计期间"));
            assertThat(result.getArtifacts()).isEmpty();
        });
        verifyNoInteractions(factsApi);
    }

    private static PaymentWorkflowFactsView payment(String status, String provider, boolean testMode,
                                                    long captured, long refunded, long version) {
        return PaymentWorkflowFactsView.builder().paymentId("payment-1").orderId("order-1").status(status)
                .aggregateVersion(version).capturedAmountMinor(captured).refundedAmountMinor(refunded)
                .currencyCode("CNY").providerCode(provider).testMode(testMode).build();
    }
}
