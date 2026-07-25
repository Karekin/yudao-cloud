package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.MissionTimer;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

class MissionRuntimeReconcilerTest {
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final MissionRuntimeService runtime = mock(MissionRuntimeService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-25T08:00:00Z"), ZoneOffset.UTC);
    private final MissionRuntimeReconciler reconciler = new MissionRuntimeReconciler(mapper, runtime, clock);

    @Test
    void reconcileRecoversExpiredRunsBeforeTimersAndDependencyResolution() {
        WorkOrder expired = new WorkOrder().setTenantId(17L).setWorkOrderId("wo-expired");
        MissionTimer timer = new MissionTimer().setTenantId(17L).setTimerId("timer-1");
        WorkOrder completed = new WorkOrder().setTenantId(17L).setWorkOrderId("wo-completed");
        when(mapper.selectExpiredRunLeaseWorkOrders(any(), eq(100))).thenReturn(List.of(expired));
        when(mapper.selectDueMissionTimers(any(), eq(100))).thenReturn(List.of(timer));
        when(mapper.selectCompletedDependencySources(100)).thenReturn(List.of(completed));
        when(runtime.recoverExpiredRun("wo-expired"))
                .thenReturn(AgentControlResult.builder().aggregateType("role_work_order")
                        .aggregateId("wo-expired").aggregateVersion(1L).status("RUN_EXPIRED").build());
        when(runtime.fireTimer("timer-1"))
                .thenReturn(AgentControlResult.builder().aggregateType("mission_timer")
                        .aggregateId("timer-1").aggregateVersion(1L).status("FIRED").build());
        when(runtime.resolveCompletedWorkOrder("wo-completed"))
                .thenReturn(AgentControlResult.builder().aggregateType("role_work_order")
                        .aggregateId("wo-completed").aggregateVersion(1L).status("ADVANCED").build());

        reconciler.reconcile();

        InOrder order = inOrder(runtime);
        order.verify(runtime).recoverExpiredRun("wo-expired");
        order.verify(runtime).fireTimer("timer-1");
        order.verify(runtime).resolveCompletedWorkOrder("wo-completed");
    }
}
