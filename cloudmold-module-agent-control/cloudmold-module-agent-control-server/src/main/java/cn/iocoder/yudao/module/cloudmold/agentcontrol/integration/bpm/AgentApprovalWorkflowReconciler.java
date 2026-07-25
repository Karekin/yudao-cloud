package cn.iocoder.yudao.module.cloudmold.agentcontrol.integration.bpm;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.ApprovalWorkflowStartCandidate;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.agent-control.approval-workflow",
        name = "enabled", havingValue = "true")
public class AgentApprovalWorkflowReconciler implements ApplicationListener<ApplicationReadyEvent> {

    private final AgentControlStoreMapper mapper;
    private final AgentApprovalWorkflowService workflows;
    private final AgentApprovalWorkflowProperties properties;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reconcile();
    }

    @Scheduled(fixedDelayString =
            "${cloudmold.agent-control.approval-workflow.reconcile-delay-ms:2000}")
    public synchronized void reconcile() {
        int batchSize = Math.max(1, Math.min(properties.getBatchSize(), 100));
        for (ApprovalWorkflowStartCandidate candidate : mapper.selectApprovalWorkflowStartCandidates(batchSize)) {
            TenantUtils.execute(candidate.getTenantId(), () -> start(candidate));
        }
    }

    private void start(ApprovalWorkflowStartCandidate candidate) {
        try {
            workflows.start(candidate);
        } catch (RuntimeException exception) {
            log.warn("BPM approval start is uncertain and requires reconciliation tenant={} approval={}",
                    candidate.getTenantId(), candidate.getApprovalId(), exception);
        }
    }

}
