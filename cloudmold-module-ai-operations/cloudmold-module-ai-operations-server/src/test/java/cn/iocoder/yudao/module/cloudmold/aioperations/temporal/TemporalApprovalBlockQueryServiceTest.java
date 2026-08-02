package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalApprovalBlockQueryServiceTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldExposeOnlyTheTemporalInstanceBlockedByApproval() {
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        TemporalApprovalBlockQueryService service = new TemporalApprovalBlockQueryService(mapper);
        TenantContextHolder.setTenantId(162L);
        when(mapper.selectRunBindingByApproval(162L, "approval-1")).thenReturn(
                new TemporalRunBindingRecord()
                        .setApprovalId("approval-1")
                        .setWorkOrderId("work-order-1")
                        .setTemporalWorkflowId("temporal-workflow-1")
                        .setTemporalRunId("temporal-run-1")
                        .setStatus("WAITING_APPROVAL"));

        TemporalApprovalBlockView result = service.getByApprovalId("approval-1");

        assertThat(result).extracting(
                        TemporalApprovalBlockView::getApprovalId,
                        TemporalApprovalBlockView::getWorkOrderId,
                        TemporalApprovalBlockView::getTemporalWorkflowId,
                        TemporalApprovalBlockView::getTemporalRunId,
                        TemporalApprovalBlockView::getStatus)
                .containsExactly("approval-1", "work-order-1", "temporal-workflow-1", "temporal-run-1",
                        "WAITING_APPROVAL");
        verify(mapper).selectRunBindingByApproval(162L, "approval-1");
    }
}
