package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockCountAdjustmentDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockCountAdjustmentOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerEntryDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockCountAdjustmentMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockCountAdjustmentOperationMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerEntryMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerTransactionMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryStockCountAdjustmentServiceImplTest {

    private final InventoryStockCountAdjustmentOperationMapper operationMapper =
            mock(InventoryStockCountAdjustmentOperationMapper.class);
    private final InventoryStockCountAdjustmentMapper adjustmentMapper =
            mock(InventoryStockCountAdjustmentMapper.class);
    private final InventoryV3BalanceMapper balanceMapper = mock(InventoryV3BalanceMapper.class);
    private final InventoryLotMapper lotMapper = mock(InventoryLotMapper.class);
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper =
            mock(InventoryV3LedgerTransactionMapper.class);
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper = mock(InventoryV3LedgerEntryMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final CatalogSkuValidationApi skuValidationApi = mock(CatalogSkuValidationApi.class);
    private final WarehouseReferenceValidationApi warehouseValidationApi =
            mock(WarehouseReferenceValidationApi.class);
    private final MerchantOwnerValidationApi merchantOwnerValidationApi =
            mock(MerchantOwnerValidationApi.class);

    private final InventoryStockCountAdjustmentServiceImpl service =
            new InventoryStockCountAdjustmentServiceImpl(operationMapper, adjustmentMapper, balanceMapper, lotMapper,
                    ledgerTransactionMapper, ledgerEntryMapper, outboxAppender, skuValidationApi,
                    warehouseValidationApi, merchantOwnerValidationApi);

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
    void appliesStockCountAdjustmentAgainstFrozenBookBalance() {
        InventoryStockCountAdjustmentCommand command = command();
        when(operationMapper.selectLastInsertId()).thenReturn(11L);
        when(operationMapper.selectForUpdate(1L, 11L)).thenReturn(new InventoryStockCountAdjustmentOperationDO()
                .setOperationId(11L)
                .setTenantId(1L)
                .setAttemptToken("attempt-1")
                .setRequestHash(JsonUtils.toJsonString("mismatch"))); // overwritten below
        doAnswer(invocation -> null).when(operationMapper).insertOrResolve(eq(1L), any(), any(), any(), any(), any(), any());
        when(operationMapper.selectForUpdate(1L, 11L)).thenAnswer(invocation -> new InventoryStockCountAdjustmentOperationDO()
                .setOperationId(11L)
                .setTenantId(1L)
                .setAttemptToken(invocation.getArgument(1) == null ? null : null));
        // manual stub with captured state
        final String[] requestHash = new String[1];
        final String[] attemptToken = new String[1];
        doAnswer(invocation -> {
            requestHash[0] = invocation.getArgument(4);
            attemptToken[0] = invocation.getArgument(5);
            return 1;
        }).when(operationMapper).insertOrResolve(eq(1L), any(), any(), any(), any(), any(), any());
        when(operationMapper.selectForUpdate(1L, 11L)).thenAnswer(invocation -> new InventoryStockCountAdjustmentOperationDO()
                .setOperationId(11L)
                .setTenantId(1L)
                .setAttemptToken(attemptToken[0])
                .setRequestHash(requestHash[0]));
        when(adjustmentMapper.selectByStockCountLineId(1L, "line-01")).thenReturn(null);
        when(balanceMapper.selectDimensionForUpdate(1L, "MERCHANT", "owner-01", "sku-01", "warehouse-01",
                "location-01", null, "SELLABLE", "QUALIFIED")).thenReturn(new InventoryV3BalanceDO()
                .setBalanceId("balance-01")
                .setTenantId(1L)
                .setOwnerType("MERCHANT")
                .setOwnerId("owner-01")
                .setCanonicalSkuId("sku-01")
                .setWarehouseId("warehouse-01")
                .setLocationId("location-01")
                .setLotId(null)
                .setStockStatus("SELLABLE")
                .setQualityStatus("QUALIFIED")
                .setBaseUomCode("PIECE")
                .setOnHandQuantity(new BigDecimal("10.000000"))
                .setReservedQuantity(new BigDecimal("0.000000"))
                .setInTransitQuantity(new BigDecimal("0.000000"))
                .setVersion(3L));
        when(balanceMapper.updateBalanceCas(eq(1L), eq("balance-01"), eq(3L),
                eq(new BigDecimal("8.000000")), eq(new BigDecimal("0.000000")),
                eq(new BigDecimal("0.000000")), any())).thenReturn(1);
        doAnswer(invocation -> {
            var tx = invocation.getArgument(0, cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO.class);
            tx.setLedgerTransactionId(701L);
            return 1;
        }).when(ledgerTransactionMapper).insert(any(cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO.class));
        when(operationMapper.markSucceeded(eq(1L), eq(11L), any(), any())).thenReturn(1);

        InventoryStockCountAdjustmentResult result = service.execute(command);

        assertThat(result.getBalanceId()).isEqualTo("balance-01");
        assertThat(result.getOnHandQuantity()).isEqualByComparingTo("8.000000");
        assertThat(result.getAdjustmentQuantity()).isEqualByComparingTo("-2.000000");
        verify(ledgerEntryMapper).insert(any(InventoryV3LedgerEntryDO.class));
        verify(adjustmentMapper).insert(any(InventoryStockCountAdjustmentDO.class));
        verify(outboxAppender).append(any(AppendDomainEventCommand.class));
    }

    private InventoryStockCountAdjustmentCommand command() {
        return InventoryStockCountAdjustmentCommand.builder()
                .idempotencyKey("stock-count-adjust-01")
                .sourceEventId("source-event-01")
                .stockCountId("count-01")
                .stockCountLineId("line-01")
                .adjustmentId("adj-01")
                .ownerType("MERCHANT")
                .ownerId("owner-01")
                .canonicalSkuId("sku-01")
                .warehouseId("warehouse-01")
                .locationId("location-01")
                .stockStatus("SELLABLE")
                .qualityStatus("QUALIFIED")
                .baseUomCode("PIECE")
                .bookOnHandQuantity(new BigDecimal("10.000000"))
                .countedOnHandQuantity(new BigDecimal("8.000000"))
                .adjustmentQuantity(new BigDecimal("-2.000000"))
                .businessType("STOCK_COUNT")
                .businessId("count-01")
                .businessItemId("line-01")
                .businessNo("COUNT-0001")
                .occurredAt(Instant.parse("2026-08-02T05:00:00Z"))
                .build();
    }
}
