package cn.iocoder.yudao.module.cloudmold.finance.service.query.receivables;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesSummaryView;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.receivables.ReceivablesMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReceivablesQueryServiceTest {
    private final ReceivablesMapper mapper = mock(ReceivablesMapper.class);
    private final ReceivablesQueryService service = new ReceivablesQueryService(mapper);

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void summarizeByCustomerIsTenantScoped() {
        TenantContextHolder.setTenantId(162L);
        ReceivablesSummaryView view = ReceivablesSummaryView.builder().customerId("customer-1")
                .currencyCode("CNY").receivablePlanAmountMinor(10000L).build();
        when(mapper.selectCustomerSummaries(162L, "customer-1")).thenReturn(List.of(view));

        assertThat(service.summarizeByCustomer("customer-1")).containsExactly(view);
        verify(mapper, never()).selectCustomerSummaries(argThat(id -> id == null || id != 162L), anyString());
    }

    @Test
    void summarizeBySalesContractReturnsMapperProjection() {
        TenantContextHolder.setTenantId(162L);
        ReceivablesSummaryView view = ReceivablesSummaryView.builder().customerId("customer-1")
                .salesContractId("contract-1").currencyCode("USD").receiptAmountMinor(2500L).build();
        when(mapper.selectSalesContractSummaries(162L, "contract-1")).thenReturn(List.of(view));

        assertThat(service.summarizeBySalesContract("contract-1")).containsExactly(view);
    }
}
