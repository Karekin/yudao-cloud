package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

public interface P2pFinanceSetupCommandApi {
    ProcureToPayResult createLedger(P2pFinanceSetupCommands.Ledger command, String actorPrincipalId);
    ProcureToPayResult createAccount(P2pFinanceSetupCommands.Account command, String actorPrincipalId);
    ProcureToPayResult registerSupplierPayeeInstrument(P2pFinanceSetupCommands.SupplierPayeeInstrument command,
                                                       String actorPrincipalId);
    ProcureToPayResult createMatchPolicy(P2pFinanceSetupCommands.MatchPolicy command, String actorPrincipalId);
    ProcureToPayResult createPaymentTerm(P2pFinanceSetupCommands.PaymentTerm command, String actorPrincipalId);
    ProcureToPayResult createPostingRule(P2pFinanceSetupCommands.PostingRule command, String actorPrincipalId);
    ProcureToPayResult createDimensionType(P2pFinanceSetupCommands.DimensionType command, String actorPrincipalId);
    ProcureToPayResult createDimensionValue(P2pFinanceSetupCommands.DimensionValue command, String actorPrincipalId);
    ProcureToPayResult createInventoryValuationPolicy(
            P2pFinanceSetupCommands.InventoryValuationPolicy command, String actorPrincipalId);
}
