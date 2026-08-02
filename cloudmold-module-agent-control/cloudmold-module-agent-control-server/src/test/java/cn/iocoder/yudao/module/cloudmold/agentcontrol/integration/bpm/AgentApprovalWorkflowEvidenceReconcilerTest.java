package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentApprovalWorkflowEvidenceReconcilerTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentApprovalWorkflowService workflows = mock(AgentApprovalWorkflowService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final HistoricProcessInstanceQuery query = mock(HistoricProcessInstanceQuery.class);
    private final AgentApprovalWorkflowProperties properties = properties();
    private final AgentApprovalWorkflowEvidenceReconciler reconciler =
            new AgentApprovalWorkflowEvidenceReconciler(mapper, workflows, properties, history);

    @Test
    void recoversCompletedTerminateEndOnlyWithAllowlistedAiEvidence() {
        ApprovalWorkflowBinding binding = binding("RUNNING", "process-1");
        HistoricProcessInstance process = mock(HistoricProcessInstance.class);
        when(mapper.selectApprovalWorkflowRecoveryCandidates(any(), eq(20))).thenReturn(List.of(binding));
        when(history.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.singleResult()).thenReturn(process);
        when(process.getId()).thenReturn("process-1");
        when(process.getBusinessKey()).thenReturn(binding.getBusinessKey());
        when(process.getProcessDefinitionKey()).thenReturn(binding.getProcessDefinitionKey());
        when(process.getEndTime()).thenReturn(new java.util.Date());
        when(process.getProcessVariables()).thenReturn(Map.of(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS,
                BpmProcessInstanceStatusEnum.RUNNING.getStatus(),
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TERMINAL_OPERATOR_USER_ID, 229L,
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TERMINAL_TASK_ID, "task-ai-1",
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TERMINAL_TASK_DEFINITION_KEY,
                YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY));

        reconciler.reconcile();

        ArgumentCaptor<BpmProcessInstanceStatusEvent> event =
                ArgumentCaptor.forClass(BpmProcessInstanceStatusEvent.class);
        verify(workflows).observe(event.capture());
        assertThat(event.getValue().getStatus()).isEqualTo(BpmProcessInstanceStatusEnum.APPROVE.getStatus());
        assertThat(event.getValue().getTerminalOperatorUserId()).isEqualTo(229L);
    }

    @Test
    void retriesUncertainStartOnlyAfterHistoryProvesTheBusinessKeyAbsent() {
        ApprovalWorkflowBinding binding = binding("START_UNCERTAIN", null);
        when(mapper.selectApprovalWorkflowRecoveryCandidates(any(), eq(20))).thenReturn(List.of(binding));
        when(history.createHistoricProcessInstanceQuery()).thenReturn(query);
        when(query.processInstanceBusinessKey(binding.getBusinessKey())).thenReturn(query);
        when(query.includeProcessVariables()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);
        when(mapper.resetAbsentUncertainApprovalWorkflowStart(eq(162L), eq("approval-1"), eq(4L), any()))
                .thenReturn(1);

        reconciler.reconcile();

        verify(mapper).resetAbsentUncertainApprovalWorkflowStart(
                eq(162L), eq("approval-1"), eq(4L), any(LocalDateTime.class));
        verifyNoInteractions(workflows);
    }

    private static AgentApprovalWorkflowProperties properties() {
        AgentApprovalWorkflowProperties value = new AgentApprovalWorkflowProperties();
        value.setEnabled(true);
        value.setRecoveryGraceSeconds(10);
        value.setAiReviewerOrSignEnabled(true);
        value.setAiReviewerUserIds(Set.of(229L));
        value.setAiReviewAllowedRiskLevels(Set.of("R3"));
        return value;
    }

    private static ApprovalWorkflowBinding binding(String status, String processInstanceId) {
        return new ApprovalWorkflowBinding().setApprovalId("approval-1").setTenantId(162L)
                .setWorkOrderId("work-1").setActionCode("risk.dispute-resolve")
                .setRoleCode("risk-operations").setRiskLevel("R3")
                .setProcessDefinitionKey("cloudmold-agent-approval-v1")
                .setBusinessKey("cloudmold-agent-approval:162:approval-1")
                .setProcessInstanceId(processInstanceId).setStatus(status).setVersion(4L)
                .setUpdatedAt(LocalDateTime.now().minusMinutes(5));
    }
}
