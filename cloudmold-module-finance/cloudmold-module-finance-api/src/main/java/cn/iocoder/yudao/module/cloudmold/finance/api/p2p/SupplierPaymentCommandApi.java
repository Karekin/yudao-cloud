package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

public interface SupplierPaymentCommandApi {
    ProcureToPayResult create(SupplierPaymentCommands.Create command, String actorPrincipalId);
    ProcureToPayResult submitForApproval(SupplierPaymentCommands.Transition command, String actorPrincipalId);
    ProcureToPayResult approve(SupplierPaymentCommands.Transition command, String actorPrincipalId);
    ProcureToPayResult release(SupplierPaymentCommands.Transition command, String actorPrincipalId);
    ProcureToPayResult submitForExecution(SupplierPaymentCommands.Transition command, String actorPrincipalId);
    ProcureToPayResult recordExecution(SupplierPaymentCommands.Execution command, String actorPrincipalId);
    ProcureToPayResult settle(SupplierPaymentCommands.Settlement command, String actorPrincipalId);
}
