package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowBinding;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/**
 * Repairs the two safe gaps around BPM delivery: a start proven absent from Flowable, and a
 * completed OR-sign process whose terminate end event retained evidence but skipped the normal
 * completion callback. No business approval is inferred without allowlisted terminal AI evidence.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class AgentApprovalWorkflowEvidenceReconciler {

    private final AgentControlStoreMapper mapper;
    private final AgentApprovalWorkflowService workflows;
    private final AgentApprovalWorkflowProperties properties;
    private final HistoryService historyService;

    @Scheduled(fixedDelayString =
            "${cloudmold.agent-control.approval-workflow.reconcile-delay-ms:2000}")
    public synchronized void reconcile() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime cutoff = now.minusSeconds(Math.max(10, properties.getRecoveryGraceSeconds()));
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), 100));
        for (ApprovalWorkflowBinding binding :
                mapper.selectApprovalWorkflowRecoveryCandidates(cutoff, batchSize)) {
            TenantUtils.execute(binding.getTenantId(), () -> reconcile(binding, now));
        }
    }

    private void reconcile(ApprovalWorkflowBinding binding, LocalDateTime now) {
        HistoricProcessInstance process = findProcess(binding);
        if (process == null) {
            if ("START_UNCERTAIN".equals(binding.getStatus())
                    && mapper.resetAbsentUncertainApprovalWorkflowStart(binding.getTenantId(),
                    binding.getApprovalId(), binding.getVersion(), now) == 1) {
                log.warn("Recovered absent uncertain BPM approval start tenant={} approval={}",
                        binding.getTenantId(), binding.getApprovalId());
            }
            return;
        }
        if (!Objects.equals(process.getBusinessKey(), binding.getBusinessKey())
                || !Objects.equals(process.getProcessDefinitionKey(), binding.getProcessDefinitionKey())) {
            log.error("Refusing mismatched BPM approval recovery tenant={} approval={} process={}",
                    binding.getTenantId(), binding.getApprovalId(), process.getId());
            return;
        }
        if ("START_UNCERTAIN".equals(binding.getStatus())) {
            if (mapper.recoverExistingApprovalWorkflowStart(binding.getTenantId(), binding.getApprovalId(),
                    binding.getVersion(), process.getId(), now) == 0) {
                return;
            }
        }
        if (process.getEndTime() == null) {
            return;
        }
        Map<String, Object> variables = process.getProcessVariables();
        Long terminalUserId = asLong(variables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TERMINAL_OPERATOR_USER_ID));
        String terminalTaskId = Objects.toString(variables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TERMINAL_TASK_ID), null);
        String terminalTaskKey = Objects.toString(variables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_TERMINAL_TASK_DEFINITION_KEY), null);
        Integer status = asInteger(variables.get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_STATUS));
        if (Objects.equals(status, BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            if (!properties.isAiReviewerOrSignEnabled()
                    || !properties.getAiReviewerUserIds().contains(terminalUserId)
                    || !properties.getAiReviewAllowedRiskLevels().contains(binding.getRiskLevel())
                    || !YudaoBpmApprovalWorkflowAdapter.AI_APPROVAL_TASK_KEY.equals(terminalTaskKey)) {
                log.error("Refusing to infer completed BPM OR-sign result without allowlisted AI evidence "
                                + "tenant={} approval={} process={}",
                        binding.getTenantId(), binding.getApprovalId(), process.getId());
                return;
            }
            status = BpmProcessInstanceStatusEnum.APPROVE.getStatus();
        }
        if (!Objects.equals(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())
                && !Objects.equals(status, BpmProcessInstanceStatusEnum.REJECT.getStatus())) {
            return;
        }
        if (terminalUserId == null || StrUtil.isBlank(terminalTaskId) || StrUtil.isBlank(terminalTaskKey)) {
            log.error("Refusing completed BPM approval recovery without terminal task evidence tenant={} "
                            + "approval={} process={}",
                    binding.getTenantId(), binding.getApprovalId(), process.getId());
            return;
        }
        String reason = Objects.toString(variables.get(
                BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON), "");
        workflows.observe(new BpmProcessInstanceStatusEvent(this)
                .setId(process.getId()).setProcessDefinitionKey(process.getProcessDefinitionKey())
                .setBusinessKey(process.getBusinessKey()).setStatus(status).setReason(reason)
                .setTerminalOperatorUserId(terminalUserId).setTerminalTaskId(terminalTaskId)
                .setTerminalTaskDefinitionKey(terminalTaskKey));
        log.warn("Recovered completed BPM approval terminal evidence tenant={} approval={} process={} status={}",
                binding.getTenantId(), binding.getApprovalId(), process.getId(), status);
    }

    private HistoricProcessInstance findProcess(ApprovalWorkflowBinding binding) {
        if (StrUtil.isNotBlank(binding.getProcessInstanceId())) {
            return historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(binding.getProcessInstanceId()).includeProcessVariables().singleResult();
        }
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(binding.getBusinessKey()).includeProcessVariables().singleResult();
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
