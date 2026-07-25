package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AgentApprovalWorkflowReconcilerTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentApprovalWorkflowService workflows = mock(AgentApprovalWorkflowService.class);
    private final AgentApprovalWorkflowProperties properties = new AgentApprovalWorkflowProperties();
    private final AgentApprovalWorkflowReconciler reconciler =
            new AgentApprovalWorkflowReconciler(mapper, workflows, properties);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void boundsBatchAndRunsEachCandidateInItsTenant() {
        properties.setBatchSize(500);
        ApprovalWorkflowStartCandidate first = candidate(17L, "approval-1");
        ApprovalWorkflowStartCandidate second = candidate(23L, "approval-2");
        when(mapper.selectApprovalWorkflowStartCandidates(100)).thenReturn(List.of(first, second));
        when(workflows.start(any())).thenAnswer(invocation -> {
            ApprovalWorkflowStartCandidate candidate = invocation.getArgument(0);
            assertThat(TenantContextHolder.getRequiredTenantId()).isEqualTo(candidate.getTenantId());
            return true;
        });

        reconciler.reconcile();

        verify(workflows).start(first);
        verify(workflows).start(second);
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void oneConflictedCandidateDoesNotBlockTheRestOfTheBatch() {
        properties.setBatchSize(20);
        ApprovalWorkflowStartCandidate first = candidate(17L, "approval-1");
        ApprovalWorkflowStartCandidate second = candidate(23L, "approval-2");
        when(mapper.selectApprovalWorkflowStartCandidates(20)).thenReturn(List.of(first, second));
        when(workflows.start(first)).thenThrow(new IllegalStateException("claim conflict"));
        when(workflows.start(second)).thenReturn(true);

        reconciler.reconcile();

        verify(workflows).start(first);
        verify(workflows).start(second);
    }

    private static ApprovalWorkflowStartCandidate candidate(Long tenantId, String approvalId) {
        return new ApprovalWorkflowStartCandidate().setTenantId(tenantId).setApprovalId(approvalId)
                .setStatus("START_REQUESTED").setVersion(1L);
    }

}
