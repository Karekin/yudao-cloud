package cn.iocoder.yudao.module.cloudmold.finance.api.inventorycontrol;

public interface InventoryControlAccountingCommandApi {
    InventoryControlAccountingResult submitStockCountGainBasis(InventoryControlAccountingCommands.SubmitStockCountGainBasis command,
                                                              String actorPrincipalId);

    InventoryControlAccountingResult approveStockCountGainBasis(InventoryControlAccountingCommands.ApproveStockCountGainBasis command,
                                                               String actorPrincipalId);

    InventoryControlAccountingResult postStockCountAdjustment(InventoryControlAccountingCommands.PostStockCountAdjustment command,
                                                             String actorPrincipalId);

    InventoryControlAccountingResult postInventoryScrap(InventoryControlAccountingCommands.PostInventoryScrap command,
                                                        String actorPrincipalId);

    InventoryControlAccountingResult reverse(InventoryControlAccountingCommands.Reverse command,
                                            String actorPrincipalId);
}
