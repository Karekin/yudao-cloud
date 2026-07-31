package cn.iocoder.yudao.module.cloudmold.supplier.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceOperation;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceResult;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.MetricEvidence;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierPerformanceMapper;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SupplierPerformanceServiceImplTest {
    private static final long TENANT_ID = 162L;
    private static final String ACTOR = "principal-supplier-performance-01";
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 7, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 7, 31);

    private final SupplierPerformanceMapper mapper = mock(SupplierPerformanceMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final SupplierActorPrincipalPort actorPrincipalPort = mock(SupplierActorPrincipalPort.class);
    private final SupplierPerformanceServiceImpl service =
            new SupplierPerformanceServiceImpl(mapper, outbox, actorPrincipalPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(mapper.insertOrResolveOperation(eq(TENANT_ID), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(701L);
        when(mapper.selectOperationForUpdate(701L, TENANT_ID)).thenAnswer(invocation -> new Operation()
                .setOperationId(701L).setTenantId(TENANT_ID).setRequestHash(requestHash.get())
                .setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(701L), eq(TENANT_ID), any(), any(), any(), any())).thenReturn(1);
        when(mapper.countActiveAdmittedSupplier(TENANT_ID, "supplier-01")).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void recordsImmutableMetricEvidenceOnlyForAdmittedSupplier() {
        when(mapper.insertMetricEvidence(any())).thenReturn(1);

        SupplierPerformanceResult result = service.execute(base(SupplierPerformanceOperation.RECORD_METRIC_EVIDENCE)
                .metricEvidence(evidence("OTIF", "po-01", "10", "10"))
                .build(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("RECORDED");
        assertThat(result.getMetricEvidenceId()).isEqualTo("evidence-OTIF");
        verify(outbox).append(argThat(event -> event.getEventType().equals("supplier.performance.metric_evidence.recorded")
                && event.getPayload().get("metric_code").equals("OTIF")));
    }

    @Test
    void returnsNoActionAndDoesNotCreateEmptyScorecardWhenEvidenceIsIncomplete() {
        when(mapper.selectMetricEvidence(TENANT_ID, "supplier-01", PERIOD_START, PERIOD_END))
                .thenReturn(List.of(metric("OTIF", "po-01", "9", "10")));

        SupplierPerformanceResult result = service.execute(base(SupplierPerformanceOperation.GENERATE_SCORECARD)
                .scorecard(SupplierPerformanceCommand.ScorecardDefinition.builder().supplierId("supplier-01")
                        .periodStart(PERIOD_START).periodEnd(PERIOD_END).build()).build(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("NO_ACTION_DUE");
        assertThat(result.getMissingMetricCodes()).containsExactly(
                "QUALITY_PASS_RATE", "CAPACITY_ATTAINMENT", "CAPA_EFFECTIVENESS");
        verify(mapper, never()).insertScorecard(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void generatesVersionedEvidenceFrozenScorecardOnlyAfterAllFourMetricFamiliesExist() {
        when(mapper.selectMetricEvidence(TENANT_ID, "supplier-01", PERIOD_START, PERIOD_END)).thenReturn(List.of(
                metric("OTIF", "po-01", "9", "10"),
                metric("QUALITY_PASS_RATE", "inspection-01", "98", "100"),
                metric("CAPACITY_ATTAINMENT", "mes-01", "95", "100"),
                metric("CAPA_EFFECTIVENESS", "capa-01", "1", "1")));
        when(mapper.selectMaxScorecardVersion(TENANT_ID, "supplier-01", PERIOD_START, PERIOD_END)).thenReturn(2);
        when(mapper.insertScorecard(any())).thenReturn(1);

        SupplierPerformanceResult result = service.execute(base(SupplierPerformanceOperation.GENERATE_SCORECARD)
                .scorecard(SupplierPerformanceCommand.ScorecardDefinition.builder().scorecardId("scorecard-01")
                        .supplierId("supplier-01").periodStart(PERIOD_START).periodEnd(PERIOD_END).build())
                .build(), ACTOR);

        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getScorecardId()).isEqualTo("scorecard-01");
        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        verify(mapper).insertScorecard(argThat(scorecard -> scorecard.getScorecardVersion() == 3
                && scorecard.getOtifBps() == 9000 && scorecard.getQualityBps() == 9800
                && scorecard.getCapacityBps() == 9500 && scorecard.getCapaBps() == 10000
                && scorecard.getOverallBps() == 9440 && scorecard.getAssessment().equals("HEALTHY")));
        verify(outbox).append(argThat(event -> event.getEventType().equals("supplier.performance.scorecard.generated")
                && event.getPayload().get("scorecard_id").equals("scorecard-01")));
    }

    private static SupplierPerformanceCommand.SupplierPerformanceCommandBuilder base(
            SupplierPerformanceOperation operation) {
        return SupplierPerformanceCommand.builder().operation(operation)
                .idempotencyKey("supplier-performance-" + operation).runId("run-001")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-07-31T00:00:00Z"));
    }

    private static SupplierPerformanceCommand.MetricEvidenceDefinition evidence(String metricCode,
                                                                                 String sourceRecordId,
                                                                                 String numerator,
                                                                                 String denominator) {
        return SupplierPerformanceCommand.MetricEvidenceDefinition.builder()
                .metricEvidenceId("evidence-" + metricCode).supplierId("supplier-01")
                .periodStart(PERIOD_START).periodEnd(PERIOD_END).sourceSystem("CLOUDMOLD_PROCUREMENT")
                .sourceRecordId(sourceRecordId).metricCode(metricCode).numerator(new BigDecimal(numerator))
                .denominator(new BigDecimal(denominator)).evidenceSha256("a".repeat(64))
                .observedAt(Instant.parse("2026-07-31T00:00:00Z")).build();
    }

    private static MetricEvidence metric(String code, String sourceRecordId, String numerator, String denominator) {
        return new MetricEvidence().setMetricCode(code).setSourceSystem("CLOUDMOLD_PROCUREMENT")
                .setSourceRecordId(sourceRecordId).setNumerator(new BigDecimal(numerator))
                .setDenominator(new BigDecimal(denominator)).setEvidenceSha256("b".repeat(64));
    }
}
