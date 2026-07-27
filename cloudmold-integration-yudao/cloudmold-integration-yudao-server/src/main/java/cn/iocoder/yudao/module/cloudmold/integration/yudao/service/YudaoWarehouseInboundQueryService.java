package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWarehouseInboundQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoWmsReceiptInventoryBridgeRow;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class YudaoWarehouseInboundQueryService implements YudaoWarehouseInboundQueryApi {

    private static final int FINISHED_STATUS = 4;

    private final LegacyWmsPhysicalOperationsPort physicalOperationsPort;
    private final YudaoWmsReceiptInventoryBridgeService receiptInventoryBridgeService;

    @Override
    public PurchaseInboundTerminalView getPurchaseInboundTerminal(PurchaseInboundTerminalQuery query) {
        require(query != null, "purchase inbound query is required");
        require(hasText(query.businessDocumentType()), "businessDocumentType is required");
        require(hasText(query.businessDocumentId()), "businessDocumentId is required");
        if (query.receiptOrderId() == null) {
            return new PurchaseInboundTerminalView(
                    "CLOUDMOLD",
                    query.businessDocumentType(),
                    query.businessDocumentId(),
                    query.businessDocumentNo(),
                    null,
                    null,
                    "WAITING_ASN_CREATION",
                    "WAITING_RECEIPT_ORDER",
                    "WAITING_RECEIPT_COMPLETION",
                    "WAITING_QUALITY_RELEASE",
                    "ASN_CREATED",
                    "等待创建收货预约/ASN",
                    null,
                    null);
        }
        LegacyWmsPhysicalOperationsPort.ReceiptOrderContext context =
                physicalOperationsPort.getReceiptOrderContext(query.receiptOrderId());
        require(context != null && context.order() != null, "receipt order does not exist");
        if (context.order().status() != null && context.order().status() == FINISHED_STATUS) {
            YudaoWmsReceiptInventoryBridgeService.ReceiptBridgeReplay replay =
                    receiptInventoryBridgeService.resolveReplay(query.receiptOrderId(), context);
            if (replay.exists()) {
                YudaoWmsReceiptInventoryBridgeRow row = replay.row();
                return new PurchaseInboundTerminalView(
                        "WMS",
                        query.businessDocumentType(),
                        query.businessDocumentId(),
                        query.businessDocumentNo(),
                        context.order().documentId(),
                        context.order().documentNo(),
                        "COMPLETED",
                        "COMPLETED",
                        "WAITING_QUALITY_RELEASE",
                        "WAITING_PUTAWAY_CONFIRMATION",
                        "QUALITY_RELEASED",
                        "等待质检放行",
                        String.valueOf(row.getInventoryLedgerTransactionId()),
                        row.getInventoryBalanceId());
            }
            return new PurchaseInboundTerminalView(
                    "WMS",
                    query.businessDocumentType(),
                    query.businessDocumentId(),
                    query.businessDocumentNo(),
                    context.order().documentId(),
                    context.order().documentNo(),
                    "COMPLETED",
                    "NEEDS_CANONICAL_RECONCILIATION",
                    "WAITING_QUALITY_RELEASE",
                    "WAITING_PUTAWAY_CONFIRMATION",
                    "MANUAL_RECONCILIATION",
                    "等待人工核对收货与库存证据",
                    null,
                    null);
        }
        return new PurchaseInboundTerminalView(
                "WMS",
                query.businessDocumentType(),
                query.businessDocumentId(),
                query.businessDocumentNo(),
                context.order().documentId(),
                context.order().documentNo(),
                "COMPLETED",
                "WAITING_RECEIPT_COMPLETION",
                "WAITING_RECEIPT_COMPLETION",
                "WAITING_QUALITY_RELEASE",
                "RECEIPT_COMPLETED",
                "等待完成收货",
                null,
                null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }
}
