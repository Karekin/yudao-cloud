package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlCommandApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlOperation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentApprovalContinuationApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowDecisionCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class AgentApprovalWorkflowTerminalDecisionReconciler implements ApplicationListener<ApplicationReadyEvent> {

    private final AgentControlStoreMapper mapper;
    private final AgentControlCommandApi commands;
    private final AgentApprovalWorkflowProperties properties;
    private final List<AgentApprovalContinuationApi> continuations;

    @Autowired
    public AgentApprovalWorkflowTerminalDecisionReconciler(AgentControlStoreMapper mapper,
                                                           @Lazy AgentControlCommandApi commands,
                                                           AgentApprovalWorkflowProperties properties,
                                                           List<AgentApprovalContinuationApi> continuations) {
        this.mapper = mapper;
        this.commands = commands;
        this.properties = properties;
        this.continuations = continuations;
    }

    AgentApprovalWorkflowTerminalDecisionReconciler(AgentControlStoreMapper mapper,
                                                     AgentControlCommandApi commands,
                                                     AgentApprovalWorkflowProperties properties) {
        this(mapper, commands, properties, List.of());
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reconcile();
    }

    @Scheduled(fixedDelayString =
            "${cloudmold.agent-control.approval-workflow.reconcile-delay-ms:2000}")
    public synchronized void reconcile() {
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), 100));
        for (ApprovalWorkflowDecisionCandidate candidate
                : mapper.selectApprovalWorkflowDecisionCandidates(batchSize)) {
            TenantUtils.execute(candidate.getTenantId(), () -> decide(candidate));
        }
    }

    private void decide(ApprovalWorkflowDecisionCandidate candidate) {
        String decision = decision(candidate);
        if (decision == null || candidate.getApproverUserId() == null
                || candidate.getApproverUserId() <= 0
                || candidate.getTerminalOperatorUserId() == null
                || candidate.getTerminalOperatorUserId() <= 0
                || candidate.getApprovalVersion() == null || candidate.getApprovalVersion() <= 0
                || candidate.getWorkOrderVersion() == null || candidate.getWorkOrderVersion() <= 0) {
            log.warn("Deferring BPM terminal decision candidate with incomplete attestation tenant={} approval={}",
                    candidate.getTenantId(), candidate.getApprovalId());
            return;
        }
        AgentControlCommand command = AgentControlCommand.builder()
                .operation(AgentControlOperation.DECIDE_APPROVAL)
                .idempotencyKey(idempotencyKey(candidate, decision))
                .approval(AgentControlCommand.ApprovalDefinition.builder()
                        .approvalId(candidate.getApprovalId())
                        .decision(decision)
                        .reasonCode("BPM_" + decision + "_ATTESTED")
                        .approvalExpectedVersion(candidate.getApprovalVersion())
                        .workOrderExpectedVersion(candidate.getWorkOrderVersion())
                        .build())
                .build();
        try {
            commands.execute(command, candidate.getApproverUserId());
            for (AgentApprovalContinuationApi continuation : continuations) {
                continuation.onApprovalDecision(candidate.getTenantId(), candidate.getWorkOrderId(),
                        candidate.getApprovalId(), decision);
            }
        } catch (RuntimeException exception) {
            log.warn("BPM terminal decision candidate remains pending tenant={} approval={} decision={}",
                    candidate.getTenantId(), candidate.getApprovalId(), decision, exception);
        }
    }

    private String idempotencyKey(ApprovalWorkflowDecisionCandidate candidate, String decision) {
        return "bpm-terminal-decision:" + candidate.getTenantId() + ":" + candidate.getApprovalId()
                + ":" + decision + ":a" + candidate.getApprovalVersion()
                + ":w" + candidate.getWorkOrderVersion();
    }

    private static String decision(ApprovalWorkflowDecisionCandidate candidate) {
        return switch (candidate.getObservedStatus()) {
            case "BPM_APPROVED_PENDING_ATTESTATION" -> "APPROVE";
            case "BPM_REJECTED" -> "REJECT";
            default -> null;
        };
    }
}
