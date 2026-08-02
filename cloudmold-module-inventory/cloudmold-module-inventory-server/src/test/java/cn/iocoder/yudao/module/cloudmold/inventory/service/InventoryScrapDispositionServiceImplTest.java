package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryScrapDispositionOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerEntryDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryScrapDispositionOperationMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerEntryMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerTransactionMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryScrapDispositionServiceImplTest {

    private final InventoryScrapDispositionOperationMapper operationMapper =
            mock(InventoryScrapDispositionOperationMapper.class);
    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryLotMapper lotMapper = mock(InventoryLotMapper.class);
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper =
            mock(InventoryV3LedgerTransactionMapper.class);
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper = mock(InventoryV3LedgerEntryMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CatalogSkuValidationApi skuValidationApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi =
            mock(WarehouseReferenceValidationApi.class);
    private final MerchantOwnerValidationApi merchantOwnerValidationApi = mock(MerchantOwnerValidationApi.class);

    private final InventoryScrapDispositionServiceImpl service = new InventoryScrapDispositionServiceImpl(
            operationMapper, balanceMapper, lotMapper, ledgerTransactionMapper, ledgerEntryMapper,
            outboxAppender, skuValidationApi, warehouseReferenceValidationApi, merchantOwnerValidationApi);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult("event-1", "a".repeat(64), false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void disposesInventoryAndWritesLedger() {
        InventoryScrapDispositionCommand command = command();
        when(operationMapper.selectLastInsertId()).thenReturn(31L);
        doAnswer(invocation -> {
            String requestHash = invocation.getArgument(7);
            String attemptToken = invocation.getArgument(8);
            when(operationMapper.selectForUpdate(1L, 31L)).thenReturn(new InventoryScrapDispositionOperationDO()
                    .setOperationId(31L).setTenantId(1L).setAttemptToken(attemptToken)
                    .setRequestHash(requestHash).setStatus(0));
            return 1;
        }).when(operationMapper).insertOrResolve(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any());
        when(balanceMapper.selectDimensionForUpdate(1L, "MERCHANT", "owner-01", "sku-01", "warehouse-01",
                "location-01", null, "NON_SELLABLE", "DAMAGED")).thenReturn(new InventoryV3BalanceDO()
                .setBalanceId("balance-01").setTenantId(1L).setOwnerType("MERCHANT").setOwnerId("owner-01")
                .setCanonicalSkuId("sku-01").setWarehouseId("warehouse-01").setLocationId("location-01")
                .setLotId(null).setStockStatus("NON_SELLABLE").setQualityStatus("DAMAGED")
                .setBaseUomCode("PCS").setOnHandQuantity(new BigDecimal("9.000000"))
                .setReservedQuantity(new BigDecimal("1.000000")).setInTransitQuantity(new BigDecimal("0.000000"))
                .setVersion(4L));
        when(balanceMapper.updateBalanceCas(eq(1L), eq("balance-01"), eq(4L), eq(new BigDecimal("6.500000")),
                eq(new BigDecimal("1.000000")), eq(new BigDecimal("0.000000")), any())).thenReturn(1);
        doAnswer(invocation -> {
            InventoryV3LedgerTransactionDO transaction = invocation.getArgument(0);
            assertThat(transaction.getOperationId()).isNull();
            assertThat(transaction.getScrapDispositionOperationId()).isEqualTo(31L);
            transaction.setLedgerTransactionId(701L);
            return 1;
        }).when(ledgerTransactionMapper).insert(any(InventoryV3LedgerTransactionDO.class));
        when(ledgerEntryMapper.insert(any(InventoryV3LedgerEntryDO.class))).thenReturn(1);
        when(operationMapper.markSucceeded(eq(1L), eq(31L), any(), any())).thenReturn(1);

        InventoryScrapDispositionResult result = service.execute(command);

        assertThat(result.getLedgerTransactionId()).isEqualTo(701L);
        assertThat(result.getBalanceId()).isEqualTo("balance-01");
        assertThat(result.getOnHandQuantity()).isEqualTo(new BigDecimal("6.500000"));
        verify(merchantOwnerValidationApi).requireActiveMerchant("owner-01");
        verify(skuValidationApi).requireActiveSku("sku-01");
        verify(warehouseReferenceValidationApi).requireActiveLocation("warehouse-01", "location-01");
        verify(outboxAppender).append(any());
    }

    @Test
    void replaysDuplicateDisposition() {
        InventoryScrapDispositionCommand command = command();
        when(operationMapper.selectLastInsertId()).thenReturn(32L);
        when(operationMapper.selectForUpdate(1L, 32L)).thenReturn(new InventoryScrapDispositionOperationDO()
                .setOperationId(32L).setTenantId(1L).setAttemptToken("first-token")
                .setRequestHash("72ff66e4b20dbde4baf87c45f4b853f0bc72a55db62b5989674f10c7fb2f9ebd")
                .setStatus(10).setLedgerTransactionId(702L).setBalanceId("balance-02")
                .setAggregateVersion(5L).setOnHandQuantity(new BigDecimal("3.000000"))
                .setDisposedQuantity(new BigDecimal("2.500000")));

        doAnswer(invocation -> {
            String requestHash = invocation.getArgument(7);
            when(operationMapper.selectForUpdate(1L, 32L)).thenReturn(new InventoryScrapDispositionOperationDO()
                    .setOperationId(32L).setTenantId(1L).setAttemptToken("different-token")
                    .setRequestHash(requestHash).setStatus(10).setLedgerTransactionId(702L)
                    .setBalanceId("balance-02").setAggregateVersion(5L)
                    .setOnHandQuantity(new BigDecimal("3.000000"))
                    .setDisposedQuantity(new BigDecimal("2.500000")));
            return 1;
        }).when(operationMapper).insertOrResolve(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any());

        InventoryScrapDispositionResult result = service.execute(command);

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getLedgerTransactionId()).isEqualTo(702L);
        verifyNoInteractions(balanceMapper, ledgerTransactionMapper, ledgerEntryMapper);
    }

    private InventoryScrapDispositionCommand command() {
        return InventoryScrapDispositionCommand.builder()
                .idempotencyKey("scrap-dispose-01")
                .sourceEventId("source-event-01")
                .dispositionLineId("disp-line-01")
                .scrapDocumentId("scrap-01")
                .scrapLineId("scrap-line-01")
                .dispositionBatchId("batch-01")
                .ownerType("MERCHANT")
                .ownerId("owner-01")
                .canonicalSkuId("sku-01")
                .warehouseId("warehouse-01")
                .locationId("location-01")
                .stockStatus("NON_SELLABLE")
                .qualityStatus("DAMAGED")
                .baseUomCode("PCS")
                .quantity(new BigDecimal("2.500000"))
                .businessNo("SCRAP-0001")
                .occurredAt(Instant.parse("2026-08-02T03:00:00Z"))
                .build();
    }
}
