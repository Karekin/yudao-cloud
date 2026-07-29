package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemporalAutomationCandidateMaterializerTest {

    @Test
    void materializesCanonicalOutboxEventWithStableIdempotencyKey() {
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AutomationOutboxEventRecord event = new AutomationOutboxEventRecord()
                .setTenantId(162L)
                .setEventId("event-payment-1")
                .setEventType("payment.status.changed")
                .setAggregateType("payment")
                .setAggregateId("payment-1")
                .setPayload("{\"order_id\":\"order-1\",\"payment_id\":\"payment-1\"}")
                .setRecordedAt(LocalDateTime.of(2026, 7, 29, 2, 0));
        when(mapper.selectUnmaterializedAutomationEvents(
                eq(162L),
                any(), any(), eq(100)))
                .thenReturn(List.of(event));
        when(mapper.insertAutomationCandidate(any())).thenReturn(1);
        TemporalAutomationCandidateMaterializer materializer =
                new TemporalAutomationCandidateMaterializer(mapper);

        int created = materializer.materialize(TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId("skill.cloudmold.payment.reconciliation-readback.v1")
                .skillVersion("1.0.0")
                .maxFanOut(100)
                .build());

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<TemporalAutomationCandidateRecord> candidate =
                ArgumentCaptor.forClass(TemporalAutomationCandidateRecord.class);
        verify(mapper).insertAutomationCandidate(candidate.capture());
        assertThat(candidate.getValue())
                .extracting(TemporalAutomationCandidateRecord::getClientRequestKey,
                        TemporalAutomationCandidateRecord::getBusinessKey,
                        TemporalAutomationCandidateRecord::getInputJson,
                        TemporalAutomationCandidateRecord::getStatus)
                .containsExactly(candidate.getValue().getClientRequestKey(), "payment-1",
                        "{\"orderId\":\"order-1\",\"paymentId\":\"payment-1\"}",
                        "PENDING");
        assertThat(candidate.getValue().getClientRequestKey()).startsWith("execution:");
        assertThat(candidate.getValue().getCandidateId()).startsWith("mac-").hasSize(36);
        assertThat(candidate.getValue().getDueAt()).isEqualTo(event.getRecordedAt());
        ArgumentCaptor<AutomationCandidateEventRecord> provenance =
                ArgumentCaptor.forClass(AutomationCandidateEventRecord.class);
        verify(mapper).insertAutomationCandidateEvent(provenance.capture());
        assertThat(provenance.getValue().getDisposition()).isEqualTo("MATERIALIZED");
        assertThat(provenance.getValue().getCandidateId())
                .isEqualTo(candidate.getValue().getCandidateId());
    }

    @Test
    void coalescesMultipleStatusEventsForOneBusinessObject() {
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AutomationOutboxEventRecord first = paymentEvent("event-payment-1", "AUTHORIZED");
        AutomationOutboxEventRecord second = paymentEvent("event-payment-2", "CAPTURED");
        when(mapper.selectUnmaterializedAutomationEvents(
                eq(162L), any(), any(), eq(100))).thenReturn(List.of(first, second));
        when(mapper.insertAutomationCandidate(any())).thenReturn(1, 0);

        TemporalAutomationCandidateMaterializer materializer =
                new TemporalAutomationCandidateMaterializer(mapper);
        int created = materializer.materialize(TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId("skill.cloudmold.payment.reconciliation-readback.v1")
                .skillVersion("1.0.0")
                .maxFanOut(100)
                .build());

        assertThat(created).isEqualTo(1);
        ArgumentCaptor<TemporalAutomationCandidateRecord> candidates =
                ArgumentCaptor.forClass(TemporalAutomationCandidateRecord.class);
        verify(mapper, times(2)).insertAutomationCandidate(candidates.capture());
        assertThat(candidates.getAllValues())
                .extracting(TemporalAutomationCandidateRecord::getCandidateId)
                .containsExactly(candidates.getAllValues().get(0).getCandidateId(),
                        candidates.getAllValues().get(0).getCandidateId());
        ArgumentCaptor<AutomationCandidateEventRecord> provenance =
                ArgumentCaptor.forClass(AutomationCandidateEventRecord.class);
        verify(mapper, times(2)).insertAutomationCandidateEvent(provenance.capture());
        assertThat(provenance.getAllValues())
                .extracting(AutomationCandidateEventRecord::getDisposition)
                .containsExactly("MATERIALIZED", "COALESCED");
    }

    @Test
    void recordsPoisonEventAsRejectedSoItCannotStarveLaterEvents() {
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        AutomationOutboxEventRecord invalid = new AutomationOutboxEventRecord()
                .setTenantId(162L)
                .setEventId("event-invalid")
                .setEventType("payment.status.changed")
                .setAggregateType("payment")
                .setAggregateId("payment-1")
                .setPayload("{}");
        when(mapper.selectUnmaterializedAutomationEvents(
                eq(162L), any(), any(), eq(100))).thenReturn(List.of(invalid));

        TemporalAutomationCandidateMaterializer materializer =
                new TemporalAutomationCandidateMaterializer(mapper);
        int created = materializer.materialize(TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId("skill.cloudmold.payment.reconciliation-readback.v1")
                .skillVersion("1.0.0")
                .maxFanOut(100)
                .build());

        assertThat(created).isZero();
        ArgumentCaptor<AutomationCandidateEventRecord> provenance =
                ArgumentCaptor.forClass(AutomationCandidateEventRecord.class);
        verify(mapper).insertAutomationCandidateEvent(provenance.capture());
        assertThat(provenance.getValue())
                .extracting(AutomationCandidateEventRecord::getDisposition,
                        AutomationCandidateEventRecord::getRejectionReason)
                .containsExactly("REJECTED", "ROUTE_INPUT_NOT_MATERIALIZABLE");
    }

    @Test
    void leavesWriteWorkflowWithoutOutboxDiscovery() {
        AiOperationsTemporalMapper mapper = mock(AiOperationsTemporalMapper.class);
        TemporalAutomationCandidateMaterializer materializer =
                new TemporalAutomationCandidateMaterializer(mapper);

        int created = materializer.materialize(TemporalDailyDispatchRequest.builder()
                .tenantId(162L)
                .skillId("skill.cloudmold.commerce.product-to-listing.v1")
                .skillVersion("1.0.0")
                .maxFanOut(100)
                .build());

        assertThat(created).isZero();
    }

    private static AutomationOutboxEventRecord paymentEvent(String eventId, String status) {
        return new AutomationOutboxEventRecord()
                .setTenantId(162L)
                .setEventId(eventId)
                .setEventType("payment.status.changed")
                .setAggregateType("payment")
                .setAggregateId("payment-1")
                .setPayload("{\"order_id\":\"order-1\",\"payment_id\":\"payment-1\","
                        + "\"current_status\":\"" + status + "\"}")
                .setRecordedAt(LocalDateTime.of(2026, 7, 29, 2, 0));
    }
}
