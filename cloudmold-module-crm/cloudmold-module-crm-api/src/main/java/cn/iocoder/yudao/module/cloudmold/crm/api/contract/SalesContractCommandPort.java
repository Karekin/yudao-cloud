package cn.iocoder.yudao.module.cloudmold.crm.api.contract;

/**
 * Internal command port. Actor identity is supplied only by the trusted web boundary.
 * This port is deliberately not published as a governed RPC capability.
 */
public interface SalesContractCommandPort {

    SalesContractCommandResult execute(SalesContractCommand command, String actorPrincipalId, Long actorAdminUserId);
}
