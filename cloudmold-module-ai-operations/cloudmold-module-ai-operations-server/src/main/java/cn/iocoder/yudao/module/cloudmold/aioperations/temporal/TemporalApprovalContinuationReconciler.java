package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlQueryApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.temporal.client.WorkflowNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Repairs the narrow failure window after Agent Control persisted a terminal BPM decision but before
 * the Temporal signal was acknowledged. The binding stays WAITING_APPROVAL until signal delivery
 * succeeds, so application restarts and transient Temporal outages are recoverable without bypassing BPM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalApprovalContinuationReconciler implements ApplicationListener<ApplicationReadyEvent> {

    private static final int BATCH_SIZE = 100;

    private final AiOperationsTemporalMapper mapper;
    private final AgentControlQueryApi agentQueries;
    private final TemporalApprovalContinuationAdapter continuation;
    private final TemporalApprovedTimeoutRecoveryService timeoutRecovery;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reconcile();
    }

    @Scheduled(fixedDelayString =
            "${cloudmold.ai-operations.temporal.approval-reconcile-delay-ms:2000}")
    public void reconcile() {
        for (TemporalRunBindingRecord binding : mapper.selectWaitingApprovalBindings(BATCH_SIZE)) {
            TenantUtils.execute(binding.getTenantId(), () -> reconcileInTenant(binding));
        }
    }

    private void reconcileInTenant(TemporalRunBindingRecord binding) {
        try {
            AgentControlResult approval = agentQueries.getApproval(binding.getApprovalId());
            String decision = switch (approval.getStatus()) {
                case "APPROVED" -> "APPROVE";
                case "REJECTED" -> "REJECT";
                default -> null;
            };
            if (decision != null) {
                continuation.onApprovalDecision(binding.getTenantId(), binding.getWorkOrderId(),
                        binding.getApprovalId(), decision);
            }
        } catch (WorkflowNotFoundException exception) {
            timeoutRecovery.recover(binding);
        } catch (RuntimeException exception) {
            log.warn("Temporal approval continuation remains retryable tenant={} approval={} run={}",
                    binding.getTenantId(), binding.getApprovalId(), binding.getTemporalRunId(), exception);
        }
    }
}
