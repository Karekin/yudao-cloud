package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import io.grpc.Status;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleDescription;
import io.temporal.client.schedules.ScheduleHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsTemporalScheduleServiceTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreateCalendarScheduleWithBoundedCatchupAndDispatcherWorkflow() {
        ScheduleClient client = mock(ScheduleClient.class);
        ScheduleHandle missing = mock(ScheduleHandle.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AiOperationsTemporalProperties temporal = new AiOperationsTemporalProperties();
        AiOperationsTemporalSeedProperties seed = new AiOperationsTemporalSeedProperties();
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        AiOperationsTemporalScheduleService service =
                new AiOperationsTemporalScheduleService(client, mapper, temporal, workflows);
        ManagedSkillTaskWorkflowView workflow = ManagedSkillTaskWorkflowView.builder()
                .skillId("skill.cloudmold.operations.daily-business-control.v1")
                .skillVersion("1.0.0")
                .displayName("日经营控制")
                .definitionClosureSha256("a".repeat(64))
                .approvalRequired(false)
                .build();
        TenantContextHolder.setTenantId(162L);
        when(client.getHandle(any())).thenReturn(missing);
        when(missing.describe()).thenThrow(Status.NOT_FOUND.asRuntimeException());
        when(client.createSchedule(any(), any(), any())).thenReturn(missing);
        when(mapper.insertSchedule(any())).thenReturn(1);
        when(mapper.markScheduleReconciled(any(), any(), any(), any(), any())).thenReturn(1);

        boolean created = service.reconcileManagedDaily(workflow, seed);

        assertThat(created).isTrue();
        ArgumentCaptor<Schedule> schedule = ArgumentCaptor.forClass(Schedule.class);
        verify(client).createSchedule(
                eq("cloudmold-t162-managed-daily-operations-daily-business-control"),
                schedule.capture(), any());
        assertThat(schedule.getValue().getSpec().getIntervals()).isNullOrEmpty();
        assertThat(schedule.getValue().getSpec().getCronExpressions()).hasSize(1);
        assertThat(schedule.getValue().getSpec().getTimeZoneName()).isEqualTo("Asia/Shanghai");
        assertThat(schedule.getValue().getPolicy().getOverlap())
                .isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
        assertThat(schedule.getValue().getPolicy().getCatchupWindow())
                .isEqualTo(Duration.ofHours(1));
        assertThat(schedule.getValue().getPolicy().isPauseOnFailure()).isFalse();
        assertThat(schedule.getValue().getAction())
                .isInstanceOf(ScheduleActionStartWorkflow.class);
        assertThat(((ScheduleActionStartWorkflow) schedule.getValue().getAction()).getWorkflowType())
                .isEqualTo("TemporalManagedDailyDispatchWorkflow");
        assertThat(((ScheduleActionStartWorkflow) schedule.getValue().getAction())
                .getOptions().getWorkflowIdReusePolicy())
                .isNull();
        ArgumentCaptor<TemporalScheduleRecord> record =
                ArgumentCaptor.forClass(TemporalScheduleRecord.class);
        verify(mapper).insertSchedule(record.capture());
        assertThat(record.getValue().getScheduleId())
                .isEqualTo("cloudmold-t162-managed-daily-operations-daily-business-control");
        assertThat(record.getValue().getSkillId()).isEqualTo(workflow.getSkillId());
        assertThat(record.getValue().getSkillVersion()).isEqualTo(workflow.getSkillVersion());
        assertThat(record.getValue().getCronExpression())
                .isEqualTo(schedule.getValue().getSpec().getCronExpressions().get(0));
        assertThat(record.getValue().getTimeZone())
                .isEqualTo(schedule.getValue().getSpec().getTimeZoneName());
        assertThat(record.getValue().getOverlapPolicy()).isEqualTo("SKIP");
        assertThat(record.getValue().getDesiredPolicySha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldKeepRegistryTemporalAndDatabaseScheduleSetsIdentical() {
        ScheduleClient client = mock(ScheduleClient.class);
        ScheduleHandle missing = mock(ScheduleHandle.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AiOperationsTemporalProperties temporal = new AiOperationsTemporalProperties();
        AiOperationsTemporalSeedProperties seed = new AiOperationsTemporalSeedProperties();
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        AiOperationsTemporalScheduleService service =
                new AiOperationsTemporalScheduleService(client, mapper, temporal, workflows);
        TenantContextHolder.setTenantId(162L);
        when(client.getHandle(any())).thenReturn(missing);
        when(missing.describe()).thenThrow(Status.NOT_FOUND.asRuntimeException());
        when(client.createSchedule(any(), any(), any())).thenReturn(missing);
        when(mapper.insertSchedule(any())).thenReturn(1);
        when(mapper.markScheduleReconciled(any(), any(), any(), any(), any())).thenReturn(1);

        List<ManagedSkillTaskWorkflowView> registrySnapshot =
                ManagedWorkflowDailyAutomationFixtures.workflows();
        registrySnapshot.forEach(workflow ->
                service.reconcileManagedDaily(workflow, seed));

        ArgumentCaptor<String> temporalIds = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Schedule> temporalSchedules = ArgumentCaptor.forClass(Schedule.class);
        ArgumentCaptor<TemporalScheduleRecord> databaseRecords =
                ArgumentCaptor.forClass(TemporalScheduleRecord.class);
        verify(client, times(registrySnapshot.size())).createSchedule(
                temporalIds.capture(), temporalSchedules.capture(), any());
        verify(mapper, times(registrySnapshot.size())).insertSchedule(databaseRecords.capture());

        Set<String> registrySet = registrySnapshot.stream()
                .map(workflow -> workflow.getSkillId() + "@" + workflow.getSkillVersion())
                .collect(Collectors.toSet());
        Set<String> databaseSet = databaseRecords.getAllValues().stream()
                .map(record -> record.getSkillId() + "@" + record.getSkillVersion())
                .collect(Collectors.toSet());
        Set<String> databaseScheduleIds = databaseRecords.getAllValues().stream()
                .map(TemporalScheduleRecord::getScheduleId)
                .collect(Collectors.toSet());
        assertThat(registrySet).hasSize(registrySnapshot.size());
        assertThat(databaseSet).isEqualTo(registrySet);
        assertThat(temporalIds.getAllValues())
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(databaseScheduleIds);
        assertThat(temporalSchedules.getAllValues()).allSatisfy(schedule -> {
            assertThat(schedule.getSpec().getIntervals()).isNullOrEmpty();
            assertThat(schedule.getSpec().getCronExpressions()).singleElement();
            assertThat(schedule.getSpec().getTimeZoneName()).isEqualTo("Asia/Shanghai");
            assertThat(schedule.getPolicy().getOverlap())
                    .isEqualTo(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP);
            assertThat(schedule.getPolicy().getCatchupWindow()).isEqualTo(Duration.ofHours(1));
            assertThat(schedule.getAction()).isInstanceOf(ScheduleActionStartWorkflow.class);
            assertThat(((ScheduleActionStartWorkflow) schedule.getAction()).getWorkflowType())
                    .isEqualTo("TemporalManagedDailyDispatchWorkflow");
        });
    }

    @Test
    void shouldCreateOnlyOnceAndReconcileExistingScheduleOnSubsequentRuns() {
        ScheduleClient client = mock(ScheduleClient.class);
        ScheduleHandle handle = mock(ScheduleHandle.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AiOperationsTemporalProperties temporal = new AiOperationsTemporalProperties();
        AiOperationsTemporalSeedProperties seed = new AiOperationsTemporalSeedProperties();
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        AiOperationsTemporalScheduleService service =
                new AiOperationsTemporalScheduleService(client, mapper, temporal, workflows);
        ManagedSkillTaskWorkflowView workflow =
                ManagedWorkflowDailyAutomationFixtures.workflow(
                        "skill.cloudmold.operations.daily-business-control.v1");
        TemporalScheduleRecord existing = new TemporalScheduleRecord()
                .setTenantId(162L)
                .setScheduleId("cloudmold-t162-managed-daily-operations-daily-business-control")
                .setSkillId(workflow.getSkillId())
                .setSkillVersion(workflow.getSkillVersion())
                .setVersion(1L);
        TenantContextHolder.setTenantId(162L);
        when(client.getHandle(any())).thenReturn(handle);
        when(handle.describe())
                .thenThrow(Status.NOT_FOUND.asRuntimeException())
                .thenReturn(mock(ScheduleDescription.class));
        when(client.createSchedule(any(), any(), any())).thenReturn(handle);
        when(mapper.selectSchedule(any(), any())).thenReturn(null, existing);
        when(mapper.insertSchedule(any())).thenReturn(1);
        when(mapper.markScheduleReconciled(any(), any(), any(), any(), any())).thenReturn(1);
        when(mapper.updateScheduleDefinition(any())).thenReturn(1);

        boolean firstCreated = service.reconcileManagedDaily(workflow, seed);
        boolean secondCreated = service.reconcileManagedDaily(workflow, seed);

        assertThat(firstCreated).isTrue();
        assertThat(secondCreated).isFalse();
        verify(client, times(1)).createSchedule(any(), any(), any());
        verify(mapper, times(1)).insertSchedule(any());
        verify(mapper, times(1)).updateScheduleDefinition(any());
        verify(handle, times(1)).update(any());
    }

    @Test
    void shouldRecoverTypedAlreadyExistsRaceByUpdatingCanonicalSchedule() {
        ScheduleClient client = mock(ScheduleClient.class);
        ScheduleHandle handle = mock(ScheduleHandle.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AiOperationsTemporalProperties temporal = new AiOperationsTemporalProperties();
        AiOperationsTemporalSeedProperties seed = new AiOperationsTemporalSeedProperties();
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        AiOperationsTemporalScheduleService service =
                new AiOperationsTemporalScheduleService(client, mapper, temporal, workflows);
        ManagedSkillTaskWorkflowView workflow =
                ManagedWorkflowDailyAutomationFixtures.workflow(
                        "skill.cloudmold.operations.daily-business-control.v1");
        TenantContextHolder.setTenantId(162L);
        when(client.getHandle(any())).thenReturn(handle);
        when(handle.describe()).thenThrow(Status.NOT_FOUND.asRuntimeException());
        when(client.createSchedule(any(), any(), any()))
                .thenThrow(Status.ALREADY_EXISTS.asRuntimeException());
        when(mapper.insertSchedule(any())).thenReturn(1);
        when(mapper.markScheduleReconciled(any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(service.reconcileManagedDaily(workflow, seed)).isTrue();

        verify(handle).update(any());
        verify(mapper).insertSchedule(any());
        verify(mapper, never()).updateScheduleDefinition(any());
    }

    @Test
    void shouldPauseOnlyObsoleteManagedDailySchedules() {
        ScheduleClient client = mock(ScheduleClient.class);
        ScheduleHandle obsoleteHandle = mock(ScheduleHandle.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AiOperationsTemporalScheduleService service = new AiOperationsTemporalScheduleService(
                client, mapper, new AiOperationsTemporalProperties(),
                mock(AiOperationsManagedRunQueryServiceFacade.class));
        TemporalScheduleRecord obsolete = new TemporalScheduleRecord()
                .setTenantId(162L).setScheduleId("cloudmold-t162-managed-daily-old-readback")
                .setSkillId("skill.cloudmold.old.readback.v1")
                .setInputJson(ManagedWorkflowDailyAutomationCatalog.DAILY_DISCOVERY_INPUT)
                .setStatus("ACTIVE");
        TemporalScheduleRecord desired = new TemporalScheduleRecord()
                .setTenantId(162L).setScheduleId("cloudmold-t162-managed-daily-role")
                .setSkillId("skill.cloudmold.role.v1")
                .setInputJson(ManagedWorkflowDailyAutomationCatalog.DAILY_DISCOVERY_INPUT)
                .setStatus("ACTIVE");
        TemporalScheduleRecord manual = new TemporalScheduleRecord()
                .setTenantId(162L).setScheduleId("cloudmold-t162-manual")
                .setSkillId("skill.cloudmold.manual.v1")
                .setInputJson("{}").setStatus("ACTIVE");
        TenantContextHolder.setTenantId(162L);
        when(mapper.selectSchedules(162L)).thenReturn(List.of(obsolete, desired, manual));
        when(client.getHandle(obsolete.getScheduleId())).thenReturn(obsoleteHandle);

        int paused = service.pauseObsoleteManagedDaily(Set.of(desired.getSkillId()));

        assertThat(paused).isEqualTo(1);
        verify(obsoleteHandle).pause("Definition is now an internal subflow");
        verify(mapper).updateScheduleStatus(eq(162L), eq(obsolete.getScheduleId()),
                eq("PAUSED"), any());
        verify(client, never()).getHandle(desired.getScheduleId());
        verify(client, never()).getHandle(manual.getScheduleId());
    }
}
