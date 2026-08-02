package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotLineView;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryAgingSnapshotView;
import cn.iocoder.yudao.module.cloudmold.inventory.controller.admin.vo.InventoryAgingSnapshotPageReqVO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryAgingSnapshotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryAgingSnapshotCaptureItem;
import cn.iocoder.yudao.module.cloudmold.inventory.service.query.InventoryAgingSnapshotWatermark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryAgingSnapshotServiceImplTest {

    private final InventoryAgingSnapshotMapper mapper = mock(InventoryAgingSnapshotMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final InventoryAgingSnapshotServiceImpl service =
            new InventoryAgingSnapshotServiceImpl(mapper, outboxAppender);
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
        when(mapper.selectOperationForUpdate(17L, 101L)).thenAnswer(invocation -> {
            InventoryAgingSnapshotOperationDO row = new InventoryAgingSnapshotOperationDO();
            row.setOperationId(101L);
            row.setTenantId(17L);
            row.setRequestHash(requestHash.get());
            row.setAttemptToken(attemptToken.get());
            row.setStatus(0);
            return row;
        });
        when(mapper.markOperationSucceeded(eq(17L), eq(101L), anyString(), any())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void capturesAgingSnapshotUsingLotReceiptAndUnknownExpiryRules() {
        when(mapper.selectCaptureItems(17L, "MERCHANT", "merchant-01", "warehouse-01"))
                .thenReturn(List.of(lotTrackedItem(), noLotItem()));
        when(mapper.selectWatermark(17L, "MERCHANT", "merchant-01", "warehouse-01"))
                .thenReturn(watermark());
        when(mapper.selectSnapshotHeadForUpdate(17L, expectedSnapshotId())).thenReturn(null);
        when(mapper.insertSnapshot(any())).thenReturn(1);
        when(mapper.insertSnapshotLine(any())).thenReturn(1);

        InventoryAgingSnapshotResult result = service.capture(command());

        assertThat(result.getStatus()).isEqualTo("CAPTURED");
        assertThat(result.getLineCount()).isEqualTo(2);
        assertThat(result.getSnapshotId()).matches("[0-9a-f\\-]{36}");
        assertThat(result.getSnapshotCode()).isEqualTo("IAS-20260802-" + result.getSnapshotId()
                .replace("-", "").substring(0, 8).toUpperCase());
        verify(mapper).insertSnapshot(argThat(snapshot ->
                snapshot.getLineCount().equals(2)
                        && snapshot.getUnknownAgeCount().equals(0)
                        && snapshot.getUnknownExpiryCount().equals(1)
                        && snapshot.getBucketPolicyVersion().equals("2026.08")
                        && snapshot.getBucketPolicyHash().matches("[0-9a-f]{64}")
                        && snapshot.getLedgerWatermarkRef().contains("inventory-ledger-watermark:v3:17:MERCHANT:merchant-01:warehouse-01:88")));
        verify(mapper).insertSnapshotLine(argThat(line ->
                "LOT_RECEIVED_AT".equals(line.getAgeBasisType())
                        && "AGING".equals(line.getAgeBucket())
                        && "WARNING".equals(line.getExpiryStatus())
                        && "EXPIRY_WARNING".equals(line.getRiskClassification())));
        verify(mapper).insertSnapshotLine(argThat(line ->
                line.getLotId() == null
                        && "LEDGER_FIRST_ENTRY_AT".equals(line.getAgeBasisType())
                        && "UNKNOWN".equals(line.getExpiryStatus())
                        && line.getExpiryDaysRemaining() == null
                        && "AGE_ATTENTION".equals(line.getRiskClassification())));
        verify(outboxAppender).append(argThat(event ->
                "inventory.aging_snapshot.captured".equals(event.getEventType())
                        && "inventory_aging_snapshot".equals(event.getAggregateType())
                        && event.getTenantId().equals(17L)));
    }

    @Test
    void reusesExistingSnapshotForBusinessDuplicateWithDifferentIdempotencyKey() {
        when(mapper.selectCaptureItems(17L, "MERCHANT", "merchant-01", "warehouse-01"))
                .thenReturn(List.of(lotTrackedItem(), noLotItem()));
        when(mapper.selectWatermark(17L, "MERCHANT", "merchant-01", "warehouse-01"))
                .thenReturn(watermark());
        when(mapper.selectSnapshotHeadForUpdate(17L, expectedSnapshotId())).thenReturn(existingSnapshot());

        InventoryAgingSnapshotResult result = service.capture(command("aging-snapshot-02"));

        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.getSnapshotId()).isEqualTo(expectedSnapshotId());
        assertThat(result.getSnapshotCode()).isEqualTo("IAS-20260802-394888FC");
        assertThat(result.getLineCount()).isEqualTo(2);
        verify(mapper, never()).insertSnapshot(any());
        verify(mapper, never()).insertSnapshotLine(any());
        verify(outboxAppender, never()).append(any());
        verify(mapper).markOperationSucceeded(eq(17L), eq(101L), anyString(), any());
    }

    @Test
    void getSnapshotPageShortCircuitsWhenNoRowsMatch() {
        InventoryAgingSnapshotPageReqVO request = new InventoryAgingSnapshotPageReqVO();
        when(mapper.countSnapshotPage(17L, null)).thenReturn(0L);

        var result = service.getSnapshotPage(request);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getList()).isEmpty();
        verify(mapper, never()).selectSnapshotPage(anyLong(), any(), anyLong(), anyInt());
    }

    @Test
    void requireSnapshotReturnsPersistedLines() {
        InventoryAgingSnapshotView header = InventoryAgingSnapshotView.builder()
                .snapshotId("snapshot-01")
                .snapshotCode("IAS-20260802")
                .status("CAPTURED")
                .snapshotVersion(1L)
                .build();
        InventoryAgingSnapshotLineView line = InventoryAgingSnapshotLineView.builder()
                .lineId(1L)
                .balanceId("balance-01")
                .ageBucket("AGING")
                .expiryStatus("UNKNOWN")
                .build();
        when(mapper.selectSnapshot(17L, "snapshot-01")).thenReturn(header);
        when(mapper.selectSnapshotLines(17L, "snapshot-01")).thenReturn(List.of(line));

        InventoryAgingSnapshotView result = service.requireSnapshot("snapshot-01");

        assertThat(result.getLines()).containsExactly(line);
    }

    private static InventoryAgingSnapshotCommand command() {
        return command("aging-snapshot-01");
    }

    private static InventoryAgingSnapshotCommand command(String idempotencyKey) {
        return InventoryAgingSnapshotCommand.builder()
                .idempotencyKey(idempotencyKey)
                .sourceEventId("inventory-health-scan-01")
                .correlationId("33333333-3333-4333-8333-333333333333")
                .occurredAt(Instant.parse("2026-08-02T12:00:00Z"))
                .ownerType("merchant")
                .ownerId("merchant-01")
                .warehouseId("warehouse-01")
                .bucketPolicyCode("AGING_BUCKET_V1")
                .bucketPolicyVersion("2026.08")
                .ageFreshMaxDays(7)
                .ageAgingMaxDays(30)
                .ageStaleMaxDays(90)
                .expiryWarningMaxDays(14)
                .expiryCriticalMaxDays(3)
                .build();
    }

    private static String expectedSnapshotId() {
        return "394888fc-f643-397d-bb1c-98f5ce8546cb";
    }

    private static cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotDO existingSnapshot() {
        cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotDO snapshot =
                new cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryAgingSnapshotDO();
        snapshot.setSnapshotId(expectedSnapshotId());
        snapshot.setTenantId(17L);
        snapshot.setSnapshotCode("IAS-20260802-394888FC");
        snapshot.setLineCount(2);
        snapshot.setStatus("CAPTURED");
        snapshot.setVersion(1L);
        snapshot.setCreatedAt(LocalDateTime.of(2026, 8, 2, 12, 0));
        return snapshot;
    }

    private static InventoryAgingSnapshotWatermark watermark() {
        InventoryAgingSnapshotWatermark watermark = new InventoryAgingSnapshotWatermark();
        watermark.setLedgerEntryCount(5L);
        watermark.setMaxLedgerEntryId(88L);
        watermark.setMaxBalanceVersion(4L);
        watermark.setMaxLedgerEntryAt(LocalDateTime.of(2026, 8, 2, 11, 59));
        return watermark;
    }

    private static InventoryAgingSnapshotCaptureItem lotTrackedItem() {
        InventoryAgingSnapshotCaptureItem item = new InventoryAgingSnapshotCaptureItem();
        item.setBalanceId("balance-01");
        item.setOwnerType("MERCHANT");
        item.setOwnerId("merchant-01");
        item.setCanonicalSkuId("sku-01");
        item.setWarehouseId("warehouse-01");
        item.setLocationId("location-01");
        item.setLotId("lot-01");
        item.setLotCode("LOT-01");
        item.setStockStatus("SELLABLE");
        item.setQualityStatus("QUALIFIED");
        item.setBaseUomCode("EA");
        item.setOnHandQuantity(new BigDecimal("10"));
        item.setReservedQuantity(new BigDecimal("2"));
        item.setInTransitQuantity(BigDecimal.ZERO);
        item.setBalanceVersion(4L);
        item.setExpiresOn(LocalDate.of(2026, 8, 10));
        item.setLotReceivedAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        return item;
    }

    private static InventoryAgingSnapshotCaptureItem noLotItem() {
        InventoryAgingSnapshotCaptureItem item = new InventoryAgingSnapshotCaptureItem();
        item.setBalanceId("balance-02");
        item.setOwnerType("MERCHANT");
        item.setOwnerId("merchant-01");
        item.setCanonicalSkuId("sku-02");
        item.setWarehouseId("warehouse-01");
        item.setLocationId("location-02");
        item.setStockStatus("SELLABLE");
        item.setQualityStatus("QUALIFIED");
        item.setBaseUomCode("EA");
        item.setOnHandQuantity(new BigDecimal("5"));
        item.setReservedQuantity(BigDecimal.ZERO);
        item.setInTransitQuantity(BigDecimal.ZERO);
        item.setBalanceVersion(2L);
        item.setFirstLedgerEntryAt(LocalDateTime.of(2026, 7, 15, 8, 0));
        return item;
    }
}
