package cn.iocoder.yudao.module.cloudmold.finance.api.receivables;

/**
 * Governed Agent write surface for customer receivables.
 *
 * <p>Actor identity is derived from the verified RPC login context by the
 * provider and is intentionally absent from every method signature.</p>
 */
public interface ReceivablesAutomationCommandApi {

    ReceivablesCommandResult registerPlan(ReceivablesCommands.RegisterPlan command);

    ReceivablesCommandResult recordReceipt(ReceivablesCommands.RecordReceipt command);

    ReceivablesCommandResult allocateReceipt(ReceivablesCommands.AllocateReceipt command);
}
