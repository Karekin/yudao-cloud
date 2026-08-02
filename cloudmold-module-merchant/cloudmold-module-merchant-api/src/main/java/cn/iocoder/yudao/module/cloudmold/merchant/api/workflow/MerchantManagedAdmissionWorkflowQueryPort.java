package cn.iocoder.yudao.module.cloudmold.merchant.api.workflow;

/**
 * Read-only workflow fact port for hosted merchant admission and inspection.
 */
public interface MerchantManagedAdmissionWorkflowQueryPort {

    MerchantManagedAdmissionWorkflowResult inspect(String applicationId);

    MerchantManagedAdmissionWorkflowResult inspectByMerchantId(String merchantId);
}
