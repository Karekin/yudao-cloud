package cn.iocoder.yudao.module.cloudmold.merchant.api.workflow;

/**
 * Governed read contract for the managed merchant admission and inspection lifecycle.
 */
public interface MerchantManagedAdmissionWorkflowQueryApi {

    MerchantManagedAdmissionWorkflowResult inspect(String applicationId);

    /**
     * 通过规范商家标识读取最近一条托管准入/成长工作流事实。
     *
     * 商家中心以 merchantId 展示主数据，不能要求运营人员手工反查 applicationId。
     */
    MerchantManagedAdmissionWorkflowResult inspectByMerchantId(String merchantId);
}
