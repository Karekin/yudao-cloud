package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionProposalView;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SupplyPlanningQueryApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReplenishmentExecutionProposalCandidateFactoryTest {

    @Test
    void materializesStableMinimalCandidateFromReadyDomainProposal() {
        SupplyPlanningQueryApi queryApi = mock(SupplyPlanningQueryApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        ReplenishmentExecutionProposalCandidateFactory factory =
                new ReplenishmentExecutionProposalCandidateFactory(queryApi, mapper);
        LocalDateTime proposedAt = LocalDateTime.of(2026, 7, 29, 9, 30);
        when(queryApi.listReadyReplenishmentExecutionProposals(100))
                .thenReturn(List.of(ReplenishmentExecutionProposalView.builder()
                        .proposalId("proposal-1")
                        .recommendationId("recommendation-1")
                        .expectedRecommendationVersion(2L)
                        .policySha256("a".repeat(64))
                        .proposedAt(proposedAt)
                        .build()));
        when(mapper.insertAutomationCandidate(
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        int created = factory.materialize(request(
                ReplenishmentExecutionProposalCandidateFactory.SKILL_ID));

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<TemporalAutomationCandidateRecord> captor =
                ArgumentCaptor.forClass(TemporalAutomationCandidateRecord.class);
        verify(mapper).insertAutomationCandidate(captor.capture());
        TemporalAutomationCandidateRecord record = captor.getValue();
        assertThat(record.getCandidateId()).startsWith("mac-").hasSize(36);
        assertThat(record.getClientRequestKey()).startsWith("execution:");
        assertThat(record.getBusinessKey()).isEqualTo(
                "replenishment-execution-proposal/proposal-1/recommendation-version/2");
        assertThat(record.getInputJson())
                .contains("\"proposalId\":\"proposal-1\"")
                .contains("\"recommendationId\":\"recommendation-1\"")
                .contains("\"conversionId\":")
                .contains("\"occurredAt\":\"2026-07-29T09:30:00Z\"")
                .doesNotContain("mappingEvidenceSha256", "supplierId", "targetWarehouseId");
        assertThat(record.getDueAt()).isEqualTo(proposedAt);
        assertThat(record.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void ignoresUnrelatedManagedWorkflow() {
        SupplyPlanningQueryApi queryApi = mock(SupplyPlanningQueryApi.class);
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        ReplenishmentExecutionProposalCandidateFactory factory =
                new ReplenishmentExecutionProposalCandidateFactory(queryApi, mapper);

        assertThat(factory.materialize(request("skill.cloudmold.other.v1"))).isZero();

        verifyNoInteractions(queryApi, mapper);
    }

    private static TemporalDailyDispatchRequest request(String skillId) {
        return TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId(skillId)
                .skillVersion("1.0.0")
                .maxFanOut(100)
                .build();
    }
}
