package cn.iocoder.yudao.module.cloudmold.rpc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudMoldRpcCallContextTest {

    @Test
    void shouldRestoreNestedContext() {
        CloudMoldRpcCallContext outer = new CloudMoldRpcCallContext(1L, 2L, 1, "outer", "run-outer");
        CloudMoldRpcCallContext inner = new CloudMoldRpcCallContext(1L, 3L, 1, "inner", "run-inner");

        try (CloudMoldRpcCallContext.Scope ignored = CloudMoldRpcCallContext.open(outer)) {
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(outer);
            try (CloudMoldRpcCallContext.Scope nested = CloudMoldRpcCallContext.open(inner)) {
                assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(inner);
            }
            assertThat(CloudMoldRpcCallContext.requireCurrent()).isEqualTo(outer);
        }
        assertThatThrownBy(CloudMoldRpcCallContext::requireCurrent)
                .isInstanceOf(IllegalStateException.class);
    }
}
