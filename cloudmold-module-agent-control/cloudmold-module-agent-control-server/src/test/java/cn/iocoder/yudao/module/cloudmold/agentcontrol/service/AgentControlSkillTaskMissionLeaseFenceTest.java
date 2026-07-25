package cn.iocoder.yudao.module.cloudmold.agentcontrol.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.AgentRunLease;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.approval.SkillTaskMissionLeaseFencePort.MissionLeaseFence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentControlSkillTaskMissionLeaseFenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 25, 12, 0);
    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final AgentControlSkillTaskMissionLeaseFence service =
            new AgentControlSkillTaskMissionLeaseFence(mapper,
                    Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void acceptsNonMissionPermitWithoutInventingLeaseEvidence() {
        TenantContextHolder.setTenantId(8L);
        when(mapper.selectWorkOrderForUpdate(8L, "wo-1"))
                .thenReturn(new WorkOrder().setTenantId(8L).setWorkOrderId("wo-1").setStatus("READY"));

        service.validateCurrentLease(new MissionLeaseFence(8L, "wo-1", null, null, 0L, 0L));

        verify(mapper, never()).selectRunLeaseForUpdate(8L, "wo-1");
    }

    @Test
    void acceptsOnlyTheExactCurrentMissionLeaseFence() {
        TenantContextHolder.setTenantId(8L);
        WorkOrder workOrder = new WorkOrder().setTenantId(8L).setWorkOrderId("wo-1")
                .setMissionId("mission-1").setStatus("IN_PROGRESS").setActiveRunId("run-2");
        AgentRunLease lease = lease("run-2", "worker-2", 3L, 4L);
        when(mapper.selectWorkOrderForUpdate(8L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectRunLeaseForUpdate(8L, "wo-1")).thenReturn(lease);

        service.validateCurrentLease(new MissionLeaseFence(8L, "wo-1", "run-2", "worker-2", 4L, 3L));

        verify(mapper).selectRunLeaseForUpdate(8L, "wo-1");
    }

    @Test
    void rejectsOldWorkerFenceAfterTakeoverEvenForTheSameOperator() {
        TenantContextHolder.setTenantId(8L);
        WorkOrder workOrder = new WorkOrder().setTenantId(8L).setWorkOrderId("wo-1")
                .setMissionId("mission-1").setStatus("IN_PROGRESS").setActiveRunId("run-2");
        when(mapper.selectWorkOrderForUpdate(8L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectRunLeaseForUpdate(8L, "wo-1"))
                .thenReturn(lease("run-2", "worker-2", 3L, 4L));

        assertThatThrownBy(() -> service.validateCurrentLease(
                new MissionLeaseFence(8L, "wo-1", "run-1", "worker-1", 3L, 2L)))
                .isInstanceOf(SecurityException.class)
                .hasMessage("mission lease fence is stale");
    }

    @Test
    void serializesActuatorExecutionBehindTheCurrentFenceLocks() {
        TenantContextHolder.setTenantId(8L);
        WorkOrder workOrder = new WorkOrder().setTenantId(8L).setWorkOrderId("wo-1")
                .setMissionId("mission-1").setStatus("IN_PROGRESS").setActiveRunId("run-2");
        when(mapper.selectWorkOrderForUpdate(8L, "wo-1")).thenReturn(workOrder);
        when(mapper.selectRunLeaseForUpdate(8L, "wo-1"))
                .thenReturn(lease("run-2", "worker-2", 3L, 4L));
        MissionLeaseFence fence = new MissionLeaseFence(8L, "wo-1", "run-2", "worker-2", 4L, 3L);

        String result = service.executeWhileCurrent(fence, () -> {
            verify(mapper).selectWorkOrderForUpdate(8L, "wo-1");
            verify(mapper).selectRunLeaseForUpdate(8L, "wo-1");
            return "executed";
        });

        assertThat(result).isEqualTo("executed");
    }

    private static AgentRunLease lease(String runId, String owner, long fence, long epoch) {
        return new AgentRunLease().setTenantId(8L).setWorkOrderId("wo-1").setMissionId("mission-1")
                .setRunId(runId).setActorUserId(42L).setLeaseOwner(owner).setFencingToken(fence)
                .setVersion(epoch).setStatus("ACTIVE").setLeaseUntil(NOW.plusMinutes(1));
    }
}
