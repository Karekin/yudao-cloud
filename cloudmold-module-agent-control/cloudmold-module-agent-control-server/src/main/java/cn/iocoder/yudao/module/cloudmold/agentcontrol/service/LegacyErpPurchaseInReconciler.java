package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.EventSubscription;
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
@ConditionalOnProperty(prefix = "cloudmold.agent-control.runtime", name = "legacy-erp-bridge-enabled",
        havingValue = "true")
public class LegacyErpPurchaseInReconciler implements ApplicationListener<ApplicationReadyEvent> {

    private final AgentControlStoreMapper mapper;
    private final LegacyErpPurchaseInEventBridge bridge;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        reconcile();
    }

    @Scheduled(fixedDelayString = "${cloudmold.agent-control.runtime.legacy-erp-bridge-delay-ms:5000}")
    public synchronized void reconcile() {
        for (EventSubscription subscription : mapper.selectActiveLegacyPurchaseInSubscriptions(100)) {
            TenantUtils.execute(subscription.getTenantId(), () -> {
                try {
                    bridge.capture(Long.valueOf(subscription.getAggregateId()), subscription.getCorrelationId());
                } catch (RuntimeException exception) {
                    log.warn("Legacy ERP purchase-in bridge deferred tenant={} subscription={}",
                            subscription.getTenantId(), subscription.getSubscriptionId(), exception);
                }
            });
        }
    }
}
