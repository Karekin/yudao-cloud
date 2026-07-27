package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoWmsReceiptInventoryBridgeRow;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class YudaoWarehouseInboundQueryServiceTest {
    private final LegacyWmsPhysicalOperationsPort physicalOperationsPort = mock(LegacyWmsPhysicalOperationsPort.class);
    private final YudaoWmsReceiptInventoryBridgeService receiptBridgeService = mock(YudaoWmsReceiptInventoryBridgeService.class);
    private final YudaoWarehouseInboundQueryService service =
            new YudaoWarehouseInboundQueryService(physicalOperationsPort, receiptBridgeService);

    @Test
    void returnsWaitingAsnWhenReceiptOrderDoesNotExistYet() {
        YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalView result = service.getPurchaseInboundTerminal(
                new YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalQuery(
                        "PROCUREMENT_ORDER", "po-01", "PO-CM-001", null));
        assertThat(result.asnStatus()).isEqualTo("WAITING_ASN_CREATION");
        assertThat(result.nextWaitingEventCode()).isEqualTo("ASN_CREATED");
    }

    @Test
    void returnsCompletedReceiptWithCanonicalInventoryEvidence() {
        LegacyWmsPhysicalOperationsPort.ReceiptOrderContext context = new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                        "WMS", "RECEIPT_ORDER", 41L, "RK-41", 4, "2026-07-27T10:00:00", 7L,
                        new BigDecimal("10"), new BigDecimal("129.90"), "ok"),
                5L,
                List.of(new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                        4101L, 88L, 7L, new BigDecimal("10"), new BigDecimal("12.99"), new BigDecimal("129.90"))));
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(context);
        when(receiptBridgeService.resolveReplay(41L, context)).thenReturn(
                new YudaoWmsReceiptInventoryBridgeService.ReceiptBridgeReplay(true,
                        new YudaoWmsReceiptInventoryBridgeRow()
                                .setInventoryLedgerTransactionId(9001L)
                                .setInventoryBalanceId("balance-01")));

        YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalView result = service.getPurchaseInboundTerminal(
                new YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalQuery(
                        "PROCUREMENT_ORDER", "po-01", "PO-CM-001", 41L));
        assertThat(result.receiptStatus()).isEqualTo("COMPLETED");
        assertThat(result.qualityStatus()).isEqualTo("WAITING_QUALITY_RELEASE");
        assertThat(result.nextWaitingEventCode()).isEqualTo("QUALITY_RELEASED");
        assertThat(result.inventoryLedgerTransactionId()).isEqualTo("9001");
    }

    @Test
    void returnsReceiptWaitingWhenOrderExistsButNotFinished() {
        LegacyWmsPhysicalOperationsPort.ReceiptOrderContext context = new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                        "WMS", "RECEIPT_ORDER", 42L, "RK-42", 1, "2026-07-27T10:00:00", 7L,
                        new BigDecimal("10"), new BigDecimal("129.90"), "draft"),
                5L,
                List.of(new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                        4201L, 88L, 7L, new BigDecimal("10"), new BigDecimal("12.99"), new BigDecimal("129.90"))));
        when(physicalOperationsPort.getReceiptOrderContext(42L)).thenReturn(context);

        YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalView result = service.getPurchaseInboundTerminal(
                new YudaoWarehouseInboundQueryApi.PurchaseInboundTerminalQuery(
                        "PROCUREMENT_ORDER", "po-01", "PO-CM-001", 42L));
        assertThat(result.receiptStatus()).isEqualTo("WAITING_RECEIPT_COMPLETION");
        assertThat(result.nextWaitingEventCode()).isEqualTo("RECEIPT_COMPLETED");
    }
}
