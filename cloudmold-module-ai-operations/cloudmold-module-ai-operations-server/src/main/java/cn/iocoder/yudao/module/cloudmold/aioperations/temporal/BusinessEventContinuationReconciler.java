package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentBusinessStageView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 兜底修复“业务事件已发布但 businessEvent signal 在 Temporal 宕机/重启窗口丢失”。
 * <p>
 * 扫描 WAITING_EVENT 的 run_binding，重查补货业务阶段；若 SoR 已推进到终态（PO 关闭/上架完成/任一取消），
 * 主动 signal 一次 BUSINESS_STAGE_REFRESH，触发 child 重查并落 SUCCEEDED。
 * 中间阶段推进由 {@code BusinessEventOutboxProjector} 的事件驱动 + child timeout 兜底覆盖。
 * <p>
 * 设计克隆 {@link TemporalApprovalContinuationReconciler}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal", name = "enabled", havingValue = "true")
public class BusinessEventContinuationReconciler implements ApplicationListener<ApplicationReadyEvent> {

    private static final int BATCH_SIZE = 100;

    private final AiOperationsTemporalMapper mapper;
    private final SupplyPlanningQueryApi supplyPlanningQueries;
    private final TemporalBusinessEventContinuationAdapter continuation;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reconcile();
    }

    @Scheduled(fixedDelayString =
            "${cloudmold.ai-operations.temporal.business-event-reconcile-delay-ms:5000}")
    public void reconcile() {
        for (TemporalRunBindingRecord binding : mapper.selectWaitingEventBindings(BATCH_SIZE)) {
            TenantUtils.execute(binding.getTenantId(), () -> reconcileInTenant(binding));
        }
    }

    private void reconcileInTenant(TemporalRunBindingRecord binding) {
        String recommendationId = binding.getBusinessReferenceId();
        if (recommendationId == null || recommendationId.isBlank()) {
            return;
        }
        try {
            ReplenishmentBusinessStageView stage =
                    supplyPlanningQueries.requireReplenishmentBusinessStage(recommendationId);
            if (isTerminalStage(stage)) {
                continuation.onBusinessEvent(binding.getTenantId(), recommendationId,
                        "BUSINESS_STAGE_REFRESH", recommendationId);
            }
        } catch (RuntimeException exception) {
            log.warn("Temporal business event continuation remains retryable tenant={} businessRef={} run={}",
                    binding.getTenantId(), recommendationId, binding.getTemporalRunId(), exception);
        }
    }

    /**
     * 终态判断保持宽松：reconciler 只负责“别漏掉终态”，多 signal 会被 child fingerprint 去重，
     * 权威判定由 child refresh 后的 evaluateBusinessStage 完成。
     */
    private static boolean isTerminalStage(ReplenishmentBusinessStageView stage) {
        if (stage == null) {
            return false;
        }
        String purchaseOrder = stage.getProcurementOrderStatus();
        String receipt = stage.getReceiptStatus();
        String putaway = stage.getPutawayStatus();
        String stockTransfer = stage.getStockTransferStatus();
        if ("CANCELLED".equals(purchaseOrder) || "CANCELLED".equals(receipt)
                || "CANCELLED".equals(putaway) || "CANCELED".equals(stockTransfer)) {
            return true;
        }
        return "CLOSED".equals(purchaseOrder) || "COMPLETED".equals(putaway)
                || "COMPLETED".equals(stockTransfer);
    }
}
