package cn.iocoder.yudao.module.cloudmold.supplier.service.query;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierAdmissionStatus;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileView;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierStatus;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierProfileMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierProfileQueryServiceTest {
    private final SupplierProfileMapper mapper = mock(SupplierProfileMapper.class);
    private final SupplierProfileQueryService service = new SupplierProfileQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(162L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void returnsExactTenantScopedProfile() {
        SupplierProfileView profile = SupplierProfileView.builder()
                .supplierId("supplier-01").status(SupplierStatus.CANDIDATE)
                .admissionStatus(SupplierAdmissionStatus.DRAFT).build();
        when(mapper.selectProfile(162L, "supplier-01")).thenReturn(profile);

        assertThat(service.requireSupplier("supplier-01")).isSameAs(profile);
    }

    @Test
    void returnsOnlyExactProcurementEligibleSupplier() {
        SupplierProfileView profile = SupplierProfileView.builder()
                .supplierId("supplier-01").status(SupplierStatus.ACTIVE)
                .admissionStatus(SupplierAdmissionStatus.ADMITTED).build();
        when(mapper.selectProfile(162L, "supplier-01")).thenReturn(profile);

        assertThat(service.requireProcurementEligibleSupplier("supplier-01")).isSameAs(profile);
    }

    @Test
    void failsClosedForNonEligibleSupplier() {
        when(mapper.selectProfile(162L, "supplier-01")).thenReturn(SupplierProfileView.builder()
                .supplierId("supplier-01").status(SupplierStatus.ACTIVE)
                .admissionStatus(SupplierAdmissionStatus.UNDER_REVIEW).build());

        assertThatThrownBy(() -> service.requireProcurementEligibleSupplier("supplier-01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE and ADMITTED");
    }
}
