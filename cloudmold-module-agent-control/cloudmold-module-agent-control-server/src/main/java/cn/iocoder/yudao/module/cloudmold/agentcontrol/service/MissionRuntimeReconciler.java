package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.MissionTimer;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "cloudmold.agent-control.runtime", name = "enabled", havingValue = "true")
public class MissionRuntimeReconciler {
    private final AgentControlStoreMapper mapper;
    private final MissionRuntimeService runtime;
    private final Clock clock;

    @Autowired
    public MissionRuntimeReconciler(AgentControlStoreMapper mapper, MissionRuntimeService runtime) {
        this(mapper, runtime, Clock.systemUTC());
    }

    MissionRuntimeReconciler(AgentControlStoreMapper mapper, MissionRuntimeService runtime, Clock clock) {
        this.mapper = mapper; this.runtime = runtime; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${cloudmold.agent-control.runtime.reconcile-delay-ms:2000}")
    public void reconcile() {
        for (MissionTimer timer : mapper.selectDueMissionTimers(LocalDateTime.now(clock), 100)) {
            TenantUtils.execute(timer.getTenantId(), () -> {
                try { runtime.fireTimer(timer.getTimerId()); }
                catch (RuntimeException exception) { log.warn("Mission timer reconcile deferred tenant={} timer={}",
                        timer.getTenantId(), timer.getTimerId(), exception); }
            });
        }
        for (WorkOrder workOrder : mapper.selectCompletedDependencySources(100)) {
            TenantUtils.execute(workOrder.getTenantId(), () -> {
                try { runtime.resolveCompletedWorkOrder(workOrder.getWorkOrderId()); }
                catch (RuntimeException exception) { log.warn("Mission dependency reconcile deferred tenant={} work={}",
                        workOrder.getTenantId(), workOrder.getWorkOrderId(), exception); }
            });
        }
    }
}
