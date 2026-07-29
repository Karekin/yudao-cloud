package cn.iocoder.yudao.module.cloudmold.agentcontrol.service.workflow;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Mission;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.MissionGoal;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.WorkOrder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionLifecycleWorkflowQueryServiceTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final MissionLifecycleWorkflowQueryService service = new MissionLifecycleWorkflowQueryService(mapper);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void assemblesThePersistedMissionGoalsAndWorkOrdersIntoAWorkflowView() {
        when(mapper.selectMission(17L, "mission-1")).thenReturn(new Mission()
                .setMissionId("mission-1")
                .setMissionType(MissionLifecycleWorkflowCommandService.MISSION_TEMPLATE)
                .setTemplateVersion("1.0.0")
                .setTitle("夏季连衣裙缺断码响应")
                .setObjectiveJson("{\"styleCode\":\"YS-1\"}")
                .setCorrelationId("stockout-YS-1")
                .setStatus("ACTIVE")
                .setSupervisorUserId(900L)
                .setVersion(1L)
                .setStartedAt(LocalDateTime.of(2026, 7, 28, 4, 0))
                .setDeadlineAt(LocalDateTime.of(2026, 8, 1, 0, 0))
                .setUpdatedAt(LocalDateTime.of(2026, 7, 28, 4, 0)));
        when(mapper.selectMissionGoals(17L, "mission-1")).thenReturn(List.of(
                new MissionGoal().setGoalId("goal-1").setGoalCode("diagnose").setTitle("缺断码诊断")
                        .setStatus("ACTIVE").setVersion(1L)
                        .setCreatedAt(LocalDateTime.of(2026, 7, 28, 4, 0))
                        .setUpdatedAt(LocalDateTime.of(2026, 7, 28, 4, 0)),
                new MissionGoal().setGoalId("goal-2").setGoalCode("prepare").setTitle("制定补货方案")
                        .setStatus("ACTIVE").setVersion(1L)
                        .setCreatedAt(LocalDateTime.of(2026, 7, 28, 4, 1))
                        .setUpdatedAt(LocalDateTime.of(2026, 7, 28, 4, 1))));
        when(mapper.selectMissionWorkOrders(17L, "mission-1")).thenReturn(List.of(
                new WorkOrder().setWorkOrderId("wo-1").setGoalId("goal-1").setTitle("缺断码诊断")
                        .setRoleCode("inventory-control").setActionCode("inventory.detect-size-stockout")
                        .setStatus("READY").setRiskLevel("R1").setExecutionRequired(false)
                        .setRequesterUserId(900L).setAssigneeUserId(101L).setVersion(1L)
                        .setCreatedAt(LocalDateTime.of(2026, 7, 28, 4, 0))
                        .setReadyAt(LocalDateTime.of(2026, 7, 28, 4, 0))
                        .setUpdatedAt(LocalDateTime.of(2026, 7, 28, 4, 0)),
                new WorkOrder().setWorkOrderId("wo-2").setGoalId("goal-2").setParentWorkOrderId("wo-1")
                        .setTitle("制定补货方案").setRoleCode("buyer").setActionCode("buyer.prepare-replenishment")
                        .setStatus("WAITING_DEPENDENCY").setRiskLevel("R1").setExecutionRequired(false)
                        .setRequesterUserId(900L).setAssigneeUserId(102L).setWaitingReasonCode("PREDECESSOR")
                        .setVersion(1L).setCreatedAt(LocalDateTime.of(2026, 7, 28, 4, 1))
                        .setUpdatedAt(LocalDateTime.of(2026, 7, 28, 4, 1))));

        var view = service.inspect("mission-1");

        assertThat(view.getWorkflowScope()).isEqualTo(MissionLifecycleWorkflowCommandService.WORKFLOW_SCOPE);
        assertThat(view.getMissionType()).isEqualTo(MissionLifecycleWorkflowCommandService.MISSION_TEMPLATE);
        assertThat(view.getGoals()).hasSize(2);
        assertThat(view.getWorkOrders()).hasSize(2);
        assertThat(view.getWorkOrders().get(0).getStatus()).isEqualTo("READY");
        assertThat(view.getWorkOrders().get(1).getWaitingReasonCode()).isEqualTo("PREDECESSOR");
    }

    @Test
    void rejectsNonStockoutMissionTemplates() {
        when(mapper.selectMission(17L, "mission-1")).thenReturn(new Mission()
                .setMissionId("mission-1")
                .setMissionType("mission.generic.v1"));

        assertThatThrownBy(() -> service.inspect("mission-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fixed stockout mission lifecycle template");
    }
}
