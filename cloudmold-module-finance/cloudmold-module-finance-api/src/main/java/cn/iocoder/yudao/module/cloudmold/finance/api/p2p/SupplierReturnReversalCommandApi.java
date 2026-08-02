package cn.iocoder.yudao.module.cloudmold.finance.api.p2p;

public interface SupplierReturnReversalCommandApi {
    ProcureToPayResult post(SupplierReturnReversalCommands.Post command, String actorPrincipalId);
}
