package cn.iocoder.yudao.module.cloudmold.order.service.migration;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeBenefitGovernanceCommand;
import cn.iocoder.yudao.module.cloudmold.order.api.migration.LegacyTradeBenefitGovernanceResult;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.migration.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.migration.LegacyTradeBenefitGovernanceMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyTradeBenefitGovernanceServiceImplTest {

    private static final String RUN = "49000000-0000-4000-8000-000000000001";
    private static final String SOURCE_RUN = "45000000-0000-4000-8000-000000000001";
    private static final String CORRELATION = "49000000-0000-4000-8000-000000000002";
    private static final String CANDIDATE_A = "49000000-0000-4000-8000-000000000003";
    private static final String CANDIDATE_B = "49000000-0000-4000-8000-000000000004";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 7, 0);

    private final LegacyTradeBenefitGovernanceMapper mapper = mock(LegacyTradeBenefitGovernanceMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final LegacyTradeBenefitGovernanceServiceImpl service =
            new LegacyTradeBenefitGovernanceServiceImpl(mapper, outboxAppender);
    private final List<LegacyTradeBenefitGovernanceComponentDO> savedComponents = new ArrayList<>();
    private final List<LegacyTradeBenefitGovernanceQuarantineDO> savedQuarantines = new ArrayList<>();
    private final List<AppendDomainEventCommand> events = new ArrayList<>();
    private LegacyTradeBenefitGovernanceOperationDO operation;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        doAnswer(invocation -> {
            if (operation == null) {
                operation = new LegacyTradeBenefitGovernanceOperationDO().setOperationId(1L).setTenantId(1L)
                        .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4))
                        .setStatus(0);
            }
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), nullable(String.class), anyString(),
                anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(1L);
        when(mapper.selectOperationForUpdate(1L, 1L)).thenAnswer(ignored -> operation);
        doAnswer(invocation -> {
            operation.setStatus(10).setGovernanceRunId(invocation.getArgument(2))
                    .setResultJson(invocation.getArgument(3));
            return 1;
        }).when(mapper).markOperationSucceeded(eq(1L), eq(1L), anyString(), anyString(), any());
        when(mapper.insertRun(any())).thenReturn(1);
        doAnswer(invocation -> { savedComponents.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insertComponent(any());
        doAnswer(invocation -> { savedQuarantines.add(invocation.getArgument(0)); return 1; })
                .when(mapper).insertQuarantine(any());
        doAnswer(invocation -> { events.add(invocation.getArgument(0)); return null; })
                .when(outboxAppender).append(any());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void separatesCurrentObservationFromHistoricalQualificationAndNamedFunding() {
        LegacyTradeBenefitGovernanceComponentSourceDO observed = source("c1", CANDIDATE_A, 11L,
                "GENERIC_DISCOUNT", 100L, "SECKILL:41")
                .setObservedSourceTable("promotion_seckill_activity").setObservedSourceId(41L)
                .setObservedSourceCreatedAt(NOW.minusYears(2)).setObservedSourceUpdatedAt(NOW.minusYears(1))
                .setObservedSourceStatus("0").setObservedSourceDeleted(false).setObservedSourceSpuId(634L);
        LegacyTradeBenefitGovernanceComponentSourceDO vip = source("c2", CANDIDATE_A, 11L,
                "VIP", 200L, null);
        LegacyTradeBenefitGovernanceComponentSourceDO qualified = source("c3", CANDIDATE_B, 12L,
                "POINT", 300L, "POINT_QUANTITY:30");
        String qualifiedHash = LegacyTradeBenefitGovernanceServiceImpl.sourceComponentEvidenceHash(qualified);
        qualified.setIdentityQualificationCount(1).setIdentityQualificationId("identity-q-1")
                .setIdentitySourceComponentEvidenceHash(qualifiedHash).setFundingShareCount(2)
                .setFundingAmountMinor(300L).setFundingSourceEvidenceHashCount(1)
                .setFundingSourceComponentEvidenceHash(qualifiedHash);
        when(mapper.selectSourceRun(1L, SOURCE_RUN)).thenReturn(sourceRun(3, 1));
        when(mapper.selectSourceComponents(1L, SOURCE_RUN)).thenReturn(List.of(observed, vip, qualified));
        when(mapper.selectSourceQuarantines(1L, SOURCE_RUN)).thenReturn(List.of(openQuarantine()));

        LegacyTradeBenefitGovernanceResult result = service.assess(command());

        assertThat(result.getSourceComponentCount()).isEqualTo(3);
        assertThat(result.getSourceReferencePresentCount()).isEqualTo(2);
        assertThat(result.getCurrentReferenceObservedCount()).isEqualTo(1);
        assertThat(result.getHistoricalIdentityQualifiedCount()).isEqualTo(1);
        assertThat(result.getIdentityBlockedCount()).isEqualTo(2);
        assertThat(result.getFundingQualifiedCount()).isEqualTo(1);
        assertThat(result.getFundingBlockedCount()).isEqualTo(2);
        assertThat(result.getSourceQuarantineCount()).isEqualTo(1);
        assertThat(result.getQuarantineDecidedCount()).isZero();
        assertThat(result.getQuarantineOpenCount()).isEqualTo(1);
        assertThat(result.getGovernanceAdmittedComponentCount()).isEqualTo(1);
        assertThat(result.getProductionMigrationEnabled()).isFalse();
        assertThat(result.getStatus()).isEqualTo(
                "BLOCKED_REQUIRES_HISTORICAL_BENEFIT_FUNDING_AND_QUARANTINE_DECISIONS");
        assertThat(savedComponents).extracting(LegacyTradeBenefitGovernanceComponentDO::getCurrentReferenceStatus)
                .containsExactly("CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION",
                        "MISSING_SOURCE_REFERENCE", "NON_VERSIONED_ENTITLEMENT_QUANTITY_ONLY");
        assertThat(savedComponents.get(0).getHistoricalIdentityStatus())
                .isEqualTo("BLOCKED_MISSING_HISTORICAL_VERSION");
        assertThat(savedComponents.get(0).getCurrentReferenceSnapshotHash()).matches("[0-9a-f]{64}");
        assertThat(savedComponents.get(2).getGovernanceStatus()).isEqualTo("READY");
        assertThat(savedComponents).allSatisfy(value -> assertThat(value.getCanonicalImportAllowed()).isFalse());
        assertThat(savedQuarantines).singleElement().satisfies(value -> {
            assertThat(value.getDecisionStatus()).isEqualTo("OPEN");
            assertThat(value.getRecommendedAction()).isEqualTo("CORRECT_SOURCE_AND_REASSESS");
            assertThat(value.getCanonicalImportAllowed()).isFalse();
        });
        assertThat(events).hasSize(2).allSatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(LegacyTradeBenefitGovernanceServiceImpl.GOVERNANCE_EVENT);
            assertThat(event.getSchemaVersion()).isEqualTo(2);
            assertThat(event.getAggregateType()).isEqualTo("legacy_trade_benefit_governance_readiness");
            assertThat(event.getAggregateId()).isNotIn(CANDIDATE_A, CANDIDATE_B);
            assertThat(event.getPayload()).containsEntry("governance_run_id", RUN)
                    .containsEntry("run_source_component_count", 3)
                    .containsEntry("run_current_reference_observed_count", 1)
                    .containsEntry("run_governance_admitted_component_count", 1)
                    .containsEntry("production_migration_enabled", false);
        });
    }

    @Test
    void doesNotAcceptQualificationRowsBoundToAnotherSourceEvidenceHash() {
        LegacyTradeBenefitGovernanceComponentSourceDO source = source("c4", CANDIDATE_A, 13L,
                "GENERIC_DISCOUNT", 100L, "SECKILL:41")
                .setIdentityQualificationCount(1).setIdentityQualificationId("identity-q-2")
                .setIdentitySourceComponentEvidenceHash("a".repeat(64)).setFundingShareCount(1)
                .setFundingAmountMinor(100L).setFundingSourceEvidenceHashCount(1)
                .setFundingSourceComponentEvidenceHash("a".repeat(64));

        LegacyTradeBenefitGovernanceComponentDO assessed =
                LegacyTradeBenefitGovernanceServiceImpl.assessComponent(1L, command(), source, NOW);

        assertThat(assessed.getIdentityQualificationId()).isNull();
        assertThat(assessed.getHistoricalIdentityStatus()).isEqualTo("BLOCKED_SOURCE_REFERENCE_NOT_FOUND");
        assertThat(assessed.getFundingResolutionStatus()).isEqualTo("BLOCKED_FUNDING_AMOUNT_MISMATCH");
        assertThat(assessed.getBlockerCodes()).contains("IDENTITY_QUALIFICATION_EVIDENCE_MISMATCH");
        assertThat(assessed.getGovernanceAdmissionAllowed()).isFalse();
    }

    @Test
    void acceptsOnlyAQuarantineDecisionBoundToTheImmutableCandidateSnapshot() {
        LegacyTradeBenefitGovernanceQuarantineSourceDO source = openQuarantine()
                .setDecisionCount(1).setDecisionId("decision-1")
                .setDecisionSourceCandidateEvidenceHash("b".repeat(64));
        LegacyTradeBenefitGovernanceQuarantineDO mismatch =
                LegacyTradeBenefitGovernanceServiceImpl.assessQuarantine(1L, command(), source, NOW);
        source.setDecisionSourceCandidateEvidenceHash(source.getLegacySnapshotHash());
        LegacyTradeBenefitGovernanceQuarantineDO decided =
                LegacyTradeBenefitGovernanceServiceImpl.assessQuarantine(1L, command(), source, NOW);

        assertThat(mismatch.getDecisionStatus()).isEqualTo("OPEN");
        assertThat(mismatch.getDecisionId()).isNull();
        assertThat(mismatch.getBlockerCodes()).contains("QUARANTINE_DECISION_EVIDENCE_MISMATCH");
        assertThat(decided.getDecisionStatus()).isEqualTo("DECIDED");
        assertThat(decided.getDecisionId()).isEqualTo("decision-1");
        assertThat(decided.getRecommendedAction()).isEqualTo("EXCLUDE_CONFIRMED_SOURCE_DEFECT");
        assertThat(decided.getBlockerCodes()).isEqualTo("[]");
        assertThat(decided.getCanonicalImportAllowed()).isFalse();
    }

    @Test
    void immutableReplayDoesNotReadSourceOrAppendEvents() {
        when(mapper.selectSourceRun(1L, SOURCE_RUN)).thenReturn(sourceRun(1, 0));
        when(mapper.selectSourceComponents(1L, SOURCE_RUN)).thenReturn(List.of(
                source("c5", CANDIDATE_A, 14L, "VIP", 100L, null)));
        when(mapper.selectSourceQuarantines(1L, SOURCE_RUN)).thenReturn(List.of());
        LegacyTradeBenefitGovernanceCommand command = command();
        LegacyTradeBenefitGovernanceResult first = service.assess(command);
        clearInvocations(mapper, outboxAppender);

        LegacyTradeBenefitGovernanceResult replay = service.assess(command);

        assertThat(replay.getDuplicate()).isTrue();
        assertThat(replay.getGovernanceEvidenceHash()).isEqualTo(first.getGovernanceEvidenceHash());
        verify(mapper).insertOrResolveOperation(eq(1L), anyString(), nullable(String.class), anyString(),
                anyString(), any());
        verify(mapper).selectLastInsertId();
        verify(mapper).selectOperationForUpdate(1L, 1L);
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(outboxAppender);
    }

    @Test
    void parserBypassKeepsEveryTableStatementExplicitlyTenantScoped() {
        InterceptorIgnore bypass = LegacyTradeBenefitGovernanceMapper.class
                .getAnnotation(InterceptorIgnore.class);
        assertThat(bypass).isNotNull();
        assertThat(bypass.tenantLine()).isEqualTo("true");
        Arrays.stream(LegacyTradeBenefitGovernanceMapper.class.getDeclaredMethods())
                .filter(method -> !method.getName().equals("selectLastInsertId"))
                .forEach(method -> {
                    Stream<String> select = method.isAnnotationPresent(Select.class)
                            ? Arrays.stream(method.getAnnotation(Select.class).value()) : Stream.empty();
                    Stream<String> insert = method.isAnnotationPresent(Insert.class)
                            ? Arrays.stream(method.getAnnotation(Insert.class).value()) : Stream.empty();
                    Stream<String> update = method.isAnnotationPresent(Update.class)
                            ? Arrays.stream(method.getAnnotation(Update.class).value()) : Stream.empty();
                    String sql = Stream.of(select, insert, update).flatMap(value -> value)
                            .reduce("", (left, right) -> left + " " + right).toLowerCase();
                    assertThat(sql).as(method.getName()).contains("tenant_id");
                });
    }

    private static LegacyTradeBenefitMigrationRunDO sourceRun(int components, int quarantines) {
        return new LegacyTradeBenefitMigrationRunDO().setMigrationRunId(SOURCE_RUN).setTenantId(1L)
                .setPolicyVersion("legacy-trade-benefit-v5").setItemEvidenceComplete(true)
                .setBenefitComponentCount(components).setQuarantinedOrderCount(quarantines);
    }

    private static LegacyTradeBenefitGovernanceComponentSourceDO source(
            String suffix, String candidateId, long orderId, String type, long amount, String sourceReference) {
        return new LegacyTradeBenefitGovernanceComponentSourceDO().setTenantId(1L)
                .setSourceMigrationRunId(SOURCE_RUN).setComponentId("component-" + suffix)
                .setCandidateId(candidateId).setLegacyOrderId(orderId).setComponentType(type)
                .setComponentAmountMinor(amount).setSourceReference(sourceReference)
                .setIdentityQualificationCount(0).setFundingShareCount(0).setFundingAmountMinor(0L)
                .setFundingSourceEvidenceHashCount(0);
    }

    private static LegacyTradeBenefitGovernanceQuarantineSourceDO openQuarantine() {
        return new LegacyTradeBenefitGovernanceQuarantineSourceDO().setTenantId(1L)
                .setSourceMigrationRunId(SOURCE_RUN).setCandidateId(CANDIDATE_B).setLegacyOrderId(12L)
                .setLegacyOrderNo("T12").setLegacySnapshotHash("c".repeat(64))
                .setAssessmentStatus("QUARANTINED_MONEY").setReasonCodes("[\"NEGATIVE_MONEY\"]")
                .setDecisionCount(0);
    }

    private static LegacyTradeBenefitGovernanceCommand command() {
        return new LegacyTradeBenefitGovernanceCommand().setIdempotencyKey("benefit-governance-test")
                .setGovernanceRunId(RUN).setSourceMigrationRunId(SOURCE_RUN)
                .setPolicyVersion(LegacyTradeBenefitGovernanceServiceImpl.POLICY_VERSION)
                .setEvidenceRef("evidence:test").setCorrelationId(CORRELATION)
                .setOccurredAt(Instant.parse("2026-07-17T07:00:00Z"));
    }
}
