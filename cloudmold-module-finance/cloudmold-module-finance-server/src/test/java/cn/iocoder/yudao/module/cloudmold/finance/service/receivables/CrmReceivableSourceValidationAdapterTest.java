package cn.iocoder.yudao.module.cloudmold.finance.service.receivables;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractSourceView;
import cn.iocoder.yudao.module.cloudmold.crm.api.contract.SalesContractValidationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrmReceivableSourceValidationAdapterTest {

    private final SalesContractValidationApi validationApi = mock(SalesContractValidationApi.class);
    private final CrmReceivableSourceValidationAdapter adapter =
            new CrmReceivableSourceValidationAdapter(validationApi);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void acceptsActiveMatchingContract() {
        TenantContextHolder.setTenantId(7L);
        when(validationApi.requireReceivableSource("customer-1", "contract-1")).thenReturn(
                SalesContractSourceView.builder().salesContractId("contract-1").customerId("customer-1")
                        .currencyCode("CNY").totalAmountMinor(1000L).status("ACTIVE").build());

        adapter.requireActiveSalesReceivableSource(7L, "customer-1", "contract-1");
    }

    @Test
    void rejectsNonActiveContract() {
        TenantContextHolder.setTenantId(7L);
        when(validationApi.requireReceivableSource("customer-1", "contract-1")).thenReturn(
                SalesContractSourceView.builder().salesContractId("contract-1").customerId("customer-1")
                        .currencyCode("CNY").totalAmountMinor(1000L).status("PENDING_APPROVAL").build());

        assertThatThrownBy(() -> adapter.requireActiveSalesReceivableSource(7L, "customer-1", "contract-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");
    }
}
