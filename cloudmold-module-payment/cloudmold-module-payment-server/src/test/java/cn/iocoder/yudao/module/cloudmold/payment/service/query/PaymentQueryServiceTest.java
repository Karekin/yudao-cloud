package cn.iocoder.yudao.module.cloudmold.payment.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.payment.controller.admin.vo.PaymentPageReqVO;
import cn.iocoder.yudao.module.cloudmold.payment.dal.mysql.PaymentQueryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentQueryServiceTest {

    private final PaymentQueryMapper paymentQueryMapper = mock(PaymentQueryMapper.class);
    private final PaymentQueryService service = new PaymentQueryService(paymentQueryMapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(11L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldNormalizeFiltersMaskProviderReferenceAndComputeRemainingAmount() {
        PaymentPageReqVO request = new PaymentPageReqVO();
        request.setPageNo(2);
        request.setPageSize(10);
        request.setPaymentNo("  CMP-001 ");
        request.setOrderId(" order-1 ");
        request.setStatus(" captured ");
        request.setProviderCode(" internal_test ");
        request.setTestMode(Boolean.TRUE);
        PaymentPageRow row = new PaymentPageRow();
        row.setPaymentId("payment-1");
        row.setPaymentNo("CMP-001");
        row.setOrderId("order-1");
        row.setStatus("CAPTURED");
        row.setPayableAmountMinor(530L);
        row.setCapturedAmountMinor(530L);
        row.setRefundedAmountMinor(100L);
        row.setCurrencyCode("CNY");
        row.setProviderCode("INTERNAL_TEST");
        row.setProviderTransactionReference("provider-capture-1");
        row.setTestMode(true);
        row.setAggregateVersion(3L);
        row.setCapturedAt(LocalDateTime.of(2026, 7, 12, 0, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 7, 12, 0, 5));
        when(paymentQueryMapper.countPaymentPage(11L, "CMP-001", "order-1", "CAPTURED", "INTERNAL_TEST", true))
                .thenReturn(11L);
        when(paymentQueryMapper.selectPaymentPage(11L, "CMP-001", "order-1", "CAPTURED", "INTERNAL_TEST",
                true, 10L, 10)).thenReturn(List.of(row));

        PageResult<PaymentPageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isEqualTo(11L);
        assertThat(result.getList()).singleElement().satisfies(item -> {
            assertThat(item.getPaymentId()).isEqualTo("payment-1");
            assertThat(item.getRemainingAmountMinor()).isEqualTo(430L);
            assertThat(item.getProviderTransactionReferenceMasked()).isEqualTo("prov**********re-1");
            assertThat(item.getExecutionMode()).isEqualTo("INTERNAL_TEST");
            assertThat(item.getProviderCode()).isEqualTo("INTERNAL_TEST");
        });
        verify(paymentQueryMapper).selectPaymentPage(11L, "CMP-001", "order-1", "CAPTURED", "INTERNAL_TEST",
                true, 10L, 10);
    }

    @Test
    void shouldTreatInternalTestProviderAsInternalTestEvenWhenFlagIsFalseAndMaskShortReferences() {
        PaymentPageReqVO request = new PaymentPageReqVO();
        request.setTestMode(Boolean.FALSE);
        PaymentPageRow row = new PaymentPageRow();
        row.setPaymentId("payment-2");
        row.setPaymentNo("CMP-002");
        row.setOrderId("order-2");
        row.setStatus("REFUNDED");
        row.setCapturedAmountMinor(300L);
        row.setRefundedAmountMinor(300L);
        row.setCurrencyCode("CNY");
        row.setProviderCode("INTERNAL_TEST");
        row.setProviderTransactionReference("tx-123");
        row.setTestMode(false);
        row.setAggregateVersion(2L);
        when(paymentQueryMapper.countPaymentPage(11L, null, null, null, null, false)).thenReturn(1L);
        when(paymentQueryMapper.selectPaymentPage(11L, null, null, null, null, false, 0L, 10))
                .thenReturn(List.of(row));

        PageResult<PaymentPageItem> result = service.getPage(request);

        assertThat(result.getList()).singleElement().satisfies(item -> {
            assertThat(item.getExecutionMode()).isEqualTo("INTERNAL_TEST");
            assertThat(item.getProviderTransactionReferenceMasked()).isEqualTo("******");
            assertThat(item.getRemainingAmountMinor()).isZero();
        });
    }

    @Test
    void shouldShortCircuitWhenNoPaymentsMatch() {
        PaymentPageReqVO request = new PaymentPageReqVO();
        when(paymentQueryMapper.countPaymentPage(11L, null, null, null, null, null)).thenReturn(0L);

        PageResult<PaymentPageItem> result = service.getPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(paymentQueryMapper, never()).selectPaymentPage(anyLong(), any(), any(), any(), any(), any(),
                anyLong(), anyInt());
    }
}
