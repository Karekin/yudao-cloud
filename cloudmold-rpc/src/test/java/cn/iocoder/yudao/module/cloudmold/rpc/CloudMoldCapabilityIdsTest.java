package cn.iocoder.yudao.module.cloudmold.rpc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CloudMoldCapabilityIdsTest {

    @Test
    void shouldKeepReadableIdForNonOverloadedMethods() throws Exception {
        Method method = SampleQueryApi.class.getMethod("get", String.class);

        assertThat(CloudMoldCapabilityIds.forMethod(method))
                .isEqualTo("capability.cloudmold.rpc.sample-query.get.v1");
    }

    @Test
    void shouldAppendStableSignatureForOverloadedMethods() throws Exception {
        Method byId = SampleQueryApi.class.getMethod("require", Long.class);
        Method byCode = SampleQueryApi.class.getMethod("require", String.class);

        assertThat(CloudMoldCapabilityIds.forMethod(byId)).contains(".require.sig-").endsWith(".v1");
        assertThat(CloudMoldCapabilityIds.forMethod(byCode)).contains(".require.sig-").endsWith(".v1");
        assertThat(CloudMoldCapabilityIds.forMethod(byId)).isNotEqualTo(CloudMoldCapabilityIds.forMethod(byCode));
        assertThat(CloudMoldCapabilityIds.forMethod(byId)).isEqualTo(CloudMoldCapabilityIds.forMethod(byId));
    }

    @Test
    void shouldDeriveManagedMerchantWorkflowInspectCapabilityId() throws Exception {
        Method method = cn.iocoder.yudao.module.cloudmold.merchant.api.workflow
                .MerchantManagedAdmissionWorkflowQueryApi.class.getMethod("inspect", String.class);

        assertThat(CloudMoldCapabilityIds.forMethod(method))
                .isEqualTo("capability.cloudmold.merchant.merchant-managed-admission-workflow-query.inspect.v1");
    }

    interface SampleQueryApi {
        Object get(String id);
        Object require(Long id);
        Object require(String code);
    }
}
