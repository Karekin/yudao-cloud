package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * 把补货业务事件桥接到对应 Temporal 运行的 businessEvent signal。
 * <p>
 * 按 businessReferenceId（补货场景为 recommendationId）反查处于 WAITING_EVENT 的 run_binding，
 * 向其 workflow 发送 businessEvent signal。businessEvent signal 已在 BusinessEventWaitChildWorkflow 就绪，
 * child 内部 lastAcceptedEventFingerprint 去重保证幂等。
 * <p>
 * 设计完全克隆 {@link TemporalApprovalContinuationAdapter}：用 binding 落库关联 + CAS 标记已发送，
 * 配合 {@link BusinessEventContinuationReconciler} 修复 signal 在 Temporal 宕机窗口的丢失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class TemporalBusinessEventContinuationAdapter {

    private final WorkflowClient workflowClient;
    private final AiOperationsTemporalMapper mapper;

    public void onBusinessEvent(Long tenantId, String businessReferenceId, String eventCode, String eventRef) {
        if (businessReferenceId == null || businessReferenceId.isBlank()) {
            return;
        }
        List<TemporalRunBindingRecord> bindings =
                mapper.selectWaitingEventBindingsByBusinessRef(tenantId, businessReferenceId);
        if (bindings == null || bindings.isEmpty()) {
            // 没有等待该业务事件的运行：事件可能先于 run 进入等待，忽略即可
            return;
        }
        for (TemporalRunBindingRecord binding : bindings) {
            try {
                workflowClient.newUntypedWorkflowStub(binding.getTemporalWorkflowId(),
                                Optional.of(binding.getTemporalRunId()), Optional.empty())
                        .signal("businessEvent", eventCode, eventRef);
                // CAS 标记已发送，避免 projector/reconciler 重复 signal；child 侧亦有 fingerprint 去重双保险
                mapper.markBusinessEventSignaled(tenantId, binding.getTemporalRunId(),
                        LocalDateTime.now(ZoneOffset.UTC));
            } catch (RuntimeException exception) {
                log.warn("Temporal business event signal 留待重试 tenant={} businessRef={} run={} code={}",
                        tenantId, businessReferenceId, binding.getTemporalRunId(), eventCode, exception);
            }
        }
    }
}
