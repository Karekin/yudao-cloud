package cn.iocoder.yudao.module.cloudmold.merchant.api.workflow;

public interface MerchantManagedAdmissionWorkflowQueryApi {

    Object inspect(String applicationId);

    Object inspectByMerchantId(String merchantId);
}
