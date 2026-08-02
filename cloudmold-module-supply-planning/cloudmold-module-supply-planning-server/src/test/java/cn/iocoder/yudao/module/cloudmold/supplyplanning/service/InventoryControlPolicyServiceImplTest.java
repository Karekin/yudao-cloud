package cn.iocoder.yudao.module.cloudmold.supplyplanning.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.InventoryHealthSnapshotResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyCommand;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyOperation;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.SafetyStockPolicyResult;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.InventoryIssueReference;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.SafetyStockPolicy;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.dataobject.InventoryControlPolicyRecords.SafetyStockPolicyVersion;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.dal.mysql.InventoryControlPolicyMapper;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.service.actor.SupplyPlanningActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryControlPolicyServiceImplTest {
    private static final String ACTOR = "principal-planner-01";

    private final InventoryControlPolicyMapper mapper = mock(InventoryControlPolicyMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final SupplyPlanningActorPrincipalPort actorPrincipalPort =
            mock(SupplyPlanningActorPrincipalPort.class);
    private final InventoryControlPolicyServiceImpl service =
            new InventoryControlPolicyServiceImpl(mapper, outbox, actorPrincipalPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(17L);
        when(mapper.insertOrResolveOperation(eq(17L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(101L);
        when(mapper.selectOperationForUpdate(101L, 17L)).thenAnswer(invocation ->
                new Operation().setOperationId(101L).setTenantId(17L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.markOperationSucceeded(eq(101L), eq(17L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void savesDraftPolicyAndEmitsOutboxEvent() {
        when(mapper.insertPolicy(any())).thenReturn(1);
        when(mapper.insertPolicyVersion(any())).thenReturn(1);

        SafetyStockPolicyResult result = service.execute(draftCommand(), ACTOR);

        assertThat(result.getAggregateType()).isEqualTo("safety_stock_policy");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(mapper).insertPolicy(argThat(policy ->
                policy.getTenantId().equals(17L)
                        && policy.getPolicyCode().equals("SSP-2026-08")
                        && policy.getStatus().equals("DRAFT")
                        && policy.getCurrentVersion().equals(1L)));
        verify(mapper).insertPolicyVersion(argThat(version ->
                version.getStatus().equals("DRAFT")
                        && version.getVersion().equals(1L)
                        && version.getActorPrincipalId().equals(ACTOR)));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.safety_stock_policy.saved")
                        && event.getAggregateType().equals("safety_stock_policy")
                        && event.getTenantId().equals(17L)
                        && event.getDestination().equals("lakehouse")));
    }

    @Test
    void publishesApprovedPolicyWhenNoOverlapExists() {
        when(mapper.selectPolicyForUpdate(17L, "policy-01")).thenReturn(new SafetyStockPolicy()
                .setPolicyId("policy-01").setTenantId(17L).setPolicyCode("SSP-2026-08")
                .setOwnerType("MERCHANT").setOwnerId("merchant-01").setCanonicalSkuId("sku-01")
                .setWarehouseNetworkId("network-01").setEffectiveFrom(LocalDate.of(2026, 8, 1))
                .setTargetServiceLevelBasisPoints(9500)
                .setSafetyStockQuantity(new BigDecimal("10"))
                .setReorderPointQuantity(new BigDecimal("25"))
                .setMaximumStockQuantity(new BigDecimal("80"))
                .setReplenishmentCycleDays(7).setLeadTimeDays(14)
                .setPolicyBasisCode("SERVICE_LEVEL_V1").setPolicySha256("a".repeat(64))
                .setStatus("APPROVED").setCurrentVersion(2L));
        when(mapper.countOverlappingPublishedPolicies(17L, "policy-01", "MERCHANT", "merchant-01",
                "sku-01", "network-01", LocalDate.of(2026, 8, 1), null)).thenReturn(0L);
        when(mapper.publishPolicy(eq(17L), eq("policy-01"), eq(2L), eq(3L), anyString(), eq(ACTOR), any()))
                .thenReturn(1);
        when(mapper.insertPolicyVersion(any())).thenReturn(1);

        SafetyStockPolicyResult result = service.execute(
                SafetyStockPolicyCommand.builder()
                        .operation(SafetyStockPolicyOperation.PUBLISH)
                        .idempotencyKey("inventory-control-policy-publish")
                        .runId("run-001")
                        .correlationId("11111111-1111-4111-8111-111111111111")
                        .occurredAt(Instant.parse("2026-08-02T12:00:00Z"))
                        .policy(SafetyStockPolicyCommand.PolicyDefinition.builder()
                                .policyId("policy-01")
                                .expectedVersion(2L)
                                .build())
                        .build(),
                ACTOR);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        assertThat(result.getAggregateVersion()).isEqualTo(3L);
        assertThat(result.getPolicyVersionId()).isNotBlank();
        verify(mapper).publishPolicy(eq(17L), eq("policy-01"), eq(2L), eq(3L), anyString(), eq(ACTOR), any());
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.safety_stock_policy.published")
                        && event.getAggregateVersion().equals(3L)));
    }

    @Test
    void capturesImmutableInventoryHealthSnapshotAgainstPublishedPolicyVersion() {
        when(mapper.selectPolicyVersion(17L, "policy-version-01")).thenReturn(new SafetyStockPolicyVersion()
                .setPolicyVersionId("policy-version-01").setTenantId(17L)
                .setPolicyId("policy-01").setPolicyCode("SSP-2026-08")
                .setVersion(3L).setStatus("PUBLISHED"));
        when(mapper.selectInventoryIssueReference(17L, "issue-01")).thenReturn(new InventoryIssueReference()
                .setIssueId("issue-01").setTenantId(17L).setSourceBalanceId("balance-01")
                .setIssueType("STOCKOUT").setSeverity("HIGH").setStatus("OPEN"));
        when(mapper.insertSnapshot(any())).thenReturn(1);
        when(mapper.insertSnapshotIssueRef(any())).thenReturn(1);

        InventoryHealthSnapshotResult result = service.capture(snapshotCommand(), ACTOR);

        assertThat(result.getAggregateType()).isEqualTo("inventory_health_snapshot");
        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(result.getAggregateVersion()).isEqualTo(1L);
        verify(mapper).insertSnapshot(argThat(snapshot ->
                snapshot.getTenantId().equals(17L)
                        && snapshot.getPolicyVersionId().equals("policy-version-01")
                        && snapshot.getPolicyCode().equals("SSP-2026-08")
                        && snapshot.getIssueCount().equals(1)
                        && snapshot.getStatus().equals("CAPTURED")));
        verify(mapper).insertSnapshotIssueRef(argThat(ref ->
                ref.getSnapshotId() != null
                        && ref.getIssueId().equals("issue-01")
                        && ref.getIssueType().equals("STOCKOUT")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("supply_planning.inventory_health_snapshot.captured")
                        && event.getAggregateType().equals("inventory_health_snapshot")
                        && event.getTenantId().equals(17L)));
    }

    private static SafetyStockPolicyCommand draftCommand() {
        return SafetyStockPolicyCommand.builder()
                .operation(SafetyStockPolicyOperation.SAVE_DRAFT)
                .idempotencyKey("inventory-control-policy-draft")
                .runId("run-001")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-08-02T12:00:00Z"))
                .policy(SafetyStockPolicyCommand.PolicyDefinition.builder()
                        .policyCode("SSP-2026-08")
                        .ownerType("MERCHANT")
                        .ownerId("merchant-01")
                        .canonicalSkuId("sku-01")
                        .warehouseNetworkId("network-01")
                        .effectiveFrom(LocalDate.of(2026, 8, 1))
                        .targetServiceLevelBasisPoints(9500)
                        .safetyStockQuantity(new BigDecimal("10"))
                        .reorderPointQuantity(new BigDecimal("25"))
                        .maximumStockQuantity(new BigDecimal("80"))
                        .replenishmentCycleDays(7)
                        .leadTimeDays(14)
                        .policyBasisCode("SERVICE_LEVEL_V1")
                        .policySha256("a".repeat(64))
                        .evidenceRef("evidence/policy/ss-2026-08")
                        .build())
                .build();
    }

    private static InventoryHealthSnapshotCommand snapshotCommand() {
        return InventoryHealthSnapshotCommand.builder()
                .idempotencyKey("inventory-health-snapshot-01")
                .runId("run-001")
                .correlationId("22222222-2222-4222-8222-222222222222")
                .occurredAt(Instant.parse("2026-08-02T12:10:00Z"))
                .snapshot(InventoryHealthSnapshotCommand.SnapshotDefinition.builder()
                        .snapshotCode("IHS-2026-08")
                        .policyId("policy-01")
                        .policyVersionId("policy-version-01")
                        .ledgerWatermarkRef("inventory-ledger-watermark/2026-08-02T12:09:59Z")
                        .ledgerWatermarkOccurredAt(Instant.parse("2026-08-02T12:09:59Z"))
                        .stockoutCount(1)
                        .lowStockCount(2)
                        .overstockCount(0)
                        .obsoleteCount(0)
                        .agedCount(1)
                        .shelfLifeRiskCount(0)
                        .shortageQuantity(new BigDecimal("12"))
                        .excessQuantity(BigDecimal.ZERO)
                        .atRiskQuantity(new BigDecimal("12"))
                        .snapshotSha256("b".repeat(64))
                        .issueRefs(List.of(InventoryHealthSnapshotCommand.IssueRefDefinition.builder()
                                .issueId("issue-01")
                                .build()))
                        .build())
                .build();
    }
}
