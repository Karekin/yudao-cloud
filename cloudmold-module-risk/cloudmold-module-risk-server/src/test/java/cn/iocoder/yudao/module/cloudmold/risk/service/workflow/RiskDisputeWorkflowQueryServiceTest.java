package cn.iocoder.yudao.module.cloudmold.risk.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.risk.api.workflow.RiskDisputeWorkflowResult.Status;
import cn.iocoder.yudao.module.cloudmold.risk.dal.dataobject.RiskRecords.*;
import cn.iocoder.yudao.module.cloudmold.risk.dal.mysql.RiskStoreMapper;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RiskDisputeWorkflowQueryServiceTest {
    private final RiskStoreMapper mapper = mock(RiskStoreMapper.class);
    private final RiskDisputeWorkflowQueryService service = new RiskDisputeWorkflowQueryService(mapper);

    @BeforeEach void setUp() { TenantContextHolder.setTenantId(31L); }
    @AfterEach void tearDown() { TenantContextHolder.clear(); }

    @Test
    void lostDisputeWithoutLossAuthorityRemainsWaiting() {
        stubTerminalFacts();
        when(mapper.selectLossEntriesByDispute(31L, "d-1")).thenReturn(List.of());

        assertThat(service.inspect("d-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.WAITING);
            assertThat(result.getBlockers()).contains("败诉争议缺少损失台账");
        });
    }

    @Test
    void succeedsWithInvestigationDecisionAndLossLedger() {
        stubTerminalFacts();
        when(mapper.selectLossEntriesByDispute(31L, "d-1")).thenReturn(List.of(new LossEntry()
                .setLossEntryId("l-1").setEntryType("CHARGEBACK_LOSS")));

        assertThat(service.inspect("d-1")).satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo(Status.SUCCEEDED);
            assertThat(result.getSummary()).contains("调查、人工决策和损失核验");
        });
    }

    private void stubTerminalFacts() {
        when(mapper.selectPaymentDispute(31L, "d-1")).thenReturn(new PaymentDispute()
                .setDisputeId("d-1").setCaseId("case-1").setDecisionId("dec-1")
                .setStatus("LOST").setVersion(3L));
        when(mapper.selectReviewCase(31L, "case-1")).thenReturn(new ReviewCase()
                .setCaseId("case-1").setStatus("CLOSED").setVersion(4L));
        when(mapper.selectDecision(31L, "dec-1")).thenReturn(new Decision()
                .setDecisionId("dec-1").setDecisionType("CONFIRM_RISK"));
    }
}
