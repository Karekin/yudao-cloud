package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TemporalDailyDispatchActivitiesImplTest {

    @Test
    void confirmsExistingWorkflowAfterDispatchAcknowledgementCrash() {
        WorkflowClient client = mock(WorkflowClient.class);
        WorkflowStub stub = mock(WorkflowStub.class);
        when(client.newUntypedWorkflowStub("workflow-1")).thenReturn(stub);
        TemporalDailyDispatchActivitiesImpl activities = new TemporalDailyDispatchActivitiesImpl(
                mock(AiOperationsTemporalMapper.class),
                mock(TemporalAutomationCandidateMaterializer.class),
                mock(ReplenishmentExecutionProposalCandidateFactory.class),
                mock(RotatingBusinessScenarioInputFactory.class),
                client);

        assertThat(activities.workflowExists("workflow-1")).isTrue();
    }

    @Test
    void reportsMissingWorkflowSoCandidateCanBeReleasedForRetry() {
        WorkflowClient client = mock(WorkflowClient.class);
        WorkflowStub stub = mock(WorkflowStub.class);
        when(client.newUntypedWorkflowStub("workflow-1")).thenReturn(stub);
        doThrow(new WorkflowNotFoundException(
                WorkflowExecution.newBuilder().setWorkflowId("workflow-1").build(),
                null, null)).when(stub).describe();
        TemporalDailyDispatchActivitiesImpl activities = new TemporalDailyDispatchActivitiesImpl(
                mock(AiOperationsTemporalMapper.class),
                mock(TemporalAutomationCandidateMaterializer.class),
                mock(ReplenishmentExecutionProposalCandidateFactory.class),
                mock(RotatingBusinessScenarioInputFactory.class),
                client);

        assertThat(activities.workflowExists("workflow-1")).isFalse();
    }

    @Test
    void domainBacklogMaterializesGovernedProposalsInsteadOfOutboxEvents() {
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        TemporalAutomationCandidateMaterializer outbox =
                mock(TemporalAutomationCandidateMaterializer.class);
        ReplenishmentExecutionProposalCandidateFactory proposals =
                mock(ReplenishmentExecutionProposalCandidateFactory.class);
        TemporalDailyDispatchActivitiesImpl activities =
                new TemporalDailyDispatchActivitiesImpl(
                        mapper, outbox, proposals,
                        mock(RotatingBusinessScenarioInputFactory.class),
                        mock(WorkflowClient.class));
        TemporalDailyDispatchRequest request = TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId(ReplenishmentExecutionProposalCandidateFactory.SKILL_ID)
                .skillVersion("1.0.0")
                .inputStrategy("DOMAIN_BACKLOG")
                .maxFanOut(100)
                .build();

        assertThat(activities.claimCandidates(request, "2026-07-29", "lease-1"))
                .isEmpty();

        verify(proposals).materialize(request);
        verifyNoInteractions(outbox);
    }

    @Test
    void rotatingScenarioUsesOneStableBusinessKeyPerTemporalOccurrence() {
        RotatingBusinessScenarioInputFactory rotating =
                mock(RotatingBusinessScenarioInputFactory.class);
        when(rotating.build(
                162L, RotatingBusinessScenarioInputFactory.AUTONOMOUS_DAY_SKILL,
                "2026-07-29", "temporal-run-1")).thenReturn(Optional.of("{\"run\":\"one\"}"));
        when(rotating.build(
                162L, RotatingBusinessScenarioInputFactory.AUTONOMOUS_DAY_SKILL,
                "2026-07-29", "temporal-run-2")).thenReturn(Optional.of("{\"run\":\"two\"}"));
        TemporalDailyDispatchActivitiesImpl activities =
                new TemporalDailyDispatchActivitiesImpl(
                        mock(AiOperationsTemporalMapper.class),
                        mock(TemporalAutomationCandidateMaterializer.class),
                        mock(ReplenishmentExecutionProposalCandidateFactory.class),
                        rotating,
                        mock(WorkflowClient.class));
        TemporalDailyDispatchRequest request = TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId(RotatingBusinessScenarioInputFactory.AUTONOMOUS_DAY_SKILL)
                .skillVersion("1.0.0")
                .inputStrategy("ROTATING_BUSINESS_SCENARIO")
                .maxFanOut(1)
                .build();

        TemporalAutomationCandidate first =
                activities.claimCandidates(request, "2026-07-29", "temporal-run-1").get(0);
        TemporalAutomationCandidate replay =
                activities.claimCandidates(request, "2026-07-29", "temporal-run-1").get(0);
        TemporalAutomationCandidate next =
                activities.claimCandidates(request, "2026-07-29", "temporal-run-2").get(0);

        assertThat(replay.getBusinessKey()).isEqualTo(first.getBusinessKey());
        assertThat(next.getBusinessKey()).isNotEqualTo(first.getBusinessKey());
        assertThat(first.getBusinessKey())
                .startsWith("rotating-scenario/"
                        + RotatingBusinessScenarioInputFactory.AUTONOMOUS_DAY_SKILL
                        + "/2026-07-29/");
        assertThat(first.getInputJson()).isEqualTo("{\"run\":\"one\"}");
        assertThat(next.getInputJson()).isEqualTo("{\"run\":\"two\"}");
    }
}
