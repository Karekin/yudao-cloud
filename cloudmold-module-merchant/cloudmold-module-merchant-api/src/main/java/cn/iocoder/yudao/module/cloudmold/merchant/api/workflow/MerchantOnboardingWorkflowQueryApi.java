package cn.iocoder.yudao.module.cloudmold.merchant.api.workflow;

/**
 * Governed, repeatable read contract for merchant onboarding.
 */
public interface MerchantOnboardingWorkflowQueryApi {

    MerchantOnboardingWorkflowResult inspect(String applicationId);
}
