package cn.iocoder.yudao.module.cloudmold.customerservice.api.workflow;

/**
 * Governed, repeatable read contract for customer-resolution workflows.
 */
public interface CustomerResolutionWorkflowQueryApi {

    CustomerResolutionWorkflowResult inspect(String ticketId);
}
