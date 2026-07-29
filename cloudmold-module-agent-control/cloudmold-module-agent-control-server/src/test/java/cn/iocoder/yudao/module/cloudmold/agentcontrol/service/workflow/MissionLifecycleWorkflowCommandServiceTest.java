package cn.iocoder.yudao.module.cloudmold.agentcontrol.service.workflow;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.AgentControlResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.MissionRuntimeApi;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.StockoutMissionCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommand;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.api.workflow.MissionLifecycleWorkflowCommandResult;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.dataobject.AgentControlRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.agentcontrol.dal.mysql.AgentControlStoreMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissionLifecycleWorkflowCommandServiceTest {

    private final AgentControlStoreMapper mapper = mock(AgentControlStoreMapper.class);
    private final MissionRuntimeApi runtimeApi = mock(MissionRuntimeApi.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<MissionRuntimeApi> runtimeApiProvider = mock(ObjectProvider.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-28T04:00:00Z"), ZoneOffset.UTC);
    private final MissionLifecycleWorkflowCommandService service =
            new MissionLifecycleWorkflowCommandService(mapper, runtimeApiProvider, clock);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void startsTheFixedStockoutMissionAndPersistsAnIdempotentReplayEnvelope() {
        AtomicReference<String> attemptToken = new AtomicReference<>();
        when(runtimeApiProvider.getIfAvailable()).thenReturn(runtimeApi);
        doAnswer(invocation -> {
            attemptToken.set(invocation.getArgument(4, String.class));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(17L), eq("skill-step-1"),
                eq("MISSION_LIFECYCLE_START_STOCKOUT"), anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(41L);
        when(mapper.selectOperationForUpdate(41L, 17L)).thenAnswer(invocation ->
                new Operation().setTenantId(17L).setAttemptToken(attemptToken.get()).setStatus(0));
        when(runtimeApi.startStockoutMission(any(StockoutMissionCommand.class), eq(900L))).thenReturn(
                AgentControlResult.builder()
                        .aggregateType("business_mission")
                        .aggregateId("mission-1")
                        .aggregateVersion(1L)
                        .status("ACTIVE")
                        .build());
        when(mapper.markOperationSucceeded(eq(41L), eq(17L), eq("business_mission"), eq("mission-1"),
                anyString(), any())).thenReturn(1);

        service.startStockoutMission(MissionLifecycleWorkflowCommand.builder()
                .idempotencyKey("skill-step-1")
                .missionId("mission-1")
                .title("夏季连衣裙缺断码响应")
                .objectiveJson("{\"styleCode\":\"YS-1\"}")
                .correlationId("stockout-YS-1")
                .supervisorUserId(900L)
                .inventoryAgentUserId(101L)
                .buyerAgentUserId(102L)
                .customerServiceAgentUserId(103L)
                .deadlineAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build());

        verify(mapper).insertOrResolveOperation(eq(17L), eq("skill-step-1"),
                eq("MISSION_LIFECYCLE_START_STOCKOUT"), anyString(), anyString(), any());
        ArgumentCaptor<StockoutMissionCommand> commandCaptor = ArgumentCaptor.forClass(StockoutMissionCommand.class);
        verify(runtimeApi).startStockoutMission(commandCaptor.capture(), eq(900L));
        assertThat(commandCaptor.getValue().getMissionId()).isEqualTo("mission-1");
        assertThat(commandCaptor.getValue().getBuyerAgentUserId()).isEqualTo(102L);
        assertThat(commandCaptor.getValue().getCustomerServiceAgentUserId()).isEqualTo(103L);
        verify(mapper).markOperationSucceeded(eq(41L), eq(17L), eq("business_mission"), eq("mission-1"),
                anyString(), any());
    }

    @Test
    void replaysThePreviousMissionIdWhenTheSameIdempotencyKeyIsSeenAgain() {
        when(runtimeApiProvider.getIfAvailable()).thenReturn(runtimeApi);
        when(mapper.selectLastInsertId()).thenReturn(42L);
        when(mapper.selectOperationForUpdate(42L, 17L)).thenReturn(
                new Operation().setTenantId(17L)
                        .setAttemptToken("older-attempt")
                        .setRequestHash("5db3f07f4f90f7b0199bafcea6fd65f967f1e0ef32edb3e5d992051827f7a831")
                        .setStatus(10)
                        .setResultJson(JsonUtils.toJsonString(MissionLifecycleWorkflowCommandResult.builder()
                                .workflowScope(MissionLifecycleWorkflowCommandService.WORKFLOW_SCOPE)
                                .missionId("mission-existing")
                                .missionType(MissionLifecycleWorkflowCommandService.MISSION_TEMPLATE)
                                .aggregateVersion(1L)
                                .status("ACTIVE")
                                .duplicate(false)
                                .build())));

        MissionLifecycleWorkflowCommand command = MissionLifecycleWorkflowCommand.builder()
                .idempotencyKey("skill-step-1")
                .title("夏季连衣裙缺断码响应")
                .objectiveJson("{\"styleCode\":\"YS-1\"}")
                .correlationId("stockout-YS-1")
                .supervisorUserId(900L)
                .inventoryAgentUserId(101L)
                .buyerAgentUserId(102L)
                .customerServiceAgentUserId(103L)
                .build();
        String requestHash = cn.hutool.crypto.digest.DigestUtil.sha256Hex(JsonUtils.toJsonString(java.util.Map.of(
                "command", command,
                "workflow_scope", MissionLifecycleWorkflowCommandService.WORKFLOW_SCOPE)));
        when(mapper.selectOperationForUpdate(42L, 17L)).thenReturn(
                new Operation().setTenantId(17L)
                        .setAttemptToken("older-attempt")
                        .setRequestHash(requestHash)
                        .setStatus(10)
                        .setResultJson(JsonUtils.toJsonString(MissionLifecycleWorkflowCommandResult.builder()
                                .workflowScope(MissionLifecycleWorkflowCommandService.WORKFLOW_SCOPE)
                                .missionId("mission-existing")
                                .missionType(MissionLifecycleWorkflowCommandService.MISSION_TEMPLATE)
                                .aggregateVersion(1L)
                                .status("ACTIVE")
                                .duplicate(false)
                                .build())));

        MissionLifecycleWorkflowCommandResult replay = service.startStockoutMission(command);

        assertThat(replay.isDuplicate()).isTrue();
        assertThat(replay.getMissionId()).isEqualTo("mission-existing");
        verify(runtimeApi, never()).startStockoutMission(any(), any());
        verify(mapper, never()).markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
    }
}
