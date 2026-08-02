package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

public interface P2pEvidenceIngestionApi {
    ProcureToPayResult ingestPurchaseOrderLine(P2pEvidenceCommands.PurchaseOrderLine command,
                                               String actorPrincipalId);
    ProcureToPayResult ingestReceiptLine(P2pEvidenceCommands.ReceiptLine command,
                                         String actorPrincipalId);
    ProcureToPayResult ingestQualityDisposition(P2pEvidenceCommands.QualityDisposition command,
                                                String actorPrincipalId);
    ProcureToPayResult ingestInventoryMovement(P2pEvidenceCommands.InventoryMovement command,
                                               String actorPrincipalId);
    ProcureToPayResult postQualifiedReceipt(P2pEvidenceCommands.PostQualifiedReceipt command,
                                            String actorPrincipalId);
}
