package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

/** Internal Finance command port; actor identity must come from a trusted boundary. */
public interface ReceivablesCommandPort {

    ReceivablesCommandResult registerPlan(ReceivablesCommands.RegisterPlan command, String actorPrincipalId);

    ReceivablesCommandResult recordReceipt(ReceivablesCommands.RecordReceipt command, String actorPrincipalId);

    ReceivablesCommandResult allocateReceipt(ReceivablesCommands.AllocateReceipt command, String actorPrincipalId);
}
