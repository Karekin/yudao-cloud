package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryMigrationPilotApi.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceMappingQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryMigrationPilotServiceImplTest {

    private static final String BATCH = "50000000-0000-4000-8000-000000000001";
    private static final String RUN = "50000000-0000-4000-8000-000000000002";
    private static final String ITEM = "50000000-0000-4000-8000-000000000003";
    private static final String HASH = "a".repeat(64);

    private final InventoryMigrationStoreMapper migrationMapper = mock(InventoryMigrationStoreMapper.class);
    private final InventoryMigrationPilotMapper pilotMapper = mock(InventoryMigrationPilotMapper.class);
    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryLotMapper lotMapper = mock(InventoryLotMapper.class);
    private final MerchantOwnerValidationApi merchantApi = mock(MerchantOwnerValidationApi.class);
    private final SourceMappingQueryApi merchantMappingApi = mock(SourceMappingQueryApi.class);
    private final CatalogSkuValidationApi catalogApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseSourceMappingQueryApi warehouseMappingApi = mock(WarehouseSourceMappingQueryApi.class);
    private final WarehouseReferenceValidationApi warehouseApi = mock(WarehouseReferenceValidationApi.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final InventoryMigrationPilotProperties properties = productionProperties();
    private final InventoryMigrationPilotServiceImpl service = new InventoryMigrationPilotServiceImpl(
            migrationMapper, pilotMapper, balanceMapper, lotMapper, merchantApi, merchantMappingApi,
            catalogApi, warehouseMappingApi, warehouseApi, outbox, properties);
    private final AtomicReference<InventoryMigrationOperationDO> operation = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(migrationMapper.insertOrResolveCommand(anyLong(), anyString(), nullable(String.class), anyString(),
                anyString(), anyString(), any())).thenAnswer(invocation -> {
            InventoryMigrationOperationDO value = new InventoryMigrationOperationDO();
            value.setOperationId(101L);
            value.setAttemptToken(invocation.getArgument(5));
            value.setRequestHash(invocation.getArgument(4));
            value.setStatus(0);
            operation.set(value);
            return 1;
        });
        when(migrationMapper.selectLastInsertId()).thenReturn(101L);
        when(migrationMapper.selectOperationForUpdate(1L, 101L)).thenAnswer(invocation -> operation.get());
        when(migrationMapper.markOperationSucceeded(eq(1L), eq(101L), eq(RUN), anyString(), any())).thenReturn(1);
        when(pilotMapper.insertApproval(any())).thenReturn(1);
        when(pilotMapper.insertCheckpoint(anyString(), eq(1L), eq(BATCH), anyString(), anyLong(), anyLong(),
                eq(HASH), eq(HASH), eq(1), anyString(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void serverDefaultsKeepPilotAndAdmissionDisabled() {
        InventoryMigrationPilotProperties disabled = new InventoryMigrationPilotProperties();
        InventoryMigrationPilotServiceImpl disabledService = new InventoryMigrationPilotServiceImpl(
                migrationMapper, pilotMapper, balanceMapper, lotMapper, merchantApi, merchantMappingApi,
                catalogApi, warehouseMappingApi, warehouseApi, outbox, disabled);

        assertThatThrownBy(() -> disabledService.admit(BATCH, admissionCommand(), 40L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
        assertThat(disabled.isPilotEnabled()).isFalse();
        assertThat(disabled.isProductionAdmissionEnabled()).isFalse();
        verifyNoInteractions(pilotMapper);
    }

    @Test
    void requesterCannotApproveOwnFrozenManifest() {
        InventoryMigrationPilotBatchDO batch = batch("FROZEN", 1L, 0, 10L);
        when(pilotMapper.selectBatchForUpdate(1L, BATCH)).thenReturn(batch);

        assertThatThrownBy(() -> service.approve(BATCH, approvalCommand("DATA_OWNER", 1L), 10L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requester");
        verify(pilotMapper, never()).insertApproval(any());
    }

    @Test
    void firstIndependentApprovalAdvancesOnlyToPartiallyApproved() {
        InventoryMigrationPilotBatchDO batch = batch("FROZEN", 1L, 0, 10L);
        InventoryMigrationPilotItemDO item = item("FROZEN", 1L);
        when(pilotMapper.selectBatchForUpdate(1L, BATCH)).thenReturn(batch);
        when(pilotMapper.selectApprovalsForUpdate(1L, BATCH)).thenReturn(List.of());
        when(pilotMapper.markApproved(eq(1L), eq(BATCH), eq(1L), eq(1), eq("PARTIALLY_APPROVED"),
                isNull(), any())).thenReturn(1);
        when(pilotMapper.selectItemsForUpdate(1L, BATCH)).thenReturn(List.of(item));

        PilotBatchResult result = service.approve(BATCH, approvalCommand("DATA_OWNER", 1L), 20L);

        assertThat(result.getStatus()).isEqualTo("PARTIALLY_APPROVED");
        assertThat(result.getApprovalCount()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().extracting(PilotItemResult::getStatus).isEqualTo("FROZEN");
        verify(pilotMapper, never()).markItemsApproved(anyLong(), anyString(), any());
        verify(outbox).append(argThat((AppendDomainEventCommand event) ->
                InventoryMigrationPilotServiceImpl.BATCH_EVENT.equals(event.getEventType())
                        && Boolean.FALSE.equals(event.getPayload().get("execution_available"))));
    }

    @Test
    void oneActorCannotSatisfyBothApprovalRoles() {
        InventoryMigrationPilotBatchDO batch = batch("PARTIALLY_APPROVED", 2L, 1, 10L);
        when(pilotMapper.selectBatchForUpdate(1L, BATCH)).thenReturn(batch);
        when(pilotMapper.selectApprovalsForUpdate(1L, BATCH)).thenReturn(List.of(approval("DATA_OWNER", 20L)));

        assertThatThrownBy(() -> service.approve(BATCH, approvalCommand("CHANGE_MANAGER", 2L), 20L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("one actor");
        verify(pilotMapper, never()).insertApproval(any());
    }

    @Test
    void approverCannotExecuteAdmission() {
        InventoryMigrationPilotBatchDO batch = batch("APPROVED", 3L, 2, 10L);
        when(pilotMapper.selectBatchForUpdate(1L, BATCH)).thenReturn(batch);
        when(pilotMapper.selectApprovalsForUpdate(1L, BATCH)).thenReturn(List.of(
                approval("DATA_OWNER", 20L), approval("CHANGE_MANAGER", 30L)));

        assertThatThrownBy(() -> service.admit(BATCH, admissionCommand(), 20L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("approver");
        verify(pilotMapper, never()).markAdmitted(anyLong(), anyString(), anyLong(), anyLong(), any());
    }

    private static InventoryMigrationPilotProperties productionProperties() {
        InventoryMigrationPilotProperties value = new InventoryMigrationPilotProperties();
        value.setEnvironment("PRODUCTION");
        value.setEnvironmentFingerprint("prod-cn-sha256:fixture");
        value.setPilotEnabled(true);
        value.setProductionAdmissionEnabled(true);
        return value;
    }

    private static InventoryMigrationPilotBatchDO batch(String status, Long version, int approvalCount,
                                                         Long requesterId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new InventoryMigrationPilotBatchDO().setBatchId(BATCH).setTenantId(1L).setMigrationRunId(RUN)
                .setEnvironment("PRODUCTION").setSourceClassification("PRODUCTION_HISTORY")
                .setManifestHash(HASH).setPolicyHash(HASH).setPolicyVersion("pilot-v1")
                .setExpectedItemCount(1).setExpectedOnHandQuantity(new BigDecimal("10.000000"))
                .setApprovalCount(approvalCount).setRequesterId(requesterId).setStatus(status).setVersion(version)
                .setExecutionWindowStart(now.minusMinutes(1)).setExecutionWindowEnd(now.plusMinutes(5))
                .setTargetWatermarkAppliedAt(now).setMaxLagSeconds(300);
    }

    private static InventoryMigrationPilotItemDO item(String status, Long version) {
        return new InventoryMigrationPilotItemDO().setItemId(ITEM).setOrdinal(1)
                .setSourceOnHandQuantity(new BigDecimal("10.000000")).setStatus(status).setVersion(version);
    }

    private static InventoryMigrationPilotApprovalDO approval(String role, Long actor) {
        return new InventoryMigrationPilotApprovalDO().setApprovalId(UUID.randomUUID().toString())
                .setApprovalRole(role).setApproverId(actor).setScopeHash(HASH).setPolicyHash(HASH)
                .setEvidenceRef("ticket:approval").setStatus("APPROVED")
                .setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(4));
    }

    private static ApprovePilotBatchCommand approvalCommand(String role, Long version) {
        return new ApprovePilotBatchCommand().setIdempotencyKey("pilot:approve:" + role)
                .setApprovalRole(role).setExpectedVersion(version).setEvidenceRef("ticket:approval")
                .setExpiresAt(Instant.now().plusSeconds(180)).setCorrelationId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now());
    }

    private static AdmitPilotBatchCommand admissionCommand() {
        return new AdmitPilotBatchCommand().setIdempotencyKey("pilot:admit").setExpectedVersion(3L)
                .setEvidenceRef("ticket:admission").setCorrelationId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now());
    }
}
