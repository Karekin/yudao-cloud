package cn.iocoder.yudao.module.cloudmold.warehouse.service.inventoryscrap;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.inventoryscrap.InventoryScrapResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapDocumentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InventoryScrapOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InventoryScrapStoreMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryScrapCommandServiceImplTest {

    private final InventoryScrapStoreMapper mapper = mock(InventoryScrapStoreMapper.class);
    private final WarehouseActorPrincipalPort actorPrincipalPort = mock(WarehouseActorPrincipalPort.class);
    private final WarehouseReferenceValidationApi warehouseApi = mock(WarehouseReferenceValidationApi.class);
    private final CatalogSkuValidationApi skuApi = mock(CatalogSkuValidationApi.class);
    private final InventoryScrapDispositionApi inventoryApi = mock(InventoryScrapDispositionApi.class);

    private final InventoryScrapCommandServiceImpl service = new InventoryScrapCommandServiceImpl(
            mapper, actorPrincipalPort, warehouseApi, skuApi, inventoryApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsDraftDocumentAndLines() {
        InventoryScrapCommand command = createCommand();
        when(mapper.selectLastInsertId()).thenReturn(11L);
        doAnswer(invocation -> {
            String attemptToken = invocation.getArgument(5);
            when(mapper.selectOperationForUpdate(1L, 11L)).thenReturn(new InventoryScrapOperationDO()
                    .setOperationId(11L).setTenantId(1L).setAttemptToken(attemptToken).setStatus(0));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), any(), any(), any(), any(), any());
        when(mapper.selectDocument(1L, "scrap-01")).thenReturn(null);
        when(mapper.insertDocument(any())).thenReturn(1);
        when(mapper.insertLine(any())).thenReturn(1);
        when(mapper.insertHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(1L), eq(11L), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        InventoryScrapResult result = service.execute(command);

        assertThat(result.getScrapStatus()).isEqualTo("DRAFT");
        verify(warehouseApi).requireActiveWarehouse("warehouse-01");
        verify(warehouseApi).requireActiveLocation("warehouse-01", "location-01");
        verify(skuApi).requireActiveSku("sku-01");
        verify(actorPrincipalPort).requireActive("principal-01");
    }

    @Test
    void recordsDispositionBatchAndTransitionsToDisposed() {
        InventoryScrapCommand command = dispositionCommand();
        when(mapper.selectLastInsertId()).thenReturn(21L);
        doAnswer(invocation -> {
            String attemptToken = invocation.getArgument(5);
            when(mapper.selectOperationForUpdate(1L, 21L)).thenReturn(new InventoryScrapOperationDO()
                    .setOperationId(21L).setTenantId(1L).setAttemptToken(attemptToken).setStatus(0));
            return 1;
        }).when(mapper).insertOrResolveOperation(eq(1L), anyString(), any(), any(), any(), any(), any());
        when(mapper.selectDocument(1L, "scrap-01")).thenReturn(document("APPROVED", 2L,
                new BigDecimal("5.000000"), ZERO));
        when(mapper.selectDocumentForUpdate(1L, "scrap-01")).thenReturn(document("APPROVED", 2L,
                new BigDecimal("5.000000"), ZERO));
        when(mapper.insertDispositionBatch(any())).thenReturn(1);
        when(mapper.selectLineForUpdate(1L, "scrap-01", 10)).thenReturn(line("scrap-line-01", 10,
                new BigDecimal("5.000000"), ZERO, "APPROVED", 2L));
        when(mapper.updateLineDispositionCas(eq(1L), eq("scrap-line-01"), eq(2L), eq(3L),
                eq(new BigDecimal("5.000000")), eq("DISPOSED"), any())).thenReturn(1);
        when(mapper.insertDispositionLine(any())).thenReturn(1);
        when(mapper.updateDispositionCas(eq(1L), eq("scrap-01"), eq(2L), eq(3L),
                eq(new BigDecimal("5.000000")), eq("DISPOSED"), any())).thenReturn(1);
        when(mapper.insertHistory(any())).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(1L), eq(21L), anyString(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(inventoryApi.execute(any(InventoryScrapDispositionCommand.class))).thenReturn(
                InventoryScrapDispositionResult.builder()
                        .operationId(301L).ledgerTransactionId(401L).balanceId("balance-01")
                        .aggregateVersion(9L).onHandQuantity(new BigDecimal("8.000000"))
                        .disposedQuantity(new BigDecimal("5.000000")).build());

        InventoryScrapResult result = service.execute(command);

        assertThat(result.getScrapStatus()).isEqualTo("DISPOSED");
        ArgumentCaptor<InventoryScrapDispositionCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryScrapDispositionCommand.class);
        verify(inventoryApi).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getScrapDocumentId()).isEqualTo("scrap-01");
        assertThat(commandCaptor.getValue().getQuantity()).isEqualTo(new BigDecimal("5.000000"));
    }

    private InventoryScrapCommand createCommand() {
        return InventoryScrapCommand.builder()
                .operation(InventoryScrapOperation.CREATE_DRAFT)
                .idempotencyKey("scrap-op-01")
                .sourceEventId("source-event-01")
                .actorPrincipalId("principal-01")
                .occurredAt(Instant.parse("2026-08-02T03:00:00Z"))
                .scrapId("scrap-01")
                .scrapCode("SCRAP-0001")
                .reasonCode("DAMAGED_STOCK")
                .remark("draft")
                .ownerType("MERCHANT")
                .ownerId("owner-01")
                .warehouseId("warehouse-01")
                .lines(List.of(InventoryScrapCommand.LineDefinition.builder()
                        .lineId("scrap-line-01").lineNumber(10).canonicalSkuId("sku-01")
                        .locationId("location-01").stockStatus("NON_SELLABLE").qualityStatus("DAMAGED")
                        .baseUomCode("PCS").requestedQuantity(new BigDecimal("5.000000"))
                        .evidenceType("QUALITY").evidenceRef("quality-01").build()))
                .build();
    }

    private InventoryScrapCommand dispositionCommand() {
        return InventoryScrapCommand.builder()
                .operation(InventoryScrapOperation.RECORD_DISPOSITION_BATCH)
                .idempotencyKey("scrap-op-02")
                .sourceEventId("source-event-02")
                .actorPrincipalId("principal-02")
                .occurredAt(Instant.parse("2026-08-02T04:00:00Z"))
                .scrapId("scrap-01")
                .reasonCode("DAMAGED_STOCK")
                .remark("dispose")
                .ownerType("MERCHANT")
                .ownerId("owner-01")
                .warehouseId("warehouse-01")
                .dispositionBatch(InventoryScrapCommand.DispositionBatchDefinition.builder()
                        .batchId("batch-01").batchNo("SCRAPB-0001").dispositionType("DESTROYED")
                        .proofType("CERTIFICATE").proofRef("cert-01").remark("destroyed")
                        .lines(List.of(InventoryScrapCommand.DispositionLineDefinition.builder()
                                .dispositionLineId("disp-line-01").lineNumber(10)
                                .disposedQuantity(new BigDecimal("5.000000")).build()))
                        .build())
                .build();
    }

    private InventoryScrapDocumentDO document(String status, Long version, BigDecimal requested, BigDecimal disposed) {
        return new InventoryScrapDocumentDO().setScrapId("scrap-01").setTenantId(1L).setScrapCode("SCRAP-0001")
                .setReasonCode("DAMAGED_STOCK").setOwnerType("MERCHANT").setOwnerId("owner-01")
                .setWarehouseId("warehouse-01").setStatus(status).setVersion(version)
                .setTotalRequestedQuantity(requested).setTotalDisposedQuantity(disposed).setLineCount(1);
    }

    private InventoryScrapLineDO line(String lineId, int lineNumber, BigDecimal requested, BigDecimal disposed,
                                      String status, Long version) {
        return new InventoryScrapLineDO().setLineId(lineId).setTenantId(1L).setScrapId("scrap-01")
                .setLineNumber(lineNumber).setCanonicalSkuId("sku-01").setLocationId("location-01")
                .setStockStatus("NON_SELLABLE").setQualityStatus("DAMAGED").setBaseUomCode("PCS")
                .setRequestedQuantity(requested).setDisposedQuantity(disposed).setEvidenceType("QUALITY")
                .setEvidenceRef("quality-01").setStatus(status).setVersion(version);
    }

    private static final BigDecimal ZERO = new BigDecimal("0.000000");
}
