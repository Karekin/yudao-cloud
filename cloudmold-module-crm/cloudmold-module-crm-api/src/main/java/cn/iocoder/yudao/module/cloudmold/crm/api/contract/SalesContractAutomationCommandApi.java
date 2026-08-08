package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

/**
 * Governed Agent write surface for sales contracts.
 *
 * <p>The provider resolves both the canonical Principal and System user from
 * the verified RPC login context. Callers cannot supply either identity.</p>
 */
public interface SalesContractAutomationCommandApi {

    SalesContractCommandResult execute(SalesContractCommand command);
}
