package cn.iocoder.yudao.module.cloudmold.crm.api;

/**
 * Governed Agent command surface for CRM.
 *
 * <p>The actor is resolved exclusively from the verified CloudMold RPC login
 * context by the provider. Callers cannot supply or impersonate a Principal.</p>
 */
public interface CrmAutomationCommandApi {

    CrmCommandResult execute(CrmCommand command);
}
