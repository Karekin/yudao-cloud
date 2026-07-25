package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.yudao.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class YudaoBpmApprovalWorkflowAdapterTest {

    private final BpmProcessInstanceApi bpm = mock(BpmProcessInstanceApi.class);
    private final YudaoBpmApprovalWorkflowAdapter adapter = new YudaoBpmApprovalWorkflowAdapter(bpm);

    @Test
    void startsVersionedProcessWithOnlyFrozenAllowlistedVariables() {
        when(bpm.createProcessInstance(eq(100L), any())).thenReturn(CommonResult.success("process-17"));
        ApprovalWorkflowStartCandidate candidate = candidate();

        assertThat(adapter.start(candidate)).isEqualTo("process-17");

        ArgumentCaptor<BpmProcessInstanceCreateReqDTO> request =
                ArgumentCaptor.forClass(BpmProcessInstanceCreateReqDTO.class);
        verify(bpm).createProcessInstance(eq(100L), request.capture());
        assertThat(request.getValue().getProcessDefinitionKey()).isEqualTo("cloudmold-agent-approval-v1");
        assertThat(request.getValue().getBusinessKey()).isEqualTo("cloudmold-agent-approval:17:approval-1");
        assertThat(request.getValue().getVariables()).containsExactly(
                org.assertj.core.api.Assertions.entry("approval_id", "approval-1"),
                org.assertj.core.api.Assertions.entry("work_order_id", "work-1"),
                org.assertj.core.api.Assertions.entry("action_code", "purchase.commit"),
                org.assertj.core.api.Assertions.entry("role_code", "buyer"),
                org.assertj.core.api.Assertions.entry("risk_level", "R3"),
                org.assertj.core.api.Assertions.entry("scope_hash", "a".repeat(64)));
        assertThat(request.getValue().getStartUserSelectAssignees())
                .containsOnlyKeys(YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY);
        assertThat(request.getValue().getStartUserSelectAssignees()
                .get(YudaoBpmApprovalWorkflowAdapter.APPROVAL_TASK_KEY)).containsExactly(200L);
    }

    @Test
    void rejectsEmptyProcessInstanceId() {
        when(bpm.createProcessInstance(eq(100L), any())).thenReturn(CommonResult.success(""));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.start(candidate()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("BPM returned an empty process instance id");
    }

    @Test
    void rejectsMissingOrSelfApproverBeforeCallingBpm() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate().setApproverUserId(null))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approver is missing");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> adapter.start(candidate().setApproverUserId(100L))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ from requester");
        verifyNoInteractions(bpm);
    }

    private static ApprovalWorkflowStartCandidate candidate() {
        return new ApprovalWorkflowStartCandidate().setTenantId(17L).setApprovalId("approval-1")
                .setWorkOrderId("work-1").setActionCode("purchase.commit").setRoleCode("buyer")
                .setRiskLevel("R3").setRequesterUserId(100L).setApproverUserId(200L).setScopeHash("a".repeat(64))
                .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                .setBusinessKey("cloudmold-agent-approval:17:approval-1")
                .setStatus("START_REQUESTED").setVersion(1L);
    }

}
