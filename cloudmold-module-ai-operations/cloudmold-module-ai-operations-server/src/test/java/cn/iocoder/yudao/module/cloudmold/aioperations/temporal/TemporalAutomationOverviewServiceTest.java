package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunQueryServiceFacade;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemporalAutomationOverviewServiceTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void separatesScheduleCoverageCandidateSourcesAndAutonomyProof() {
        AiOperationsManagedRunQueryServiceFacade workflows =
                mock(AiOperationsManagedRunQueryServiceFacade.class);
        AiOperationsTemporalScheduleService schedules =
                mock(AiOperationsTemporalScheduleService.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        TenantContextHolder.setTenantId(162L);
        ManagedSkillTaskWorkflowView aggregate = workflow(
                "skill.cloudmold.operations.daily-business-control.v1",
                "每日经营控制", 0);
        ManagedSkillTaskWorkflowView outbox = workflow(
                "skill.cloudmold.commerce.order-to-cash-readback.v1",
                "订单到回款核验", 0);
        ManagedSkillTaskWorkflowView rotatingScenario = workflow(
                "skill.cloudmold.commerce.product-to-listing.v1",
                "自动铺品", 6);
        ManagedSkillTaskWorkflowView domainBacklog = workflow(
                "skill.cloudmold.supply-planning.prepare.v1",
                "补货执行方案", 1);
        when(workflows.listWorkflowsAs(
                nullable(Long.class), nullable(Integer.class)))
                .thenReturn(List.of(aggregate, outbox, rotatingScenario, domainBacklog));
        TemporalScheduleView aggregateSchedule = schedule(
                aggregate, "schedule-aggregate", "ACTIVE");
        TemporalScheduleView outboxSchedule = schedule(
                outbox, "schedule-outbox", "ACTIVE");
        when(schedules.list()).thenReturn(
                List.of(aggregateSchedule, outboxSchedule));
        when(mapper.selectLatestDispatchObservation(
                162L, "schedule-aggregate"))
                .thenReturn(new TemporalDispatchObservationRecord()
                        .setOutcomeCode("NO_ACTION_DUE")
                        .setCandidateCount(0).setDispatchedCount(0).setFailedCount(0));
        when(mapper.selectLatestRunBindingForSchedule(
                162L, "schedule-outbox"))
                .thenReturn(new TemporalRunBindingRecord()
                        .setStatus("SUCCEEDED")
                        .setManagedRunId("managed-run-1"));

        TemporalAutomationOverviewView result =
                new TemporalAutomationOverviewService(
                        workflows, schedules, mapper).get();

        assertThat(result.getRegisteredCount()).isEqualTo(4);
        assertThat(result.getScheduledCount()).isEqualTo(2);
        assertThat(result.getHealthyScheduleCount()).isEqualTo(2);
        assertThat(result.getScheduleCoverageRate()).isEqualTo(50.0d);
        assertThat(result.getCandidateSourceConnectedCount()).isEqualTo(4);
        assertThat(result.getCandidateSourceCoverageRate()).isEqualTo(100.0d);
        assertThat(result.getAutonomyProvenCount()).isEqualTo(1);
        assertThat(result.getAutonomyProofCoverageRate()).isEqualTo(25.0d);
        assertThat(result.getWorkflows())
                .filteredOn(item -> item.getSkillId().equals(outbox.getSkillId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getDiscoverySource()).isEqualTo("OUTBOX_EVENT");
                    assertThat(item.getBusinessAutonomyState())
                            .isEqualTo("AUTONOMY_PROVEN");
                    assertThat(item.getProofRef()).isEqualTo("managed-run-1");
                });
        assertThat(result.getWorkflows())
                .filteredOn(item -> item.getSkillId().equals(
                        rotatingScenario.getSkillId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getScheduleState()).isEqualTo("MISSING");
                    assertThat(item.getDiscoverySource())
                            .isEqualTo("ROTATING_BUSINESS_SCENARIO");
                    assertThat(item.getGapCodes())
                            .containsExactly("SCHEDULE_MISSING",
                                    "AUTONOMY_PROOF_MISSING");
                });
        assertThat(result.getWorkflows())
                .filteredOn(item -> item.getSkillId().equals(
                        aggregate.getSkillId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getDiscoverySource())
                            .isEqualTo("TENANT_AGGREGATE");
                    assertThat(item.getBusinessAutonomyState())
                            .isEqualTo("READY_IDLE");
                });
        assertThat(result.getWorkflows())
                .filteredOn(item -> item.getSkillId().equals(
                        domainBacklog.getSkillId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getDiscoverySource())
                            .isEqualTo("DOMAIN_BACKLOG");
                    assertThat(item.getGapCodes())
                            .doesNotContain("GOVERNED_WRITE_INPUT_REQUIRED",
                                    "CANDIDATE_SOURCE_MISSING");
                });
    }

    private static ManagedSkillTaskWorkflowView workflow(
            String skillId, String displayName, int writeStepCount) {
        return ManagedSkillTaskWorkflowView.builder()
                .skillId(skillId)
                .skillVersion("1.0.0")
                .displayName(displayName)
                .writeStepCount(writeStepCount)
                .build();
    }

    private static TemporalScheduleView schedule(
            ManagedSkillTaskWorkflowView workflow, String scheduleId,
            String status) {
        return TemporalScheduleView.builder()
                .scheduleId(scheduleId)
                .skillId(workflow.getSkillId())
                .skillVersion(workflow.getSkillVersion())
                .status(status)
                .paused(false)
                .build();
    }
}
