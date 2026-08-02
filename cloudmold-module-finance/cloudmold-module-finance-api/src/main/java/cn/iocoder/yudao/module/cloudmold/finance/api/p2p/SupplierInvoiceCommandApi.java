package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

public interface SupplierInvoiceCommandApi {
    ProcureToPayResult create(SupplierInvoiceCommands.Create command, String actorPrincipalId);
    ProcureToPayResult submit(SupplierInvoiceCommands.Transition command, String actorPrincipalId);
    ProcureToPayResult runThreeWayMatch(SupplierInvoiceCommands.Match command, String actorPrincipalId);
    ProcureToPayResult approveMatchOverride(SupplierInvoiceCommands.ApproveOverride command,
                                            String actorPrincipalId);
    ProcureToPayResult approve(SupplierInvoiceCommands.Transition command, String actorPrincipalId);
    ProcureToPayResult post(SupplierInvoiceCommands.Transition command, String actorPrincipalId);
}
