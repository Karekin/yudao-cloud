package cn.iocoder.yudao.module.cloudmold.merchant.api.workflow;

/**
 * Governed read contract for the managed merchant admission and inspection lifecycle.
 */
public interface MerchantManagedAdmissionWorkflowQueryApi {

    MerchantManagedAdmissionWorkflowResult inspect(String applicationId);
}
