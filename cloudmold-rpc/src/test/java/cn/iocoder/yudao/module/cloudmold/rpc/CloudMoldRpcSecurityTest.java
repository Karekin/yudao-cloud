package cn.iocoder.yudao.module.cloudmold.rpc;

import org.apache.dubbo.rpc.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudMoldRpcSecurityTest {

    private static final String SECRET = "local-test-secret-must-be-at-least-32-characters";

    @AfterEach
    void tearDown() {
        CloudMoldRpcSecurity.resetForTest();
    }

    @Test
    void shouldSignVerifyAndRejectReplay() {
        CloudMoldRpcSecurity.configure(SECRET, 60);
        Invocation invocation = invocationWithMutableAttachments();
        String capabilityId = "capability.cloudmold.catalog.catalog-query-api.get.v1";
        CloudMoldRpcCallContext context = new CloudMoldRpcCallContext(8L, 42L, 2, "catalog-smoke",
                "run-001");

        CloudMoldRpcSecurity.sign(invocation, capabilityId, context);
        CloudMoldRpcSecurity.VerifiedContext verified = CloudMoldRpcSecurity.verify(invocation, capabilityId);

        assertThat(verified.tenantId()).isEqualTo(8L);
        assertThat(verified.operatorId()).isEqualTo(42L);
        assertThat(verified.skillId()).isEqualTo("catalog-smoke");
        assertThat(verified.runId()).isEqualTo("run-001");
        assertThatThrownBy(() -> CloudMoldRpcSecurity.verify(invocation, capabilityId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("nonce");
    }

    @Test
    void shouldRejectAnUnsignedInvocation() {
        CloudMoldRpcSecurity.configure(SECRET, 60);

        assertThatThrownBy(() -> CloudMoldRpcSecurity.verify(invocationWithMutableAttachments(), "capability.x"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Missing RPC attachment");
    }

    private static Invocation invocationWithMutableAttachments() {
        Invocation invocation = mock(Invocation.class);
        Map<String, Object> attachments = new HashMap<>();
        doAnswer(call -> {
            attachments.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(invocation).setAttachment(anyString(), anyString());
        when(invocation.getObjectAttachment(anyString())).thenAnswer(call -> attachments.get(call.getArgument(0)));
        return invocation;
    }
}
