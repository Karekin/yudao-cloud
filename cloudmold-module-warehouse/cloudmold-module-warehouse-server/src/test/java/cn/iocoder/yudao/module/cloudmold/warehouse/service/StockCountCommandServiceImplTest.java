package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountSnapshotView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.StockCountOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.StockCountOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.StockCountStoreMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StockCountCommandServiceImplTest {

    private final StockCountStoreMapper mapper = mock(StockCountStoreMapper.class);
    private final InventoryStockCountSnapshotApi inventorySnapshotApi = mock(InventoryStockCountSnapshotApi.class);
    private final InventoryStockCountAdjustmentApi inventoryAdjustmentApi = mock(InventoryStockCountAdjustmentApi.class);
    private final CatalogSkuValidationApi catalogSkuValidationApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouseValidationApi = mock(WarehouseReferenceValidationApi.class);
    private final WarehouseActorPrincipalPort actorPrincipalPort = mock(WarehouseActorPrincipalPort.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);

    private final StockCountCommandServiceImpl service = new StockCountCommandServiceImpl(
            mapper, inventorySnapshotApi, inventoryAdjustmentApi, catalogSkuValidationApi, warehouseValidationApi,
            actorPrincipalPort, outboxAppender);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("evt-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsDraftStockCount() {
        StockCountCommand command = draftCommand();
        prepareOperation(command, 11L);
        when(mapper.insertStockCount(any())).thenReturn(1);
        when(mapper.insertLine(any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(11L), eq(1L), anyString(), anyString(), any())).thenReturn(1);

        var result = service.execute(command);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getProcessedLineCount()).isEqualTo(1);
        verify(catalogSkuValidationApi).requireActiveSku("sku-01");
        verify(warehouseValidationApi).requireActiveLocation("warehouse-01", "location-01");
    }

    @Test
    void submitsDraftByFreezingBalances() {
        StockCountCommand command = StockCountCommand.builder()
                .operation(StockCountOperation.SUBMIT)
                .idempotencyKey("stock-count-submit-01")
                .occurredAt(Instant.parse("2026-08-02T08:10:00Z"))
                .stockCountId("count-01")
                .actorPrincipalId("principal-01")
                .build();
        prepareOperation(command, 21L);
        when(mapper.selectStockCountForUpdate(1L, "count-01")).thenReturn(new StockCountDO()
                .setStockCountId("count-01").setStockCountCode("STKCNT-01").setStatus("DRAFT").setVersion(1L));
        when(mapper.selectLines(1L, "count-01")).thenReturn(List.of(new StockCountLineDO()
                .setLineId("line-01").setVersion(1L).setOwnerType("MERCHANT").setOwnerId("owner-01")
                .setCanonicalSkuId("sku-01").setWarehouseId("warehouse-01").setLocationId("location-01")
                .setLotId(null).setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PIECE")));
        when(mapper.selectFreezeLedgerTransactionId(1L)).thenReturn(88L);
        when(inventorySnapshotApi.requireSnapshot(any())).thenReturn(InventoryStockCountSnapshotView.builder()
                .balanceId("balance-01").baseUomCode("PIECE").onHandQuantity(new BigDecimal("12.000000"))
                .reservedQuantity(new BigDecimal("0.000000")).inTransitQuantity(new BigDecimal("0.000000"))
                .aggregateVersion(4L).build());
        when(mapper.updateLineSnapshotCas(eq(1L), eq("line-01"), eq(1L), eq("balance-01"),
                eq(new BigDecimal("12.000000")), eq(new BigDecimal("0.000000")), eq(new BigDecimal("0.000000")),
                eq(new BigDecimal("12.000000")), eq(4L), eq("SUBMITTED"), any(), any())).thenReturn(1);
        when(mapper.updateStockCountCas(eq(1L), eq("count-01"), eq(1L), eq(2L), eq("SUBMITTED"),
                eq(88L), any(), eq(1), eq(0), eq(0), any(), any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(21L), eq(1L), anyString(), anyString(), any())).thenReturn(1);

        var result = service.execute(command);

        assertThat(result.getStatus()).isEqualTo("SUBMITTED");
        assertThat(result.getFreezeLedgerTransactionId()).isEqualTo(88L);
    }

    @Test
    void adjustsApprovedDifferencesThroughDedicatedInventoryApi() {
        StockCountCommand command = StockCountCommand.builder()
                .operation(StockCountOperation.ADJUST)
                .idempotencyKey("stock-count-adjust-01")
                .occurredAt(Instant.parse("2026-08-02T08:20:00Z"))
                .stockCountId("count-01")
                .actorPrincipalId("principal-01")
                .build();
        prepareOperation(command, 31L);
        when(mapper.selectStockCountForUpdate(1L, "count-01")).thenReturn(new StockCountDO()
                .setStockCountId("count-01").setStockCountCode("STKCNT-01").setStatus("DIFFERENCE_APPROVED")
                .setVersion(3L).setLineCount(1).setCountedLineCount(1).setDifferenceLineCount(1).setFreezeLedgerTransactionId(88L));
        when(mapper.selectLines(1L, "count-01")).thenReturn(List.of(new StockCountLineDO()
                .setLineId("line-01").setVersion(2L).setOwnerType("MERCHANT").setOwnerId("owner-01")
                .setCanonicalSkuId("sku-01").setWarehouseId("warehouse-01").setLocationId("location-01")
                .setLotId(null).setStockStatus("SELLABLE").setQualityStatus("QUALIFIED").setBaseUomCode("PIECE")
                .setBookOnHandQuantity(new BigDecimal("10.000000")).setCountedOnHandQuantity(new BigDecimal("8.000000"))
                .setDifferenceQuantity(new BigDecimal("-2.000000")).setCountStatus("COUNTED")));
        when(inventoryAdjustmentApi.execute(any())).thenReturn(InventoryStockCountAdjustmentResult.builder()
                .adjustmentId("adj-01").ledgerTransactionId(701L).aggregateVersion(6L).build());
        when(mapper.updateLineAdjustmentCas(eq(1L), eq("line-01"), eq(2L), eq("adj-01"), eq(701L), eq(6L), any()))
                .thenReturn(1);
        when(mapper.updateStockCountCas(eq(1L), eq("count-01"), eq(3L), eq(4L), eq("ADJUSTED"),
                eq(88L), any(), eq(1), eq(1), eq(1), any(), any())).thenReturn(1);
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(31L), eq(1L), anyString(), anyString(), any())).thenReturn(1);

        var result = service.execute(command);

        assertThat(result.getStatus()).isEqualTo("ADJUSTED");
        verify(inventoryAdjustmentApi).execute(any());
    }

    private void prepareOperation(StockCountCommand command, long operationId) {
        AtomicReference<String> requestHash = new AtomicReference<>();
        AtomicReference<String> attemptToken = new AtomicReference<>();
        doAnswer(invocation -> {
            requestHash.set(invocation.getArgument(4));
            attemptToken.set(invocation.getArgument(5));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), any(), anyString(), anyString(), anyString(), any());
        when(mapper.selectLastInsertId()).thenReturn(operationId);
        when(mapper.selectOperationForUpdate(operationId, 1L)).thenAnswer(invocation -> new StockCountOperationDO()
                .setOperationId(operationId).setTenantId(1L).setAttemptToken(attemptToken.get()).setRequestHash(requestHash.get()));
    }

    private StockCountCommand draftCommand() {
        return StockCountCommand.builder()
                .operation(StockCountOperation.CREATE_DRAFT)
                .idempotencyKey("stock-count-op-01")
                .occurredAt(Instant.parse("2026-08-02T08:00:00Z"))
                .countMode("BLIND_COUNT")
                .scopeType("EXPLICIT_LINES")
                .scopeLabel("warehouse-01 blind count")
                .reasonCode("ROUTINE")
                .actorPrincipalId("principal-01")
                .lines(List.of(StockCountCommand.LineDefinition.builder()
                        .ownerType("MERCHANT")
                        .ownerId("owner-01")
                        .canonicalSkuId("sku-01")
                        .warehouseId("warehouse-01")
                        .locationId("location-01")
                        .stockStatus("SELLABLE")
                        .qualityStatus("QUALIFIED")
                        .baseUomCode("PIECE")
                        .remark("rack A")
                        .build()))
                .build();
    }
}
