package cn.iocoder.yudao.module.cloudmold.integration.yudao.api;

import java.io.Serializable;

public interface YudaoWarehouseInboundQueryApi {

    PurchaseInboundTerminalView getPurchaseInboundTerminal(PurchaseInboundTerminalQuery query);

    record PurchaseInboundTerminalQuery(String businessDocumentType,
                                        String businessDocumentId,
                                        String businessDocumentNo,
                                        Long receiptOrderId) implements Serializable {
    }

    record PurchaseInboundTerminalView(String sourceSystem,
                                       String businessDocumentType,
                                       String businessDocumentId,
                                       String businessDocumentNo,
                                       Long receiptOrderId,
                                       String receiptOrderNo,
                                       String asnStatus,
                                       String receiptStatus,
                                       String qualityStatus,
                                       String putawayStatus,
                                       String nextWaitingEventCode,
                                       String nextWaitingEventLabel,
                                       String inventoryLedgerTransactionId,
                                       String inventoryBalanceId) implements Serializable {
    }
}
