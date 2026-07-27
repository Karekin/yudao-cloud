package cn.iocoder.yudao.module.cloudmold.merchant.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.merchant.api.workflow.MerchantOnboardingWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantDepositStoreMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantStoreMapper;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MerchantOnboardingWorkflowQueryServiceTest {
    private final MerchantStoreMapper merchantMapper = mock(MerchantStoreMapper.class);
    private final MerchantDepositStoreMapper depositMapper = mock(MerchantDepositStoreMapper.class);
    private final MerchantOnboardingWorkflowQueryService service =
            new MerchantOnboardingWorkflowQueryService(merchantMapper, depositMapper);

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(7L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void missingAuthorityStaysPrepareAndReadOnly() {
        assertThat(service.inspect("app-missing")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.PREPARE);
            assertThat(result.getTerminal()).isFalse();
            assertThat(result.getBlockers()).contains("Merchant SoR 中不存在入驻申请");
        });
        verify(merchantMapper).selectApplication(7L, "app-missing");
        verifyNoInteractions(depositMapper);
    }

    @Test
    void succeedsOnlyWhenMerchantShopOwnerAndDepositAreReady() {
        when(merchantMapper.selectApplication(7L, "app-1")).thenReturn(new MerchantOnboardingApplicationDO()
                .setApplicationId("app-1").setStatus("APPROVED").setMerchantId("m-1").setShopId("s-1")
                .setOwnerAssignmentId("a-1").setVersion(4L));
        when(merchantMapper.selectMerchant(7L, "m-1")).thenReturn(new MerchantAccountDO()
                .setMerchantId("m-1").setMerchantCode("DEWU").setStatus("ACTIVE").setVersion(2L));
        when(merchantMapper.selectShop(7L, "s-1")).thenReturn(new MerchantShopDO()
                .setShopId("s-1").setStatus("ACTIVE").setVersion(2L));
        when(merchantMapper.selectAssignment(7L, "a-1")).thenReturn(new MerchantOperatorAssignmentDO()
                .setAssignmentId("a-1").setStatus("ACTIVE").setVersion(1L));
        when(depositMapper.selectAccountsByMerchant(7L, "m-1")).thenReturn(List.of(new MerchantDepositAccountDO()
                .setAccountId("d-1").setCurrency("CNY").setEnforcementStatus("ENFORCED")
                .setCoverageStatus("SUFFICIENT").setVersion(3L)));

        assertThat(service.inspect("app-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getTerminal()).isTrue();
            assertThat(result.getSummary()).contains("已完成入驻并具备经营资格");
            assertThat(result.getArtifacts()).extracting("type")
                    .containsExactly("ONBOARDING_APPLICATION", "MERCHANT", "SHOP", "OWNER_ASSIGNMENT", "DEPOSIT_ACCOUNT");
        });
    }
}
