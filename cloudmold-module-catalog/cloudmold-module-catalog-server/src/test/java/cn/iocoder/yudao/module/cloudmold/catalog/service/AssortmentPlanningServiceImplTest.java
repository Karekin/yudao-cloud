package cn.iocoder.yudao.module.cloudmold.catalog.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommand;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningCommandResult;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningOperation;
import cn.iocoder.yudao.module.cloudmold.catalog.api.AssortmentPlanningWorkflowView;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentCandidateDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentPlanningOperationDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentWaveDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject.AssortmentWaveHistoryDO;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.AssortmentCandidateMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.AssortmentPlanningOperationMapper;
import cn.iocoder.yudao.module.cloudmold.catalog.dal.mysql.AssortmentWaveMapper;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssortmentPlanningServiceImplTest {

    private static final long TENANT_ID = 162L;
    private static final String WAVE_ID = "wave-2026-autumn-01";
    private static final String ACTOR = "principal-assortment-manager";
    private static final String CORRELATION_ID = "11111111-1111-4111-8111-111111111111";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-30T10:00:00Z");

    private final AssortmentPlanningOperationMapper operationMapper =
            mock(AssortmentPlanningOperationMapper.class);
    private final AssortmentWaveMapper waveMapper = mock(AssortmentWaveMapper.class);
    private final AssortmentCandidateMapper candidateMapper = mock(AssortmentCandidateMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final AssortmentPlanningServiceImpl service = new AssortmentPlanningServiceImpl(
            operationMapper, waveMapper, candidateMapper, outboxAppender);

    private final AtomicLong operationSequence = new AtomicLong(900L);
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private final Map<String, AssortmentPlanningOperationDO> operationsByKey = new LinkedHashMap<>();
    private final Map<Long, AssortmentPlanningOperationDO> operationsById = new LinkedHashMap<>();
    private final AtomicReference<AssortmentWaveDO> currentWave = new AtomicReference<>();
    private final Map<String, AssortmentCandidateDO> candidates = new LinkedHashMap<>();
    private final List<AssortmentWaveHistoryDO> history = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(outboxAppender.append(any())).thenReturn(null);
        when(operationMapper.insertOrResolve(eq(TENANT_ID), anyString(), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            String idempotencyKey = invocation.getArgument(1);
            AssortmentPlanningOperationDO existing = operationsByKey.get(idempotencyKey);
            if (existing == null) {
                long operationId = operationSequence.incrementAndGet();
                AssortmentPlanningOperationDO row = new AssortmentPlanningOperationDO()
                        .setOperationId(operationId)
                        .setTenantId(TENANT_ID)
                        .setIdempotencyKey(idempotencyKey)
                        .setCommandType(invocation.getArgument(2))
                        .setRequestHash(invocation.getArgument(3))
                        .setAttemptToken(invocation.getArgument(4))
                        .setStatus(0);
                operationsByKey.put(idempotencyKey, row);
                operationsById.put(operationId, row);
                lastOperationId.set(operationId);
            } else {
                lastOperationId.set(existing.getOperationId());
            }
            return 1;
        });
        when(operationMapper.selectLastInsertId()).thenAnswer(invocation -> lastOperationId.get());
        when(operationMapper.selectForUpdate(anyLong(), eq(TENANT_ID)))
                .thenAnswer(invocation -> operationsById.get(invocation.getArgument(0)));
        when(operationMapper.markSucceeded(anyLong(), eq(TENANT_ID), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            AssortmentPlanningOperationDO row = operationsById.get(invocation.getArgument(0));
            if (row == null || row.getStatus() != 0) return 0;
            row.setStatus(10)
                    .setAggregateType(invocation.getArgument(2))
                    .setAggregateId(invocation.getArgument(3))
                    .setResultJson(invocation.getArgument(4));
            return 1;
        });

        when(waveMapper.insert(any(AssortmentWaveDO.class))).thenAnswer(invocation -> {
            currentWave.set(copy(invocation.getArgument(0), AssortmentWaveDO.class));
            return 1;
        });
        when(waveMapper.selectForUpdate(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            AssortmentWaveDO row = currentWave.get();
            return row != null && Objects.equals(row.getWaveId(), invocation.getArgument(1))
                    ? copy(row, AssortmentWaveDO.class) : null;
        });
        when(waveMapper.selectOneById(anyLong(), anyString())).thenAnswer(invocation -> {
            AssortmentWaveDO row = currentWave.get();
            return row != null
                    && Objects.equals(row.getTenantId(), invocation.getArgument(0))
                    && Objects.equals(row.getWaveId(), invocation.getArgument(1))
                    ? copy(row, AssortmentWaveDO.class) : null;
        });
        when(waveMapper.updateWorkflowState(eq(TENANT_ID), any(AssortmentWaveDO.class), anyLong()))
                .thenAnswer(invocation -> {
                    AssortmentWaveDO current = currentWave.get();
                    AssortmentWaveDO incoming = invocation.getArgument(1);
                    Long expected = invocation.getArgument(2);
                    if (current == null || !Objects.equals(current.getVersion(), expected)) return 0;
                    currentWave.set(copy(incoming, AssortmentWaveDO.class));
                    return 1;
                });
        when(waveMapper.insertHistory(any())).thenAnswer(invocation -> {
            AssortmentWaveHistoryDO item =
                    copy(invocation.getArgument(0), AssortmentWaveHistoryDO.class);
            item.setHistoryId((long) history.size() + 1);
            history.add(item);
            return 1;
        });

        when(candidateMapper.insert(any(AssortmentCandidateDO.class))).thenAnswer(invocation -> {
            AssortmentCandidateDO row =
                    copy(invocation.getArgument(0), AssortmentCandidateDO.class);
            candidates.put(row.getCandidateId(), row);
            return 1;
        });
        when(candidateMapper.selectForUpdate(eq(TENANT_ID), anyString())).thenAnswer(invocation -> {
            AssortmentCandidateDO row = candidates.get(invocation.getArgument(1));
            return row == null ? null : copy(row, AssortmentCandidateDO.class);
        });
        when(candidateMapper.selectByWave(eq(TENANT_ID), anyString())).thenAnswer(invocation ->
                candidates.values().stream()
                        .filter(row -> Objects.equals(row.getWaveId(), invocation.getArgument(1)))
                        .map(row -> copy(row, AssortmentCandidateDO.class))
                        .toList());
        when(candidateMapper.updateEvaluation(eq(TENANT_ID),
                any(AssortmentCandidateDO.class), anyLong())).thenAnswer(invocation -> {
            AssortmentCandidateDO incoming = invocation.getArgument(1);
            AssortmentCandidateDO current = candidates.get(incoming.getCandidateId());
            Long expected = invocation.getArgument(2);
            if (current == null || !Objects.equals(current.getVersion(), expected)) return 0;
            candidates.put(incoming.getCandidateId(),
                    copy(incoming, AssortmentCandidateDO.class));
            return 1;
        });
        when(candidateMapper.updateSelection(eq(TENANT_ID), eq(WAVE_ID), anyString(),
                anyString(), any())).thenAnswer(invocation -> {
            AssortmentCandidateDO current = candidates.get(invocation.getArgument(2));
            if (current == null || !"EVALUATED".equals(current.getStatus())) return 0;
            current.setStatus(invocation.getArgument(3))
                    .setVersion(current.getVersion() + 1);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void completesAuditableSelectionAndLaunchCalendarWorkflow() {
        service.execute(createWave("create-wave"));
        addCandidate("entry", "ENTRY", 19_900L, 9_000L);
        evaluateCandidate("entry", 86, 90, 92, 20, 900);
        addCandidate("core", "CORE", 29_900L, 14_000L);
        evaluateCandidate("core", 92, 88, 90, 24, 1_100);
        addCandidate("premium", "PREMIUM", 49_900L, 30_000L);
        evaluateCandidate("premium", 95, 82, 84, 60, 2_400);

        AssortmentPlanningCommandResult selection = service.execute(selectPortfolio());
        service.execute(approveWave());
        AssortmentPlanningCommandResult publication = service.execute(publishWave());
        AssortmentPlanningWorkflowView workflow = service.getWorkflow(WAVE_ID);

        assertThat(selection.getSelectedCandidateIds()).containsExactlyInAnyOrder(
                "candidate-entry", "candidate-core");
        assertThat(publication.getStatus()).isEqualTo("PUBLISHED");
        assertThat(publication.getAggregateVersion()).isEqualTo(10L);
        assertThat(workflow.getTerminal()).isTrue();
        assertThat(workflow.getBlockers()).isEmpty();
        assertThat(workflow.getSelectedStyleCount()).isEqualTo(2);
        assertThat(workflow.getArtifacts())
                .extracting(AssortmentPlanningWorkflowView.Artifact::getType)
                .contains("ASSORTMENT_WAVE", "SELECTED_PRODUCT_CONCEPT",
                        "LAUNCH_CALENDAR", "PRODUCT_DEVELOPMENT_HANDOFF");
        assertThat(candidates.get("candidate-premium").getStatus()).isEqualTo("REJECTED");
        assertThat(history).hasSize(10);
        verify(outboxAppender, times(10)).append(any());
    }

    @Test
    void replaysAnIdenticalCreateWithoutASecondBusinessWrite() {
        AssortmentPlanningCommand command = createWave("create-duplicate");

        AssortmentPlanningCommandResult first = service.execute(command);
        AssortmentPlanningCommandResult replay = service.execute(command);

        assertThat(first.getDuplicate()).isFalse();
        assertThat(replay.getDuplicate()).isTrue();
        assertThat(history).hasSize(1);
        verify(outboxAppender, times(1)).append(any());
    }

    @Test
    void rejectsSelectionWhenMarginAndReturnGuardrailsCannotFillThePortfolio() {
        service.execute(createWave("create-guardrail"));
        addCandidate("entry", "ENTRY", 19_900L, 9_000L);
        evaluateCandidate("entry", 86, 90, 92, 20, 900);
        addCandidate("core", "CORE", 29_900L, 20_000L);
        evaluateCandidate("core", 92, 88, 90, 24, 1_100);

        assertThatThrownBy(() -> service.execute(selectPortfolio()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot satisfy target style count");
        assertThat(currentWave.get().getStatus()).isEqualTo("BUILDING");
    }

    @Test
    void rejectsStaleCandidateMutationAndCrossTenantRead() {
        service.execute(createWave("create-isolation"));

        AssortmentPlanningCommand stale = addCandidateCommand(
                "entry", "ENTRY", 19_900L, 9_000L, 0L);
        assertThatThrownBy(() -> service.execute(stale))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedWaveVersion");

        TenantContextHolder.setTenantId(163L);
        assertThatThrownBy(() -> service.getWorkflow(WAVE_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    private void addCandidate(String key, String priceBand, long price, long cost) {
        service.execute(addCandidateCommand(key, priceBand, price, cost, version()));
    }

    private void evaluateCandidate(String key, int trend, int demand, int fit,
                                   int supplyRisk, int returnRateBps) {
        service.execute(base(AssortmentPlanningOperation.EVALUATE_CANDIDATE, "eval-" + key)
                .evaluation(AssortmentPlanningCommand.EvaluationDefinition.builder()
                        .waveId(WAVE_ID)
                        .expectedWaveVersion(version())
                        .candidateId("candidate-" + key)
                        .trendScore(trend)
                        .demandScore(demand)
                        .audienceFitScore(fit)
                        .supplyRiskScore(supplyRisk)
                        .predictedReturnRateBps(returnRateBps)
                        .evidenceSha256(sha("evaluation-" + key))
                        .rationale("基于趋势、需求、受众、供给和退货风险的 AI 评估 " + key)
                        .reasonCode("AI_CANDIDATE_EVALUATED")
                        .build())
                .build());
    }

    private AssortmentPlanningCommand createWave(String idempotencyKey) {
        return base(AssortmentPlanningOperation.CREATE_WAVE, idempotencyKey)
                .wave(AssortmentPlanningCommand.WaveDefinition.builder()
                        .waveId(WAVE_ID)
                        .waveCode("AW_2026_01")
                        .planningYear(2026)
                        .seasonCode("AUTUMN")
                        .categoryCode("WOMENSWEAR")
                        .trendBrief("轻户外通勤需求增长，强调可持续面料与多场景穿搭")
                        .targetAudience("25-35 岁城市女性，重视功能与设计平衡")
                        .targetStyleCount(2)
                        .targetPriceFloorMinor(10_000L)
                        .targetPriceCeilingMinor(50_000L)
                        .targetGrossMarginBps(4_500)
                        .maxReturnRateBps(1_800)
                        .launchStartDate(LocalDate.of(2026, 8, 15))
                        .launchEndDate(LocalDate.of(2026, 8, 31))
                        .currencyCode("CNY")
                        .reasonCode("AI_WAVE_CREATED")
                        .build())
                .build();
    }

    private AssortmentPlanningCommand addCandidateCommand(
            String key, String priceBand, long price, long cost, long expectedVersion) {
        return base(AssortmentPlanningOperation.ADD_CANDIDATE, "add-" + key)
                .candidate(AssortmentPlanningCommand.CandidateDefinition.builder()
                        .waveId(WAVE_ID)
                        .expectedWaveVersion(expectedVersion)
                        .candidateId("candidate-" + key)
                        .candidateCode("CANDIDATE_" + key.toUpperCase())
                        .productConcept("AI 波段候选款 " + key)
                        .sourceSignalType("SEARCH_TREND")
                        .sourceSignalRef("trend:" + key)
                        .priceBandCode(priceBand)
                        .targetPriceMinor(price)
                        .expectedUnitCostMinor(cost)
                        .reasonCode("AI_CANDIDATE_DISCOVERED")
                        .build())
                .build();
    }

    private AssortmentPlanningCommand selectPortfolio() {
        return base(AssortmentPlanningOperation.SELECT_PORTFOLIO, "select-portfolio")
                .portfolio(AssortmentPlanningCommand.PortfolioDefinition.builder()
                        .waveId(WAVE_ID)
                        .expectedWaveVersion(version())
                        .decisionPolicyVersion("assortment-ai-policy-v1")
                        .decisionEvidenceSha256(sha("selection"))
                        .reasonCode("AI_PORTFOLIO_SELECTED")
                        .build())
                .build();
    }

    private AssortmentPlanningCommand approveWave() {
        return base(AssortmentPlanningOperation.APPROVE_WAVE, "approve-wave")
                .approval(AssortmentPlanningCommand.ApprovalDefinition.builder()
                        .waveId(WAVE_ID)
                        .expectedWaveVersion(version())
                        .approvalRef("approval:assortment-wave-2026-01")
                        .approvalNote("买手与财务独立复核通过")
                        .reasonCode("ASSORTMENT_APPROVED")
                        .build())
                .build();
    }

    private AssortmentPlanningCommand publishWave() {
        return base(AssortmentPlanningOperation.PUBLISH_WAVE, "publish-wave")
                .publication(AssortmentPlanningCommand.PublicationDefinition.builder()
                        .waveId(WAVE_ID)
                        .expectedWaveVersion(version())
                        .launchCalendarRef("launch-calendar:AW_2026_01")
                        .downstreamHandoffRef("product-development:AW_2026_01")
                        .publicationEvidenceSha256(sha("publication"))
                        .reasonCode("ASSORTMENT_PUBLISHED")
                        .build())
                .build();
    }

    private long version() {
        return currentWave.get().getVersion();
    }

    private static AssortmentPlanningCommand.AssortmentPlanningCommandBuilder base(
            AssortmentPlanningOperation operation, String idempotencyKey) {
        return AssortmentPlanningCommand.builder()
                .operation(operation)
                .correlationId(CORRELATION_ID)
                .runId("run-assortment-2026-01")
                .idempotencyKey(idempotencyKey)
                .occurredAt(OCCURRED_AT)
                .actorPrincipalId(ACTOR);
    }

    private static String sha(String value) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex(value);
    }

    private static <T> T copy(T value, Class<T> type) {
        return JsonUtils.parseObject(JsonUtils.toJsonString(value), type);
    }
}
